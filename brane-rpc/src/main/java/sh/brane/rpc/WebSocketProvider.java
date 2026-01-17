// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.rpc;

import static sh.brane.rpc.internal.RpcUtils.MAPPER;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.LiteBlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sh.brane.core.error.RpcException;
import sh.brane.rpc.internal.RpcUtils;

/**
 * Ultra-low latency WebSocket provider using Netty and LMAX Disruptor.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Multiple threads can
 * safely call {@link #send}, {@link #sendAsync}, and {@link #sendAsyncBatch}
 * concurrently. Request tracking uses an {@link java.util.concurrent.atomic.AtomicReferenceArray} with
 * CAS-based slot allocation to prevent race conditions between the caller
 * threads, Disruptor thread, and Netty I/O thread.
 *
 * <p><b>Optimizations:</b>
 * <ul>
 *   <li>Zero-allocation JSON serialization directly to ByteBuf</li>
 *   <li>Low-allocation response parsing from ByteBuf</li>
 *   <li>Lock-free request ID generation using AtomicLong</li>
 *   <li>CAS-based slot allocation for thread-safe request tracking</li>
 *   <li>Batched writes with flush on end-of-batch</li>
 *   <li>Optimized Netty channel options (TCP_NODELAY, etc.)</li>
 *   <li>Large ring buffer for high throughput bursts</li>
 * </ul>
 */
