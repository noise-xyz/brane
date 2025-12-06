package io.brane.rpc;

import com.lmax.disruptor.RingBuffer;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ultra-low latency WebSocket provider using Netty and LMAX Disruptor.
 * 
 * Optimizations:
 * - Zero-allocation JSON serialization directly to ByteBuf
 * - Low-allocation response parsing from ByteBuf
 * - Lock-free request ID generation
 * - Batched writes with flush on end-of-batch
 * - Optimized Netty channel options (TCP_NODELAY, etc.)
 * - Large ring buffer for high throughput bursts
 */
public class WebSocketProvider implements BraneProvider, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WebSocketProvider.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    // ==================== Configuration ====================
    static final int MAX_PENDING_REQUESTS = 65536; // Power of 2
    static final int SLOT_MASK = MAX_PENDING_REQUESTS - 1;
    private static final long DEFAULT_TIMEOUT_MS = 60_000;
    private static final long RESPONSE_BUFFER_SIZE = 10 * 1024 * 1024; // 10MB

    // ==================== Connection State ====================
    private final String url;
    private final URI uri;
    private final EventLoopGroup group;
    private Channel channel;
    private final WebSocketClientHandler handler;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // ==================== Request Tracking ====================
    @SuppressWarnings("unchecked")
    private final CompletableFuture<JsonRpcResponse>[] slots = new CompletableFuture[MAX_PENDING_REQUESTS];

    // ==================== Subscriptions ====================
    private final ConcurrentHashMap<String, Consumer<JsonRpcResponse>> subscriptions = new ConcurrentHashMap<>();

    // ==================== Metrics ====================
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

    @SuppressWarnings("unchecked")
    private WebSocketProvider(String url) {
        this.url = url;
        this.uri = URI.create(url);
        this.handler = new WebSocketClientHandler(
                WebSocketClientHandshakerFactory.newHandshaker(
                        uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders()));

        // Single IO thread for minimum context switching
        this.group = new NioEventLoopGroup(1, r -> {
            Thread t = new Thread(r, "brane-netty-io");
            t.setDaemon(true);
            return t;
        });

        // Initialize Disruptor with YieldingWaitStrategy for low latency
        // (busy-spin/yield)
        // rather than BlockingWaitStrategy (locks)
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "brane-disruptor");
            t.setDaemon(true);
            return t;
        };

        this.disruptor = new Disruptor<>(
                RequestEvent::new,
                4096,
                threadFactory,
                ProducerType.MULTI,
                new YieldingWaitStrategy()); // Optimizing for Low Latency over CPU

        // ... (exception handler unchanged)

        this.disruptor.handleEventsWith(this::handleEvent);
        this.disruptor.start();
        this.ringBuffer = disruptor.getRingBuffer();

        connect();
    }

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
                    listener.accept(new JsonRpcResponse("2.0", result, null, null));
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
                int slot = (int) (id & SLOT_MASK);
                CompletableFuture<JsonRpcResponse> future = slots[slot];
                if (future != null) {
                    slots[slot] = null;
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

    public CompletableFuture<JsonRpcResponse> sendAsync(String method, List<?> params) {
        long id = idGenerator.getAndIncrement();
        int slot = (int) (id & SLOT_MASK);

        CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();
        slots[slot] = future;

        Channel ch = this.channel;
        if (ch != null && ch.isActive()) {
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
     * Async send using Disruptor for high-throughput batch scenarios.
     * Use this when sending many requests in rapid succession.
     */

    public CompletableFuture<JsonRpcResponse> sendAsyncBatch(String method, List<?> params) {
        long id = idGenerator.getAndIncrement();
        int slot = (int) (id & SLOT_MASK);

        CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();
        slots[slot] = future;

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

    @Override
    public void close() {
        closed.set(true);
        disruptor.shutdown();
        if (channel != null) {
            channel.close();
        }
        group.shutdownGracefully();
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
        for (int i = 0; i < slots.length; i++) {
            CompletableFuture<JsonRpcResponse> f = slots[i];
            if (f != null) {
                slots[i] = null;
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
                int slot = (int) (event.id & SLOT_MASK);
                CompletableFuture<JsonRpcResponse> future = slots[slot];
                if (future != null) {
                    slots[slot] = null;
                    future.completeExceptionally(e);
                }
            }

            // Flush at end of batch for maximum throughput
            if (endOfBatch) {
                ch.flush();
            }
        } else {
            // Channel not active - fail the request
            int slot = (int) (event.id & SLOT_MASK);
            CompletableFuture<JsonRpcResponse> future = slots[slot];
            if (future != null) {
                slots[slot] = null;
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
