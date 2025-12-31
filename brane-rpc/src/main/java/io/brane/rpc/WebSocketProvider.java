package io.brane.rpc;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import io.brane.core.error.RpcException;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.EventLoopGroup;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ultra-low latency WebSocket provider using Netty and LMAX Disruptor.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Multiple threads can
 * safely call {@link #send}, {@link #sendAsync}, and {@link #sendAsyncBatch}
 * concurrently. Request tracking uses an {@link AtomicReferenceArray} with
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
    private static final ObjectMapper mapper = new ObjectMapper();

    // ==================== Configuration ====================
    private static final int DEFAULT_MAX_PENDING_REQUESTS = 65536; // Power of 2
    private static final long DEFAULT_TIMEOUT_MS = 60_000;
    private static final long RESPONSE_BUFFER_SIZE = 10 * 1024 * 1024; // 10MB

    // Instance configuration (from WebSocketConfig or defaults)
    private final int maxPendingRequests;
    private final int slotMask;
    private final Duration defaultRequestTimeout;

    // ==================== Connection State ====================
    private final String url;
    private final URI uri;
    private final EventLoopGroup group;
    /** True if we created the EventLoopGroup internally and are responsible for shutting it down. */
    private final boolean ownsEventLoopGroup;
    private Channel channel;
    private final WebSocketClientHandler handler;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // ==================== Request Tracking ====================
    /**
     * Thread-safe slot array for tracking pending requests.
     * Uses AtomicReferenceArray to ensure proper visibility and CAS-based allocation.
     */
    private final AtomicReferenceArray<CompletableFuture<JsonRpcResponse>> slots;

    // ==================== Subscriptions ====================
    private final ConcurrentHashMap<String, Consumer<JsonRpcResponse>> subscriptions = new ConcurrentHashMap<>();

    /**
     * Executor for subscription callbacks. Defaults to virtual threads.
     * Callbacks are dispatched to this executor to avoid blocking the Netty I/O
     * thread.
     */
    private volatile Executor subscriptionExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Sets a custom executor for subscription callbacks.
     *
     * <p>
     * By default, subscription callbacks run on virtual threads to avoid blocking
     * the Netty I/O thread. Use this method to provide a custom executor if you
     * need
     * specific threading behavior (e.g., a bounded pool, or the same thread as
     * Netty).
     *
     * <p>
     * <strong>Warning:</strong> If you set an executor that runs callbacks on the
     * Netty I/O thread (or any single thread), ensure callbacks complete quickly.
     * Blocking operations will stall all WebSocket I/O.
     *
     * @param executor the executor to use for subscription callbacks (must not be
     *                 null)
     * @throws NullPointerException if executor is null
     */
    public void setSubscriptionExecutor(Executor executor) {
        this.subscriptionExecutor = Objects.requireNonNull(executor, "executor");
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

    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder totalResponses = new LongAdder();
    private final LongAdder totalErrors = new LongAdder();

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

    private WebSocketProvider(WebSocketConfig config) {
        this.url = config.url();
        this.uri = URI.create(config.url());
        this.maxPendingRequests = config.maxPendingRequests();
        this.slotMask = maxPendingRequests - 1;
        this.defaultRequestTimeout = config.defaultRequestTimeout();
        this.slots = new AtomicReferenceArray<>(maxPendingRequests);

        this.handler = new WebSocketClientHandler(
                WebSocketClientHandshakerFactory.newHandshaker(
                        uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders()));

        // Use provided EventLoopGroup or create default
        if (config.eventLoopGroup() != null) {
            this.group = config.eventLoopGroup();
            this.ownsEventLoopGroup = false; // External group - caller is responsible for lifecycle
        } else {
            this.group = new NioEventLoopGroup(config.ioThreads(), r -> {
                Thread t = new Thread(r, "brane-netty-io");
                t.setDaemon(true);
                return t;
            });
            this.ownsEventLoopGroup = true; // Internal group - we manage lifecycle
        }

        // Configurable wait strategy
        WaitStrategy waitStrategy = switch (config.waitStrategy()) {
            case BLOCKING -> new BlockingWaitStrategy();
            case YIELDING -> new YieldingWaitStrategy();
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

            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
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
                            p.addLast(handler);
                        }
                    });

            int port = uri.getPort();
            if (port == -1) {
                port = "wss".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            }

            int attempt = 0;
            long delay = 100;
            Exception lastError = null;

            while (attempt < 5 && !closed.get()) {
                try {
                    this.channel = b.connect(uri.getHost(), port).sync().channel();
                    handler.handshakeFuture().sync();
                    connected.set(true);
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
                        break;
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

    /**
     * Parse JSON-RPC response from ByteBuf using Jackson Streaming API.
     * Visible for testing.
     */
    static JsonRpcResponse parseResponseFromByteBuf(ByteBuf buf) {
        try (ByteBufInputStream in = new ByteBufInputStream(buf);
                JsonParser parser = mapper.getFactory().createParser((java.io.InputStream) in)) {

            String jsonrpc = null;
            Object id = null;
            Object result = null;
            JsonRpcError error = null;

            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return new JsonRpcResponse("2.0", null, null, null);
            }

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();

                if ("jsonrpc".equals(fieldName)) {
                    jsonrpc = parser.getText();
                } else if ("id".equals(fieldName)) {
                    if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                        id = String.valueOf(parser.getLongValue());
                    } else if (parser.currentToken() == JsonToken.VALUE_STRING) {
                        id = parser.getText();
                    } else {
                        id = null;
                    }
                } else if ("result".equals(fieldName)) {
                    result = mapper.readValue(parser, Object.class);
                } else if ("error".equals(fieldName)) {
                    error = mapper.readValue(parser, JsonRpcError.class);
                } else {
                    parser.skipChildren();
                }
            }
            return new JsonRpcResponse(jsonrpc, result, error, (String) id);
        } catch (Exception e) {
            return new JsonRpcResponse("2.0", null, new JsonRpcError(-32700, "Parse error: " + e.getMessage(), null),
                    null);
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
                log.warn("Connection lost, triggering reconnect");
                reconnect();
            }
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            Channel ch = ctx.channel();
            if (!handshaker.isHandshakeComplete()) {
                try {
                    handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                    handshakeFuture.setSuccess();
                } catch (WebSocketHandshakeException e) {
                    handshakeFuture.setFailure(e);
                }
                return;
            }

            if (msg instanceof FullHttpResponse) {
                FullHttpResponse response = (FullHttpResponse) msg;
                throw new IllegalStateException(
                        "Unexpected FullHttpResponse (status=" + response.status() + ")");
            }

            if (msg instanceof WebSocketFrame) {
                WebSocketFrame frame = (WebSocketFrame) msg;
                if (frame instanceof TextWebSocketFrame) {
                    TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
                    ByteBuf content = textFrame.content();
                    try {
                        try (JsonParser parser = mapper.getFactory()
                                .createParser((java.io.InputStream) new ByteBufInputStream(content))) {
                            JsonNode node = mapper.readTree(parser);

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
            JsonNode params = node.get("params");
            if (params != null) {
                String subId = params.get("subscription").asText();
                Consumer<JsonRpcResponse> listener = subscriptions.get(subId);
                if (listener != null) {
                    JsonNode resultNode = params.get("result");
                    Object result = mapper.convertValue(resultNode, Object.class);
                    JsonRpcResponse response = new JsonRpcResponse("2.0", result, null, null);
                    // Dispatch to subscription executor to avoid blocking Netty I/O thread
                    subscriptionExecutor.execute(() -> listener.accept(response));
                }
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
                    log.warn("Could not parse text response ID '{}' as long", idNode.asText(), e);
                }
            }

            if (id != -1) {
                int slot = (int) (id & slotMask);
                // Atomically get and clear the slot to prevent race conditions
                CompletableFuture<JsonRpcResponse> future = slots.getAndSet(slot, null);
                if (future != null) {
                    JsonNode errorNode = node.get("error");
                    JsonRpcError error = null;
                    if (errorNode != null && !errorNode.isNull()) {
                        error = mapper.treeToValue(errorNode, JsonRpcError.class);
                    }
                    Object result = null;
                    if (node.has("result")) {
                        result = mapper.treeToValue(node.get("result"), Object.class);
                    }
                    future.complete(new JsonRpcResponse("2.0", result, error, String.valueOf(id)));
                }
            }
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
            if (e.getCause() instanceof RpcException)
                throw (RpcException) e.getCause();
            throw new RpcException(-1, "Request failed", null, e);
        }
    }

    /**
     * Allocates a slot for a new request with backpressure handling.
     * Uses CAS-based allocation to ensure thread-safety.
     *
     * @param id the request ID
     * @return the allocated future, or a failed future if backpressure is triggered
     */
    private CompletableFuture<JsonRpcResponse> allocateSlot(long id) {
        int slot = (int) (id & slotMask);
        CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();

        // CAS loop to atomically allocate slot
        while (true) {
            CompletableFuture<JsonRpcResponse> existing = slots.get(slot);
            if (existing != null && !existing.isDone()) {
                // Slot is in use by an active request - backpressure
                metrics.onBackpressure();
                return CompletableFuture.failedFuture(new io.brane.core.error.RpcException(
                        -32000,
                        "Too many pending requests (" + maxPendingRequests + " limit reached, slot " + slot + " in use)",
                        null));
            }
            // Slot is either null or contains a completed future - try to claim it
            if (slots.compareAndSet(slot, existing, future)) {
                return future;
            }
            // CAS failed, another thread modified the slot - retry
        }
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
        long id = idGenerator.getAndIncrement();
        int slot = (int) (id & slotMask);

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
                        // Use CAS to only clear if slot still contains our future
                        slots.compareAndSet(slot, future, null);
                        metrics.onRequestTimeout(method);
                        future.completeExceptionally(new io.brane.core.error.RpcException(
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
            future.completeExceptionally(new RpcException(-1, "Channel not active", null));
        }

        return future;
    }

    /**
     * Sends an asynchronous JSON-RPC request using the Disruptor ring buffer.
     *
     * <p>
     * This method is optimized for high-throughput scenarios where many requests
     * are sent in rapid succession. Requests are batched and flushed together,
     * reducing syscall overhead.
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
     */
    public CompletableFuture<JsonRpcResponse> sendAsyncBatch(String method, List<?> params) {
        long id = idGenerator.getAndIncrement();
        int slot = (int) (id & slotMask);

        CompletableFuture<JsonRpcResponse> future = allocateSlot(id);
        if (future.isCompletedExceptionally()) {
            return future; // Backpressure triggered
        }

        // Check ring buffer saturation before publishing (metrics hook for early
        // warning)
        int bufferSize = ringBuffer.getBufferSize();
        long remainingCapacity = ringBuffer.remainingCapacity();
        if (remainingCapacity < bufferSize * 0.1) { // 10% threshold
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
                throw new RpcException(response.error().code(), response.error().message(),
                        response.error().data() != null ? response.error().data().toString() : null);
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
            if (e instanceof RpcException)
                throw (RpcException) e;
            throw new RpcException(-1, "Subscription failed", null, e);
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
            throw new RpcException(-1, "Unsubscribe failed", null, e);
        }
    }

    /**
     * Closes the WebSocket connection and releases all resources.
     *
     * <p>
     * This method:
     * </p>
     * <ul>
     * <li>Shuts down the Disruptor</li>
     * <li>Closes the WebSocket channel</li>
     * <li>Shuts down the Netty event loop group (only if created internally)</li>
     * <li>Fails any pending requests with an exception</li>
     * </ul>
     *
     * <p>
     * <b>EventLoopGroup ownership:</b> If an {@code EventLoopGroup} was provided via
     * {@link WebSocketConfig#eventLoopGroup()}, it will NOT be shut down by this method.
     * The caller is responsible for managing the lifecycle of externally-provided groups.
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
        if (!closed.get()) {
            group.schedule(() -> {
                if (!connected.get() && !closed.get()) {
                    log.info("Attempting reconnect to {}", uri);
                    try {
                        connect();
                    } catch (Exception e) {
                        log.error("Reconnect failed", e);
                        reconnect();
                    }
                }
            }, 1, TimeUnit.SECONDS);
        }
    }

    private void failAllPending(RpcException e) {
        connected.set(false);
        for (int i = 0; i < slots.length(); i++) {
            // Atomically get and clear each slot
            CompletableFuture<JsonRpcResponse> f = slots.getAndSet(i, null);
            if (f != null) {
                f.completeExceptionally(e);
            }
        }
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
                int slot = (int) (event.id & slotMask);
                // Atomically get and clear the slot
                CompletableFuture<JsonRpcResponse> future = slots.getAndSet(slot, null);
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
            int slot = (int) (event.id & slotMask);
            // Atomically get and clear the slot
            CompletableFuture<JsonRpcResponse> future = slots.getAndSet(slot, null);
            if (future != null) {
                future.completeExceptionally(new RpcException(-1, "Channel not active", null));
            }
        }
    }

    /**
     * Write a string with JSON escaping directly to ByteBuf.
     */
    private void writeEscapedString(ByteBuf buf, String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    buf.writeByte('\\');
                    buf.writeByte('"');
                    break;
                case '\\':
                    buf.writeByte('\\');
                    buf.writeByte('\\');
                    break;
                case '\n':
                    buf.writeByte('\\');
                    buf.writeByte('n');
                    break;
                case '\r':
                    buf.writeByte('\\');
                    buf.writeByte('r');
                    break;
                case '\t':
                    buf.writeByte('\\');
                    buf.writeByte('t');
                    break;
                default:
                    if (c < 32) {
                        // Control character - escape as unicode
                        buf.writeByte('\\');
                        buf.writeByte('u');
                        buf.writeByte(HEX[(c >> 12) & 0xF]);
                        buf.writeByte(HEX[(c >> 8) & 0xF]);
                        buf.writeByte(HEX[(c >> 4) & 0xF]);
                        buf.writeByte(HEX[c & 0xF]);
                    } else {
                        buf.writeByte(c);
                    }
            }
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
        if (value == null) {
            buf.writeBytes(NULL_BYTES);
        } else if (value instanceof String) {
            buf.writeByte('"');
            writeEscapedString(buf, (String) value);
            buf.writeByte('"');
        } else if (value instanceof Integer) {
            writeInt(buf, (Integer) value);
        } else if (value instanceof Long) {
            writeLong(buf, (Long) value);
        } else if (value instanceof Boolean) {
            buf.writeBytes((Boolean) value ? TRUE_BYTES : FALSE_BYTES);
        } else if (value instanceof List) {
            writeJsonArray(buf, (List<?>) value);
        } else if (value instanceof java.util.Map) {
            writeJsonObject(buf, (java.util.Map<?, ?>) value);
        } else if (value instanceof Number) {
            // Fallback for Double/Float/BigInteger
            String numStr = value.toString();
            for (int i = 0; i < numStr.length(); i++) {
                buf.writeByte(numStr.charAt(i));
            }
        } else {
            // Fallback - convert to string
            buf.writeByte('"');
            writeEscapedString(buf, value.toString());
            buf.writeByte('"');
        }
    }

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
        // Max int is 10 digits
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

        // Write digits in reverse order using a small buffer
        byte[] digitBuf = new byte[20]; // Max long is 19 digits
        int pos = digits - 1;
        while (value > 0) {
            digitBuf[pos--] = (byte) ('0' + (value % 10));
            value /= 10;
        }

        buf.writeBytes(digitBuf, 0, digits);
    }

    // Event class for Disruptor - pre-allocated and reused
    public static class RequestEvent {
        String method;
        List<?> params;
        long id;

        public void set(String method, List<?> params, long id) {
            this.method = method;
            this.params = params;
            this.id = id;
        }

        public void clear() {
            this.method = null;
            this.params = null;
            this.id = 0;
        }
    }
}