public class WebSocketProvider implements BraneProvider, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WebSocketProvider.class);

    // ==================== Connection State Machine ====================
    /**
     * Connection state for explicit state machine management.
     *
     * <p>State transitions:
     * <pre>
     * CONNECTING ---(success)---> CONNECTED
     *     |                           |
     *     | (failure)                 | (disconnect)
     *     v                           v
     *   CLOSED &lt;---------------  RECONNECTING
     *     ^                           |
     *     |                           | (max retries or close())
     *     +---------------------------+
     * </pre>
     *
     * <p><b>Request handling by state:</b>
     * <ul>
     *   <li>CONNECTING: Requests may fail if channel not yet active</li>
     *   <li>CONNECTED: Normal operation - requests sent immediately</li>
     *   <li>RECONNECTING: Requests rejected with RpcException (connection unavailable)</li>
     *   <li>CLOSED: Requests rejected with RpcException (provider closed)</li>
     * </ul>
     */
    public enum ConnectionState {
        /** Initial connection in progress. */
        CONNECTING,
        /** WebSocket is connected and ready for requests. */
        CONNECTED,
        /** Connection lost, reconnect in progress. Requests are rejected. */
        RECONNECTING,
        /** Provider is permanently closed. No more requests accepted. */
        CLOSED
    }

    /**
     * Current connection state. All state transitions must use atomic compareAndSet
     * to ensure thread-safe state machine transitions.
     */
    private final AtomicReference<ConnectionState> connectionState =
            new AtomicReference<>(ConnectionState.CONNECTING);

    // ==================== Configuration ====================
    /**
     * Default maximum pending requests before backpressure triggers.
     * <p>
     * Value is 65536 (2^16) because:
     * <ul>
     *   <li>Must be power of 2 for efficient slot indexing via bitwise AND mask</li>
     *   <li>Large enough for high-throughput scenarios (65K concurrent requests)</li>
     *   <li>Matches typical WebSocket connection capacity before server-side limits</li>
     * </ul>
     */
    private static final int DEFAULT_MAX_PENDING_REQUESTS = 65536;

    /**
     * Default timeout for requests in milliseconds.
     * <p>
     * 60 seconds provides sufficient time for:
     * <ul>
     *   <li>Slow RPC endpoints under load</li>
     *   <li>Network latency variations</li>
     *   <li>Block finality waiting (some methods wait for confirmations)</li>
     * </ul>
     */
    private static final long DEFAULT_TIMEOUT_MS = 60_000;

    /**
     * Ring buffer saturation threshold (10% remaining capacity).
     * <p>
     * When remaining capacity drops below this fraction of total buffer size,
     * the metrics callback is invoked to warn of impending backpressure.
     * <p>
     * 10% threshold chosen because:
     * <ul>
     *   <li>Early warning before hitting 0% (backpressure limit)</li>
     *   <li>Small enough to avoid false alarms during normal bursts</li>
     *   <li>Allows time for producers to slow down gracefully</li>
     * </ul>
     */
    private static final double RING_BUFFER_SATURATION_THRESHOLD = 0.1;

    // Instance configuration (from WebSocketConfig or defaults)
    private final int maxPendingRequests;
    private final Duration defaultRequestTimeout;
    // Note: slotMask was removed - it was a remnant of slot-based indexing that is no longer used
    // since pending request tracking switched to ConcurrentHashMap.

    // ==================== Connection State ====================
    private final String url;
    private final URI uri;
    private final EventLoopGroup group;
    /** True if we created the EventLoopGroup internally and are responsible for shutting it down. */
    private final boolean ownsEventLoopGroup;
    /** The socket channel class to use for Bootstrap (NIO, Epoll, or KQueue). */
    private final Class<? extends Channel> channelClass;
    /**
     * The active WebSocket channel. Volatile because it is accessed from multiple threads:
     * caller threads (sendAsync), Netty I/O thread, and reconnect scheduler.
     */
    private volatile Channel channel;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // ==================== Reconnect Configuration ====================
    /** Maximum number of reconnect attempts before giving up. */
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    /** Maximum delay between reconnect attempts (32 seconds). */
    private static final long MAX_RECONNECT_DELAY_MS = 32_000;
    /** Current reconnect attempt counter. Reset to 0 on successful connection. */
    private final AtomicLong reconnectAttempts = new AtomicLong(0);

    // ==================== Request Tracking ====================
    /**
     * Thread-safe map for tracking pending requests by ID.
     * <p>
     * Uses ConcurrentHashMap keyed by request ID instead of slot-based array to:
     * <ul>
     *   <li>Eliminate TOCTOU race conditions in slot allocation</li>
     *   <li>Prevent response/request ID mismatches under high concurrency</li>
     *   <li>Allow atomic put-if-absent semantics for thread-safe allocation</li>
     * </ul>
     */
    private final ConcurrentHashMap<Long, CompletableFuture<JsonRpcResponse>> pendingRequests = new ConcurrentHashMap<>();

    // ==================== Subscriptions ====================
    private final ConcurrentHashMap<String, Consumer<JsonRpcResponse>> subscriptions = new ConcurrentHashMap<>();

    /**
     * Executor for subscription callbacks. Defaults to virtual threads.
     * Callbacks are dispatched to this executor to avoid blocking the Netty I/O
     * thread.
     */
    private volatile Executor subscriptionExecutor;

    /**
     * Tracks whether we own the subscription executor (created internally) and
     * are responsible for shutting it down. User-provided executors are NOT owned.
     */
    private volatile boolean ownsSubscriptionExecutor = true;

    /**
     * Sets a custom executor for subscription callbacks.
     *
     * <p>
     * By default, subscription callbacks run on virtual threads to avoid blocking
     * the Netty I/O thread. Use this method to provide a custom executor if you
     * need specific threading behavior (e.g., a bounded pool, or the same thread as
     * Netty).
     *
     * <p>
     * <strong>Warning:</strong> If you set an executor that runs callbacks on the
     * Netty I/O thread (or any single thread), ensure callbacks complete quickly.
     * Blocking operations will stall all WebSocket I/O.
     *
     * <p>
     * <strong>Lifecycle:</strong> User-provided executors are NOT closed when
     * {@link #close()} is called. The caller is responsible for managing the
     * lifecycle of custom executors.
     *
     * @param executor the executor to use for subscription callbacks (must not be
     *                 null)
     * @throws NullPointerException if executor is null
     */
    public void setSubscriptionExecutor(Executor executor) {
        this.subscriptionExecutor = Objects.requireNonNull(executor, "executor");
        this.ownsSubscriptionExecutor = false; // User-provided - caller manages lifecycle
    }

    // ==================== Metrics ====================
    private volatile BraneMetrics metrics = BraneMetrics.noop();

    /**
     * Sets a custom metrics collector for observability.
     *
     * <p>
     * Use this to integrate with monitoring systems like Micrometer, Prometheus,
     * or custom metrics collectors.
     *
     * @param metrics the metrics collector (must not be null)
     * @throws NullPointerException if metrics is null
     */
    public void setMetrics(BraneMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * Counter for orphaned responses (responses received with no matching pending request).
     * Orphaned responses occur when:
     * <ul>
     *   <li>Response ID cannot be parsed</li>
     *   <li>Request timed out before response arrived</li>
     *   <li>Request was cancelled</li>
     * </ul>
     * <p>
     * Orphaned responses are logged at ERROR level for debugging. This counter provides
     * visibility into how often this occurs. High counts may indicate network issues,
     * server-side delays, or timeout values that are too aggressive.
     *
     * @see #getOrphanedResponseCount()
     */
    private final LongAdder orphanedResponses = new LongAdder();

    /**
     * Returns the total count of orphaned responses received since this provider was created.
     *
     * <p>Orphaned responses are responses received with no matching pending request.
     * This typically occurs when:
     * <ul>
     *   <li>The response ID cannot be parsed</li>
     *   <li>The request timed out before the response arrived</li>
     *   <li>The request was cancelled</li>
     * </ul>
     *
     * <p>This metric is useful for monitoring connection health. High orphan counts
     * may indicate network issues, server-side delays, or timeout values that are
     * too aggressive.
     *
     * @return the total count of orphaned responses
     * @since 0.5.0
     */
    public long getOrphanedResponseCount() {
        return orphanedResponses.sum();
    }

    /**
     * Returns the current connection state.
     *
     * <p>This method is useful for monitoring and diagnostics. Clients can use
     * this to check whether the provider is connected, reconnecting, or closed
     * before attempting operations.
     *
     * <p><b>Thread safety:</b> This method returns a snapshot of the current state.
     * The state may change immediately after this method returns.
     *
     * @return the current {@link ConnectionState}
     * @since 0.5.0
     */
    public ConnectionState getConnectionState() {
        return connectionState.get();
    }

    /**
     * Returns the current number of pending requests awaiting responses.
     *
     * <p>This metric is useful for monitoring backpressure and connection health.
     * A consistently high pending request count may indicate:
     * <ul>
     *   <li>Network latency issues</li>
     *   <li>Server-side processing delays</li>
     *   <li>Request rate exceeding response throughput</li>
     * </ul>
     *
     * <p><b>Thread safety:</b> This method returns a snapshot of the current count.
     * The count may change immediately after this method returns due to concurrent
     * request submission or response processing.
     *
     * @return the number of pending requests
     * @since 0.6.0
     */
    public int getPendingRequestCount() {
        return pendingRequests.size();
    }

    // Lock-free ID generator
    private final AtomicLong idGenerator = new AtomicLong(1);

    // Disruptor for request processing
    private final Disruptor<RequestEvent> disruptor;
    private final RingBuffer<RequestEvent> ringBuffer;

    // Pre-computed JSON fragments as bytes for zero-allocation serialization
    private static final byte[] JSON_PREFIX = "{\"jsonrpc\":\"2.0\",\"method\":\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PARAMS_PREFIX = "\",\"params\":".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ID_PREFIX = ",\"id\":".getBytes(StandardCharsets.UTF_8);
    private static final byte JSON_SUFFIX = '}';
    private static final byte[] EMPTY_PARAMS = "[]".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NULL_BYTES = "null".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TRUE_BYTES = "true".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FALSE_BYTES = "false".getBytes(StandardCharsets.UTF_8);

    // ==================== Native Transport Support ====================
    /**
     * Holds the selected EventLoopGroup and Channel class for a transport.
     */
    private record TransportSelection(EventLoopGroup group, Class<? extends Channel> channelClass) {}

    /**
     * Selects the appropriate native transport based on the configured type.
     * <p>
     * For AUTO mode, the selection order is:
     * <ol>
     *   <li>Epoll on Linux (if native library available)</li>
     *   <li>KQueue on macOS/BSD (if native library available)</li>
     *   <li>NIO as fallback on all platforms</li>
     * </ol>
     */
    private static TransportSelection selectTransport(WebSocketConfig.TransportType type, int ioThreads) {
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "brane-netty-io");
            t.setDaemon(true);
            return t;
        };

        return switch (type) {
            case EPOLL -> {
                if (!io.netty.channel.epoll.Epoll.isAvailable()) {
                    throw new IllegalStateException("Epoll transport requested but not available: "
                            + io.netty.channel.epoll.Epoll.unavailabilityCause().getMessage());
                }
                log.debug("Using Epoll transport");
                yield new TransportSelection(
                        new io.netty.channel.epoll.EpollEventLoopGroup(ioThreads, threadFactory),
                        io.netty.channel.epoll.EpollSocketChannel.class);
            }
            case KQUEUE -> {
                if (!io.netty.channel.kqueue.KQueue.isAvailable()) {
                    throw new IllegalStateException("KQueue transport requested but not available: "
                            + io.netty.channel.kqueue.KQueue.unavailabilityCause().getMessage());
                }
                log.debug("Using KQueue transport");
                yield new TransportSelection(
                        new io.netty.channel.kqueue.KQueueEventLoopGroup(ioThreads, threadFactory),
                        io.netty.channel.kqueue.KQueueSocketChannel.class);
            }
            case NIO -> {
                log.debug("Using NIO transport");
                yield new TransportSelection(
                        new io.netty.channel.nio.NioEventLoopGroup(ioThreads, threadFactory),
                        NioSocketChannel.class);
            }
            case AUTO -> selectAutoTransport(ioThreads, threadFactory);
        };
    }

    /**
     * Auto-selects the best available transport for the current platform.
     */
    private static TransportSelection selectAutoTransport(int ioThreads, ThreadFactory threadFactory) {
        // Try Epoll first (Linux)
        if (io.netty.channel.epoll.Epoll.isAvailable()) {
            log.info("Auto-selected Epoll transport (Linux native)");
            return new TransportSelection(
                    new io.netty.channel.epoll.EpollEventLoopGroup(ioThreads, threadFactory),
                    io.netty.channel.epoll.EpollSocketChannel.class);
        }
        // Try KQueue (macOS/BSD)
        if (io.netty.channel.kqueue.KQueue.isAvailable()) {
            log.info("Auto-selected KQueue transport (macOS/BSD native)");
            return new TransportSelection(
                    new io.netty.channel.kqueue.KQueueEventLoopGroup(ioThreads, threadFactory),
                    io.netty.channel.kqueue.KQueueSocketChannel.class);
        }
        // Fall back to NIO
        log.info("Auto-selected NIO transport (native transports not available)");
        return new TransportSelection(
                new io.netty.channel.nio.NioEventLoopGroup(ioThreads, threadFactory),
                NioSocketChannel.class);
    }

    /**
     * Detects the appropriate channel class for an externally-provided EventLoopGroup.
     */
    private static Class<? extends Channel> detectChannelClass(EventLoopGroup group) {
        if (group instanceof io.netty.channel.epoll.EpollEventLoopGroup) {
            return io.netty.channel.epoll.EpollSocketChannel.class;
        }
        if (group instanceof io.netty.channel.kqueue.KQueueEventLoopGroup) {
            return io.netty.channel.kqueue.KQueueSocketChannel.class;
        }
        // Default to NIO for NioEventLoopGroup or unknown types
        return NioSocketChannel.class;
    }

    private WebSocketProvider(WebSocketConfig config) {
        this.url = config.url();
        this.uri = URI.create(config.url());
        this.maxPendingRequests = config.maxPendingRequests();
        this.defaultRequestTimeout = config.defaultRequestTimeout();

        // Initialize default subscription executor (owned by this provider)
        this.subscriptionExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.ownsSubscriptionExecutor = true;

        // Use provided EventLoopGroup or create based on transport type
        if (config.eventLoopGroup() != null) {
            this.group = config.eventLoopGroup();
            this.channelClass = detectChannelClass(config.eventLoopGroup());
            this.ownsEventLoopGroup = false; // External group - caller is responsible for lifecycle
        } else {
            TransportSelection transport = selectTransport(config.transportType(), config.ioThreads());
            this.group = transport.group;
            this.channelClass = transport.channelClass;
            this.ownsEventLoopGroup = true; // Internal group - we manage lifecycle
        }

        // Configurable wait strategy
        WaitStrategy waitStrategy = switch (config.waitStrategy()) {
            case BUSY_SPIN -> new BusySpinWaitStrategy();
            case YIELDING -> new YieldingWaitStrategy();
            case LITE_BLOCKING -> new LiteBlockingWaitStrategy();
            case BLOCKING -> new BlockingWaitStrategy();
        };

        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "brane-disruptor");
            t.setDaemon(true);
            return t;
        };

        this.disruptor = new Disruptor<>(
                RequestEvent::new,
                config.ringBufferSize(),
                threadFactory,
                ProducerType.MULTI,
                waitStrategy);

        this.disruptor.handleEventsWith(this::handleEvent);
        this.disruptor.start();
        this.ringBuffer = disruptor.getRingBuffer();

        connect();
    }

    /**
     * Backward-compatible constructor using defaults.
     */
    private WebSocketProvider(String url) {
        this(WebSocketConfig.withDefaults(url));
    }

    /**
     * Creates a new WebSocketProvider with the specified configuration.
     *
     * <p>
     * Use this method for full control over provider settings including:
     * <ul>
     * <li>Max pending requests (backpressure limit)</li>
     * <li>Disruptor ring buffer size</li>
     * <li>Wait strategy (YIELDING for low latency, BLOCKING for CPU
     * efficiency)</li>
     * <li>Default request timeout</li>
     * <li>Number of I/O threads</li>
     * </ul>
     *
     * @param config the WebSocket configuration
     * @return a connected WebSocketProvider instance
     * @throws RuntimeException if connection fails
     * @see WebSocketConfig#builder(String)
     */
    public static WebSocketProvider create(WebSocketConfig config) {
        return new WebSocketProvider(config);
    }

    /**
     * Creates a new WebSocketProvider and connects to the specified URL.
     *
     * <p>
     * The provider supports both {@code ws://} and {@code wss://} schemes.
     * SSL/TLS is automatically configured for secure connections.
     * </p>
     *
     * @param url the WebSocket URL to connect to (e.g.,
     *            "wss://ethereum.publicnode.com")
     * @return a connected WebSocketProvider instance
     * @throws RuntimeException if connection fails
     */
    public static WebSocketProvider create(String url) {
        return new WebSocketProvider(url);
    }

    private void connect() {
        try {
            final SslContext sslContext;
            if ("wss".equalsIgnoreCase(uri.getScheme())) {
                sslContext = SslContextBuilder.forClient().build();
            } else {
                sslContext = null;
            }

            int port = uri.getPort();
            if (port == -1) {
                port = "wss".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            }

            int attempt = 0;
            long delay = 100;
            Exception lastError = null;

            while (attempt < MAX_RECONNECT_ATTEMPTS && !closed.get()) {
                // Create a fresh handler for each connection attempt.
                // This is critical for thread safety: the WebSocketClientHandshaker tracks
                // handshake state internally, and the handshakeFuture is a ChannelPromise
                // tied to a specific channel. Reusing the same handler across reconnections
                // would leave the handshaker in an inconsistent state.
                final WebSocketClientHandler connectionHandler = new WebSocketClientHandler(
                        WebSocketClientHandshakerFactory.newHandshaker(
                                uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders()));

                Bootstrap b = new Bootstrap();
                b.group(group)
                        .channel(channelClass)
                        .option(ChannelOption.TCP_NODELAY, true)
                        .option(ChannelOption.SO_KEEPALIVE, true)
                        .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                        .option(ChannelOption.WRITE_BUFFER_WATER_MARK,
                                new WriteBufferWaterMark(8 * 1024, 32 * 1024))
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ChannelPipeline p = ch.pipeline();
                                if (sslContext != null) {
                                    p.addLast(sslContext.newHandler(ch.alloc(), uri.getHost(),
                                            uri.getPort() == -1 ? 443 : uri.getPort()));
                                }
                                p.addLast(new HttpClientCodec());
                                p.addLast(new HttpObjectAggregator(65536));
                                p.addLast(connectionHandler);
                            }
                        });

                try {
                    this.channel = b.connect(uri.getHost(), port).sync().channel();
                    connectionHandler.handshakeFuture().sync();
                    connected.set(true);
                    connectionState.set(ConnectionState.CONNECTED);
                    return;
                } catch (Exception e) {
                    lastError = e;
                    log.warn("Netty connection attempt {} failed: {}", attempt + 1, e.getMessage());
                    if (this.channel != null && this.channel.isOpen()) {
                        this.channel.close();
                    }
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Connection attempt interrupted", ie);
                    }
                    delay = Math.min(delay * 2, 5000);
                    attempt++;
                }
            }
            throw new RuntimeException("Failed to connect to " + uri + " after " + attempt + " attempts", lastError);
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to " + uri, e);
        }
    }

    private class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {
        private final WebSocketClientHandshaker handshaker;
        private ChannelPromise handshakeFuture;

        public WebSocketClientHandler(WebSocketClientHandshaker handshaker) {
            this.handshaker = handshaker;
        }

        public ChannelFuture handshakeFuture() {
            return handshakeFuture;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            handshakeFuture = ctx.newPromise();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            handshaker.handshake(ctx.channel());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (!closed.get()) {
                // Transition to RECONNECTING state - requests will be rejected until reconnected
                connectionState.set(ConnectionState.RECONNECTING);
                log.warn("Connection lost, triggering reconnect");
                reconnect();
            }
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            Channel ch = ctx.channel();
            if (!handshaker.isHandshakeComplete()) {
                if (msg instanceof FullHttpResponse response) {
                    try {
                        handshaker.finishHandshake(ch, response);
                        handshakeFuture.setSuccess();
                    } catch (WebSocketHandshakeException e) {
                        handshakeFuture.setFailure(e);
                    }
                }
                return;
            }

            if (msg instanceof FullHttpResponse response) {
                throw new IllegalStateException(
                        "Unexpected FullHttpResponse (status=" + response.status() + ")");
            }

            if (msg instanceof WebSocketFrame frame) {
                if (frame instanceof TextWebSocketFrame textFrame) {
                    ByteBuf content = textFrame.content();
                    try {
                        try (JsonParser parser = MAPPER.getFactory()
                                .createParser((java.io.InputStream) new ByteBufInputStream(content))) {
                            JsonNode node = MAPPER.readTree(parser);

                            if (node.has("method") && node.get("method").asText().endsWith("_subscription")) {
                                handleNotificationNode(node);
                            } else {
                                processResponseNode(node);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error parsing WebSocket frame", e);
                    }
                } else if (frame instanceof CloseWebSocketFrame) {
                    ch.close();
                }
            }
        }

        private void handleNotificationNode(JsonNode node) {
            try {
                JsonNode params = node.get("params");
                if (params == null) {
                    log.warn("Malformed subscription notification: missing 'params' field");
                    return;
                }

                JsonNode subscriptionNode = params.get("subscription");
                if (subscriptionNode == null || subscriptionNode.isNull()) {
                    log.warn("Malformed subscription notification: missing 'subscription' field in params");
                    return;
                }

                String subId = subscriptionNode.asText();
                Consumer<JsonRpcResponse> listener = subscriptions.get(subId);
                if (listener != null) {
                    JsonNode resultNode = params.get("result");
                    Object result = MAPPER.convertValue(resultNode, Object.class);
                    JsonRpcResponse response = new JsonRpcResponse("2.0", result, null, null);
                    // Dispatch to subscription executor to avoid blocking Netty I/O thread
                    subscriptionExecutor.execute(() -> {
                        try {
                            listener.accept(response);
                        } catch (Exception callbackEx) {
                            log.error("Subscription callback error for subscription {}", subId, callbackEx);
                            metrics.onSubscriptionCallbackError(subId, callbackEx);
                        }
                    });
                }
            } catch (Exception e) {
                // Never let exceptions escape to Netty's exceptionCaught handler,
                // as that would disconnect the WebSocket and lose all in-flight requests
                log.error("Error handling subscription notification", e);
            }
        }

        private void processResponseNode(JsonNode node) throws com.fasterxml.jackson.core.JsonProcessingException {
            if (!node.has("id"))
                return;
            JsonNode idNode = node.get("id");
            long id = -1;
            if (idNode.isNumber())
                id = idNode.asLong();
            else if (idNode.isTextual()) {
                try {
                    id = Long.parseLong(idNode.asText());
                } catch (Exception e) {
                    log.error("Orphaned response: could not parse ID '{}' as long - caller future will never complete",
                            idNode.asText(), e);
                    orphanedResponses.increment();
                    metrics.onOrphanedResponse("unparseable ID: " + idNode.asText());
                    return;
                }
            }

            if (id == -1) {
                // ID node exists but is neither number nor textual (e.g., null, object, array)
                log.error("Orphaned response: ID node has unexpected type '{}' - caller future will never complete",
                        idNode.getNodeType());
                orphanedResponses.increment();
                metrics.onOrphanedResponse("unexpected ID type: " + idNode.getNodeType());
                return;
            }

            // Atomically remove the pending request to prevent race conditions
            CompletableFuture<JsonRpcResponse> future = pendingRequests.remove(id);
            if (future == null) {
                // Response received but no pending request found - may have timed out or been cancelled
                log.error("Orphaned response: no pending request found for ID {} - response dropped", id);
                orphanedResponses.increment();
                metrics.onOrphanedResponse("no pending request for ID: " + id);
                return;
            }

            JsonNode errorNode = node.get("error");
            JsonRpcError error = null;
            if (errorNode != null && !errorNode.isNull()) {
                error = MAPPER.treeToValue(errorNode, JsonRpcError.class);
            }
            Object result = null;
            if (node.has("result")) {
                result = MAPPER.treeToValue(node.get("result"), Object.class);
            }
            future.complete(new JsonRpcResponse("2.0", result, error, String.valueOf(id)));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("Channel exception", cause);
            if (!handshakeFuture.isDone()) {
                handshakeFuture.setFailure(cause);
            }
            failAllPending(new RpcException(-32000, "Channel error", null, cause));
            ctx.close();
        }

    }

    /**
     * Sends a synchronous JSON-RPC request and blocks until the response is
     * received.
     *
     * <p>
     * This method wraps {@link #sendAsync} and waits for completion. For better
     * performance in high-throughput scenarios, prefer {@link #sendAsync} or
     * {@link #sendAsyncBatch}.
     * </p>
     *
     * @param method the JSON-RPC method name (e.g., "eth_blockNumber")
     * @param params the method parameters, or null/empty for no parameters
     * @return the JSON-RPC response
     * @throws RpcException if the request fails or returns an error
     */
    @Override
    public JsonRpcResponse send(String method, List<?> params) throws RpcException {
        try {
            return sendAsync(method, params).join();
        } catch (Exception e) {
            if (e.getCause() instanceof RpcException rpc) throw rpc;
            throw new RpcException(-32000, "Request failed", null, e);
        }
    }

    /**
     * Allocates a pending request slot for a new request with backpressure handling.
     * <p>
     * Uses atomic putIfAbsent to ensure thread-safety without TOCTOU race conditions.
     * Each request ID is unique, so there are no slot collisions.
     *
     * @param id the request ID
     * @return the allocated future, or a failed future if backpressure is triggered
     */
    private CompletableFuture<JsonRpcResponse> allocateSlot(long id) {
        // Check pending request count for backpressure
        if (pendingRequests.size() >= maxPendingRequests) {
            metrics.onBackpressure(pendingRequests.size(), maxPendingRequests);
            return CompletableFuture.failedFuture(new sh.brane.core.error.RpcException(
                    -32000,
                    "Too many pending requests (" + maxPendingRequests + " limit reached)",
                    null));
        }

        CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();
        // Atomic put - if another thread somehow used the same ID, this would return non-null
        // In practice, IDs are monotonically increasing, so this won't happen
        CompletableFuture<JsonRpcResponse> existing = pendingRequests.putIfAbsent(id, future);
        if (existing != null) {
            // Should never happen with monotonic IDs, but handle defensively
            metrics.onBackpressure(pendingRequests.size(), maxPendingRequests);
            return CompletableFuture.failedFuture(new sh.brane.core.error.RpcException(
                    -32000,
                    "Request ID " + id + " already in use (internal error)",
                    null));
        }
        return future;
    }

    /**
     * Sends an asynchronous JSON-RPC request.
     *
     * <p>
     * The request is serialized directly to a ByteBuf with zero intermediate
     * String allocations. The returned future completes when the response is
     * received.
     * </p>
     *
     * <p>
     * For batch scenarios with many concurrent requests, consider using
     * {@link #sendAsyncBatch} which uses the Disruptor for optimal batching.
     * </p>
     *
     * @param method the JSON-RPC method name (e.g., "eth_chainId")
     * @param params the method parameters, or null/empty for no parameters
     * @return a CompletableFuture that completes with the JSON-RPC response
     */
    public CompletableFuture<JsonRpcResponse> sendAsync(String method, List<?> params) {
        return sendAsync(method, params, defaultRequestTimeout);
    }

    /**
     * Sends an asynchronous JSON-RPC request with a custom timeout.
     *
     * <p>
     * The request is serialized directly to a ByteBuf with zero intermediate
     * String allocations. The returned future completes when the response is
     * received or when the timeout expires.
     * </p>
     *
     * @param method  the JSON-RPC method name (e.g., "eth_chainId")
     * @param params  the method parameters, or null/empty for no parameters
     * @param timeout the timeout duration for this request; if null, no timeout is
     *                applied
     * @return a CompletableFuture that completes with the JSON-RPC response or
     *         exceptionally on timeout
     */
    public CompletableFuture<JsonRpcResponse> sendAsync(String method, List<?> params, Duration timeout) {
        // Check connection state - reject requests during RECONNECTING or CLOSED
        ConnectionState state = connectionState.get();
        if (state == ConnectionState.CLOSED) {
            return CompletableFuture.failedFuture(new RpcException(
                    -32000, "WebSocketProvider is closed", null));
        }
        if (state == ConnectionState.RECONNECTING) {
            return CompletableFuture.failedFuture(new RpcException(
                    -32000, "WebSocket is reconnecting - request rejected to prevent loss", null));
        }

        long id = idGenerator.getAndIncrement();

        CompletableFuture<JsonRpcResponse> future = allocateSlot(id);
        if (future.isCompletedExceptionally()) {
            return future; // Backpressure triggered
        }

        Channel ch = this.channel;
        if (ch != null && ch.isActive()) {
            // Schedule timeout if specified
            if (timeout != null && timeout.toMillis() > 0) {
                ch.eventLoop().schedule(() -> {
                    if (!future.isDone()) {
                        // Remove from pending requests on timeout
                        pendingRequests.remove(id, future);
                        metrics.onRequestTimeout(method, id);
                        future.completeExceptionally(new sh.brane.core.error.RpcException(
                                -32000,
                                "Request timed out after " + timeout.toMillis() + "ms (method: " + method + ")",
                                null));
                    }
                }, timeout.toMillis(), TimeUnit.MILLISECONDS);
            }

            ch.eventLoop().execute(() -> {
                ByteBuf buffer = ch.alloc().buffer(256);
                try {
                    buffer.writeBytes(JSON_PREFIX);
                    writeEscapedString(buffer, method);
                    buffer.writeBytes(PARAMS_PREFIX);

                    if (params == null || params.isEmpty()) {
                        buffer.writeBytes(EMPTY_PARAMS);
                    } else {
                        writeJsonArray(buffer, params);
                    }

                    buffer.writeBytes(ID_PREFIX);
                    writeLong(buffer, id);
                    buffer.writeByte(JSON_SUFFIX);

                    ch.writeAndFlush(new TextWebSocketFrame(buffer));
                } catch (Exception e) {
                    buffer.release();
                    future.completeExceptionally(e);
                }
            });
        } else {
            future.completeExceptionally(new RpcException(-32000, "Channel not active", null));
        }

        return future;
    }

    /**
     * Sends an asynchronous JSON-RPC request using the Disruptor ring buffer.
     *
     * <p>
     * This method is optimized for high-throughput scenarios where many requests
     * are sent in rapid succession. Requests are batched and flushed together,
     * reducing syscall overhead. Uses the configured default request timeout.
     * </p>
     *
     * <p>
     * Use this method when:
     * </p>
     * <ul>
     * <li>Sending many requests in a tight loop</li>
     * <li>Maximum throughput is more important than individual request latency</li>
     * <li>You want automatic batching of network writes</li>
     * </ul>
     *
     * @param method the JSON-RPC method name
     * @param params the method parameters, or null/empty for no parameters
     * @return a CompletableFuture that completes with the JSON-RPC response
     * @see #sendAsyncBatch(String, List, Duration) for custom timeout support
     */
    public CompletableFuture<JsonRpcResponse> sendAsyncBatch(String method, List<?> params) {
        return sendAsyncBatch(method, params, defaultRequestTimeout);
    }

    /**
     * Sends an asynchronous JSON-RPC request optimized for high-throughput batching,
     * with a custom timeout.
     *
     * <p>
     * Same as {@link #sendAsyncBatch(String, List)} but allows specifying a custom timeout
     * for this request. The timeout applies to the individual request, not the batch write.
     * </p>
     *
     * @param method  the JSON-RPC method name
     * @param params  the method parameters, or null/empty for no parameters
     * @param timeout the timeout duration for this request; if null, no timeout is applied
     * @return a CompletableFuture that completes with the JSON-RPC response or
     *         exceptionally on timeout
     */
    public CompletableFuture<JsonRpcResponse> sendAsyncBatch(String method, List<?> params, Duration timeout) {
        // Check connection state - reject requests during RECONNECTING or CLOSED
        ConnectionState state = connectionState.get();
        if (state == ConnectionState.CLOSED) {
            return CompletableFuture.failedFuture(new RpcException(
                    -32000, "WebSocketProvider is closed", null));
        }
        if (state == ConnectionState.RECONNECTING) {
            return CompletableFuture.failedFuture(new RpcException(
                    -32000, "WebSocket is reconnecting - request rejected to prevent loss", null));
        }

        long id = idGenerator.getAndIncrement();

        CompletableFuture<JsonRpcResponse> future = allocateSlot(id);
        if (future.isCompletedExceptionally()) {
            return future; // Backpressure triggered
        }

        // Schedule timeout if specified
        Channel ch = this.channel;
        if (timeout != null && timeout.toMillis() > 0 && ch != null && ch.isActive()) {
            ch.eventLoop().schedule(() -> {
                if (!future.isDone()) {
                    // Remove from pending requests on timeout
                    pendingRequests.remove(id, future);
                    metrics.onRequestTimeout(method, id);
                    future.completeExceptionally(new RpcException(
                            -32000,
                            "Request timed out after " + timeout.toMillis() + "ms (method: " + method + ")",
                            null));
                }
            }, timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        // Check ring buffer saturation before publishing (metrics hook for early warning)
        int bufferSize = ringBuffer.getBufferSize();
        long remainingCapacity = ringBuffer.remainingCapacity();
        if (remainingCapacity < bufferSize * RING_BUFFER_SATURATION_THRESHOLD) {
            metrics.onRingBufferSaturation(remainingCapacity, bufferSize);
        }

        // Publish to disruptor for batched processing
        long sequence = ringBuffer.next();
        try {
            RequestEvent event = ringBuffer.get(sequence);
            event.set(method, params, id);
        } finally {
            ringBuffer.publish(sequence);
        }

        return future;
    }

    /**
     * Subscribes to a real-time event stream using {@code eth_subscribe}.
     *
     * <p>
     * Supported subscription types include:
     * </p>
     * <ul>
     * <li>{@code newHeads} - new block headers</li>
     * <li>{@code logs} - contract event logs (with optional filter)</li>
     * <li>{@code newPendingTransactions} - pending transaction hashes</li>
     * </ul>
     *
     * <p>
     * The callback is invoked on the Netty I/O thread. Avoid blocking operations
     * in the callback to prevent I/O thread starvation.
     * </p>
     *
     * @param method   the subscription type (e.g., "newHeads", "logs")
     * @param params   additional parameters (e.g., log filter for "logs"
     *                 subscription)
     * @param callback invoked for each notification with the event data
     * @return the subscription ID, which can be used to {@link #unsubscribe}
     * @throws RpcException if subscription fails
     */
    @Override
    public String subscribe(String method, List<?> params, Consumer<Object> callback) throws RpcException {
        List<Object> subscribeParams = new java.util.ArrayList<>();
        subscribeParams.add(method);
        if (params != null) {
            subscribeParams.addAll(params);
        }

        try {
            JsonRpcResponse response = sendAsync("eth_subscribe", subscribeParams).join();
            if (response.error() != null) {
                throw RpcUtils.toRpcException(response.error());
            }
            String subscriptionId = String.valueOf(response.result());
            if (subscriptionId.startsWith("\"") && subscriptionId.endsWith("\"")) {
                subscriptionId = subscriptionId.substring(1, subscriptionId.length() - 1);
            }

            subscriptions.put(subscriptionId, (jsonResponse) -> {
                callback.accept(jsonResponse.result());
            });
            return subscriptionId;
        } catch (Exception e) {
            if (e instanceof RpcException rpc) throw rpc;
            throw new RpcException(-32000, "Subscription failed", null, e);
        }
    }

    /**
     * Unsubscribes from a previously created subscription.
     *
     * @param subscriptionId the subscription ID returned by {@link #subscribe}
     * @return true if the subscription was successfully cancelled
     * @throws RpcException if the unsubscribe request fails
     */
    @Override
    public boolean unsubscribe(String subscriptionId) throws RpcException {
        try {
            JsonRpcResponse response = sendAsync("eth_unsubscribe", java.util.Collections.singletonList(subscriptionId))
                    .join();
            subscriptions.remove(subscriptionId);
            return "true".equals(String.valueOf(response.result()));
        } catch (Exception e) {
            throw new RpcException(-32000, "Unsubscribe failed", null, e);
        }
    }

    /**
     * Closes the WebSocket connection and releases all resources.
     *
     * <p>
     * This method:
     * </p>
     * <ul>
     * <li>Fails any pending requests with an exception</li>
     * <li>Shuts down the Disruptor</li>
     * <li>Closes the WebSocket channel</li>
     * <li>Shuts down the subscription executor (only if created internally)</li>
     * <li>Shuts down the Netty event loop group (only if created internally)</li>
     * </ul>
     *
     * <p>
     * <b>Resource ownership:</b> Externally-provided resources are NOT closed:
     * </p>
     * <ul>
     * <li>If an {@code EventLoopGroup} was provided via {@link WebSocketConfig#eventLoopGroup()},
     *     it will NOT be shut down.</li>
     * <li>If a custom executor was set via {@link #setSubscriptionExecutor(Executor)},
     *     it will NOT be shut down.</li>
     * </ul>
     * <p>
     * The caller is responsible for managing the lifecycle of externally-provided resources.
     * </p>
     *
     * <p>
     * After calling close(), this provider cannot be reused.
     * </p>
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return; // Already closed
        }

        // Transition to CLOSED state - all new requests will be rejected
        connectionState.set(ConnectionState.CLOSED);

        // Fail all pending requests before shutting down
        RpcException shutdownException = new RpcException(-32000, "WebSocketProvider is shutting down", null);
        failAllPending(shutdownException);

        // Shutdown Disruptor - halt() stops immediately, shutdown() waits for drain
        // Use halt() since we've already failed all pending requests
        try {
            disruptor.halt();
        } catch (Exception e) {
            log.warn("Error halting Disruptor", e);
        }

        // Close the WebSocket channel
        if (channel != null) {
            try {
                channel.close().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while closing channel", e);
            } catch (Exception e) {
                log.warn("Error closing channel", e);
            }
        }

        // Shutdown the subscription executor only if we created it internally.
        // User-provided executors are NOT closed - the caller manages their lifecycle.
        if (ownsSubscriptionExecutor && subscriptionExecutor instanceof java.util.concurrent.ExecutorService es) {
            try {
                es.shutdown();
                if (!es.awaitTermination(5, TimeUnit.SECONDS)) {
                    es.shutdownNow();
                    log.warn("Subscription executor did not terminate gracefully");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                es.shutdownNow();
                log.warn("Interrupted while shutting down subscription executor", e);
            } catch (Exception e) {
                log.warn("Error shutting down subscription executor", e);
            }
        }

        // Only shutdown the EventLoopGroup if we created it internally
        if (ownsEventLoopGroup) {
            try {
                group.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while shutting down EventLoopGroup", e);
            } catch (Exception e) {
                log.warn("Error shutting down EventLoopGroup", e);
            }
        }
    }

    private void reconnect() {
        if (closed.get() || connectionState.get() == ConnectionState.CLOSED) {
            return;
        }

        long attempt = reconnectAttempts.incrementAndGet();
        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            log.error("Max reconnect attempts ({}) exceeded, giving up on {}", MAX_RECONNECT_ATTEMPTS, uri);
            closed.set(true);
            connectionState.set(ConnectionState.CLOSED);
            failAllPending(new RpcException(-32000,
                "WebSocket connection permanently failed after " + MAX_RECONNECT_ATTEMPTS + " reconnect attempts", null));
            return;
        }

        // Exponential backoff: 1s, 2s, 4s, 8s, 16s, 32s (capped)
        long delayMs = Math.min(1000L * (1L << (attempt - 1)), MAX_RECONNECT_DELAY_MS);
        log.info("Scheduling reconnect attempt {}/{} to {} in {}ms", attempt, MAX_RECONNECT_ATTEMPTS, uri, delayMs);

        group.schedule(() -> {
            if (!connected.get() && connectionState.get() == ConnectionState.RECONNECTING) {
                try {
                    connect();
                    // Reset counter on successful connection
                    reconnectAttempts.set(0);
                    log.info("Reconnected successfully to {}", uri);
                } catch (Exception e) {
                    log.error("Reconnect attempt {} failed: {}", attempt, e.getMessage());
                    reconnect();
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void failAllPending(RpcException e) {
        connected.set(false);
        // Atomically remove and fail all pending requests
        pendingRequests.forEach((id, future) -> {
            if (pendingRequests.remove(id, future)) {
                future.completeExceptionally(e);
            }
        });
    }

    /**
     * Handle request event from Disruptor.
     * Writes JSON directly to ByteBuf without intermediate String allocation.
     */
    private void handleEvent(RequestEvent event, long sequence, boolean endOfBatch) {
        Channel ch = this.channel;
        if (ch != null && ch.isActive()) {
            // Allocate buffer - estimate size based on method and params
            ByteBuf buffer = ch.alloc().buffer(256);

            try {
                // Write JSON directly to ByteBuf - zero allocation serialization
                buffer.writeBytes(JSON_PREFIX);
                writeEscapedString(buffer, event.method);
                buffer.writeBytes(PARAMS_PREFIX);

                if (event.params == null || event.params.isEmpty()) {
                    buffer.writeBytes(EMPTY_PARAMS);
                } else {
                    writeJsonArray(buffer, event.params);
                }

                buffer.writeBytes(ID_PREFIX);
                writeLong(buffer, event.id);
                buffer.writeByte(JSON_SUFFIX);

                ch.write(new TextWebSocketFrame(buffer));
            } catch (Exception e) {
                buffer.release();
                // Atomically remove the pending request
                CompletableFuture<JsonRpcResponse> future = pendingRequests.remove(event.id);
                if (future != null) {
                    future.completeExceptionally(e);
                }
            }

            // Flush at end of batch for maximum throughput
            if (endOfBatch) {
                ch.flush();
            }
        } else {
            // Channel not active - fail the request
            CompletableFuture<JsonRpcResponse> future = pendingRequests.remove(event.id);
            if (future != null) {
                future.completeExceptionally(new RpcException(-32000, "Channel not active", null));
            }
        }
        // Clear event data to prevent stale references when Disruptor reuses this event object.
        // This is important for memory hygiene: params may reference large objects that should
        // be eligible for GC after the request is processed.
        event.clear();
    }

    /**
     * Write a string with JSON escaping directly to ByteBuf.
     * <p>
     * Properly handles:
     * <ul>
     *   <li>ASCII characters (0x00-0x7F) - written as single bytes</li>
     *   <li>Non-ASCII BMP characters (0x80-0xFFFF) - encoded as UTF-8</li>
     *   <li>Supplementary characters (emoji, etc.) - detected via surrogate pairs and encoded as UTF-8</li>
     * </ul>
     */
    private void writeEscapedString(ByteBuf buf, String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> {
                    buf.writeByte('\\');
                    buf.writeByte('"');
                }
                case '\\' -> {
                    buf.writeByte('\\');
                    buf.writeByte('\\');
                }
                case '\n' -> {
                    buf.writeByte('\\');
                    buf.writeByte('n');
                }
                case '\r' -> {
                    buf.writeByte('\\');
                    buf.writeByte('r');
                }
                case '\t' -> {
                    buf.writeByte('\\');
                    buf.writeByte('t');
                }
                default -> {
                    if (c < 32) {
                        // Control character - escape as unicode
                        buf.writeByte('\\');
                        buf.writeByte('u');
                        buf.writeByte(HEX[(c >> 12) & 0xF]);
                        buf.writeByte(HEX[(c >> 8) & 0xF]);
                        buf.writeByte(HEX[(c >> 4) & 0xF]);
                        buf.writeByte(HEX[c & 0xF]);
                    } else if (c < 128) {
                        // ASCII - single byte
                        buf.writeByte(c);
                    } else {
                        // Non-ASCII: encode as UTF-8
                        writeUtf8Char(buf, s, i);
                        // If this was a surrogate pair, skip the low surrogate
                        if (Character.isHighSurrogate(c) && i + 1 < s.length()
                                && Character.isLowSurrogate(s.charAt(i + 1))) {
                            i++;
                        }
                    }
                }
            }
        }
    }

    /**
     * Write a character (possibly a surrogate pair) as UTF-8 bytes.
     */
    private void writeUtf8Char(ByteBuf buf, String s, int index) {
        char c = s.charAt(index);

        // Handle surrogate pairs for supplementary characters (emoji, etc.)
        if (Character.isHighSurrogate(c) && index + 1 < s.length()) {
            char low = s.charAt(index + 1);
            if (Character.isLowSurrogate(low)) {
                // Valid surrogate pair - decode to codepoint and encode as 4-byte UTF-8
                int codePoint = Character.toCodePoint(c, low);
                buf.writeByte(0xF0 | (codePoint >> 18));
                buf.writeByte(0x80 | ((codePoint >> 12) & 0x3F));
                buf.writeByte(0x80 | ((codePoint >> 6) & 0x3F));
                buf.writeByte(0x80 | (codePoint & 0x3F));
                return;
            }
        }

        // BMP character (or unpaired surrogate - encode as-is per CESU-8/WTF-8)
        if (c < 0x800) {
            // 2-byte UTF-8
            buf.writeByte(0xC0 | (c >> 6));
            buf.writeByte(0x80 | (c & 0x3F));
        } else {
            // 3-byte UTF-8
            buf.writeByte(0xE0 | (c >> 12));
            buf.writeByte(0x80 | ((c >> 6) & 0x3F));
            buf.writeByte(0x80 | (c & 0x3F));
        }
    }

    private static final byte[] HEX = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    /**
     * Write a JSON array directly to ByteBuf.
     */
    private void writeJsonArray(ByteBuf buf, List<?> list) {
        buf.writeByte('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0)
                buf.writeByte(',');
            writeJsonValue(buf, list.get(i));
        }
        buf.writeByte(']');
    }

    /**
     * Write any JSON value directly to ByteBuf.
     */
    private void writeJsonValue(ByteBuf buf, Object value) {
        switch (value) {
            case null -> buf.writeBytes(NULL_BYTES);
            case String s -> {
                buf.writeByte('"');
                writeEscapedString(buf, s);
                buf.writeByte('"');
            }
            case Integer i -> writeInt(buf, i);
            case Long l -> writeLong(buf, l);
            case Boolean b -> buf.writeBytes(b ? TRUE_BYTES : FALSE_BYTES);
            case List<?> list -> writeJsonArray(buf, list);
            case java.util.Map<?, ?> map -> writeJsonObject(buf, map);
            case Number n -> {
                // Fallback for Double/Float/BigInteger
                String numStr = n.toString();
                for (int idx = 0; idx < numStr.length(); idx++) {
                    buf.writeByte(numStr.charAt(idx));
                }
            }
            default -> {
                // Fallback - convert to string
                buf.writeByte('"');
                writeEscapedString(buf, value.toString());
                buf.writeByte('"');
            }
        }
    }

    /**
     * Write an int value directly to ByteBuf without String allocation.
     *
     * <p><b>Allocation note:</b> The 10-byte digitBuf array is allocated on each call for
     * values >= 10. We deliberately skip ThreadLocal optimization because:
     * <ul>
     *   <li>Array is small (10 bytes) and short-lived</li>
     *   <li>Does not escape this method - eligible for JVM escape analysis</li>
     *   <li>Modern JVMs (HotSpot C2) can perform scalar replacement, allocating on stack</li>
     *   <li>ThreadLocal would add complexity and cleanup concerns (see Keccak256.cleanup())</li>
     * </ul>
     * If profiling shows GC pressure from this allocation, consider ThreadLocal buffers.
     */
    private void writeInt(ByteBuf buf, int value) {
        if (value == Integer.MIN_VALUE) {
            buf.writeBytes("-2147483648".getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (value == 0) {
            buf.writeByte('0');
            return;
        }
        if (value < 0) {
            buf.writeByte('-');
            value = -value;
        }
        if (value < 10) {
            buf.writeByte('0' + value);
            return;
        }
        // Max int is 10 digits - small array eligible for escape analysis
        byte[] digitBuf = new byte[10];
        int pos = 9;
        while (value > 0) {
            digitBuf[pos--] = (byte) ('0' + (value % 10));
            value /= 10;
        }
        buf.writeBytes(digitBuf, pos + 1, 9 - pos);
    }

    /**
     * Write a JSON object directly to ByteBuf.
     */
    private void writeJsonObject(ByteBuf buf, java.util.Map<?, ?> map) {
        buf.writeByte('{');
        boolean first = true;
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first)
                buf.writeByte(',');
            first = false;
            buf.writeByte('"');
            writeEscapedString(buf, entry.getKey().toString());
            buf.writeByte('"');
            buf.writeByte(':');
            writeJsonValue(buf, entry.getValue());
        }
        buf.writeByte('}');
    }

    /**
     * Write a long value directly to ByteBuf without String allocation.
     *
     * <p><b>Allocation note:</b> Same rationale as {@link #writeInt} - the 20-byte digitBuf
     * array is small, short-lived, and eligible for JVM escape analysis / scalar replacement.
     * ThreadLocal optimization skipped due to complexity vs. minimal benefit trade-off.
     */
    private void writeLong(ByteBuf buf, long value) {
        if (value == Long.MIN_VALUE) {
            buf.writeBytes("-9223372036854775808".getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (value == 0) {
            buf.writeByte('0');
            return;
        }

        if (value < 0) {
            buf.writeByte('-');
            value = -value;
        }

        if (value < 10) {
            buf.writeByte((int) ('0' + value));
            return;
        }

        // Find number of digits
        int digits = 0;
        long temp = value;
        while (temp > 0) {
            digits++;
            temp /= 10;
        }

        // Write digits in reverse order - small array eligible for escape analysis
        byte[] digitBuf = new byte[20]; // Max long is 19 digits
        int pos = digits - 1;
        while (value > 0) {
            digitBuf[pos--] = (byte) ('0' + (value % 10));
            value /= 10;
        }

        buf.writeBytes(digitBuf, 0, digits);
    }

    /**
     * Event class for LMAX Disruptor - instances are pre-allocated and reused.
     *
     * <p>The Disruptor maintains a fixed pool of RequestEvent objects in its ring buffer.
     * Each event is reused across multiple requests, so {@link #clear()} must be called
     * after processing to prevent stale data leakage and allow referenced objects to be
     * garbage collected.
     */
    public static class RequestEvent {
        String method;
        List<?> params;
        long id;

        /**
         * Populates this event with request data before publishing to the ring buffer.
         *
         * @param method the JSON-RPC method name
         * @param params the method parameters
         * @param id     the request ID used to correlate responses
         */
        public void set(String method, List<?> params, long id) {
            this.method = method;
            this.params = params;
            this.id = id;
        }

        /**
         * Clears all fields to release references for garbage collection.
         *
         * <p>This method is called after event processing in {@code handleEvent()}
         * to ensure that the params list and any objects it references can be GC'd.
         * Without clearing, large parameter objects would remain referenced until
         * the next request reuses this event slot.
         */
        public void clear() {
            this.method = null;
            this.params = null;
            this.id = 0;
        }
    }
}
