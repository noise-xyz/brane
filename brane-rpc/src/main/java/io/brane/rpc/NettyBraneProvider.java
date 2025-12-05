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
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Ultra-low latency WebSocket provider using Netty and LMAX Disruptor.
 * 
 * Optimizations:
 * - Zero-allocation JSON serialization directly to ByteBuf
 * - Zero-copy response parsing from ByteBuf
 * - Lock-free request ID generation
 * - Batched writes with flush on end-of-batch
 * - Optimized Netty channel options (TCP_NODELAY, etc.)
 * - Large ring buffer for high throughput bursts
 */
public class NettyBraneProvider implements BraneProvider, AutoCloseable {

    private final URI uri;
    private final EventLoopGroup group;
    private volatile Channel channel;

    // Lock-free ID generator
    private final AtomicLong idGenerator = new AtomicLong(1);

    // Disruptor for request processing
    private final Disruptor<RequestEvent> disruptor;
    private final RingBuffer<RequestEvent> ringBuffer;

    // Pending request slots - power of 2 for fast masking
    private final CompletableFuture<JsonRpcResponse>[] slots;
    private static final int MAX_PENDING = 65536; // Increased for high throughput
    private static final int SLOT_MASK = MAX_PENDING - 1;

    // Pre-computed JSON fragments as bytes for zero-allocation serialization
    private static final byte[] JSON_PREFIX = "{\"jsonrpc\":\"2.0\",\"method\":\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PARAMS_PREFIX = "\",\"params\":".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ID_PREFIX = ",\"id\":".getBytes(StandardCharsets.UTF_8);
    private static final byte JSON_SUFFIX = '}';
    private static final byte[] EMPTY_PARAMS = "[]".getBytes(StandardCharsets.UTF_8);

    // Subscription handling
    private final java.util.Map<String, Consumer<JsonRpcResponse>> subscriptions = new java.util.concurrent.ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private NettyBraneProvider(String url) {
        this.uri = URI.create(url);
        this.slots = new CompletableFuture[MAX_PENDING];

        // Single IO thread for minimum context switching
        this.group = new NioEventLoopGroup(1, r -> {
            Thread t = new Thread(r, "brane-netty-io");
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY);
            return t;
        });

        // Initialize Disruptor with BusySpinWaitStrategy for lowest latency
        // Larger ring buffer (4096) to handle burst traffic without blocking
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "brane-disruptor");
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY);
            return t;
        };

        this.disruptor = new Disruptor<>(
                RequestEvent::new,
                4096, // Larger ring buffer for burst handling
                threadFactory,
                ProducerType.MULTI,
                new YieldingWaitStrategy()); // Lower CPU usage, good latency

        this.disruptor.setDefaultExceptionHandler(new com.lmax.disruptor.ExceptionHandler<RequestEvent>() {
            @Override
            public void handleEventException(Throwable ex, long sequence, RequestEvent event) {
                System.err.println("Disruptor exception at seq " + sequence + ": " + ex.getMessage());
                // Complete the future exceptionally if possible
                int slot = (int) (event.id & SLOT_MASK);
                CompletableFuture<JsonRpcResponse> future = slots[slot];
                if (future != null) {
                    slots[slot] = null;
                    future.completeExceptionally(ex);
                }
            }

            @Override
            public void handleOnStartException(Throwable ex) {
                System.err.println("Disruptor start exception: " + ex.getMessage());
            }

            @Override
            public void handleOnShutdownException(Throwable ex) {
                System.err.println("Disruptor shutdown exception: " + ex.getMessage());
            }
        });

        this.disruptor.handleEventsWith(this::handleEvent);
        this.disruptor.start();
        this.ringBuffer = disruptor.getRingBuffer();

        connect();
    }

    public static NettyBraneProvider create(String url) {
        return new NettyBraneProvider(url);
    }

    private void connect() {
        try {
            final SslContext sslCtx;
            if ("wss".equalsIgnoreCase(uri.getScheme())) {
                sslCtx = SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
            } else {
                sslCtx = null;
            }

            WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                    uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders(), 65536);

            WebSocketClientHandler handler = new WebSocketClientHandler(handshaker);

            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    // Low-latency socket options
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.WRITE_BUFFER_WATER_MARK,
                            new WriteBufferWaterMark(32 * 1024, 64 * 1024))
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            if (sslCtx != null) {
                                p.addLast(sslCtx.newHandler(ch.alloc(), uri.getHost(),
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

            this.channel = b.connect(uri.getHost(), port).sync().channel();
            handler.handshakeFuture().sync();
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to " + uri, e);
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * Zero-copy ID extraction from ByteBuf.
     * Scans for "id": pattern and parses the number directly.
     * Visible for testing.
     */
    static long parseIdFromByteBuf(ByteBuf buf) {
        int readableBytes = buf.readableBytes();
        int readerIndex = buf.readerIndex();

        // Scan for "id":
        for (int i = readerIndex; i < readerIndex + readableBytes - 5; i++) {
            if (buf.getByte(i) == '"' &&
                    buf.getByte(i + 1) == 'i' &&
                    buf.getByte(i + 2) == 'd' &&
                    buf.getByte(i + 3) == '"' &&
                    buf.getByte(i + 4) == ':') {

                // Found "id":, now parse the number
                int numStart = i + 5;
                // Skip whitespace
                while (numStart < readerIndex + readableBytes &&
                        (buf.getByte(numStart) == ' ' || buf.getByte(numStart) == '\t')) {
                    numStart++;
                }

                // Parse number
                long result = 0;
                boolean negative = false;
                if (numStart < readerIndex + readableBytes && buf.getByte(numStart) == '-') {
                    negative = true;
                    numStart++;
                }

                int numEnd = numStart;
                while (numEnd < readerIndex + readableBytes) {
                    byte b = buf.getByte(numEnd);
                    if (b >= '0' && b <= '9') {
                        result = result * 10 + (b - '0');
                        numEnd++;
                    } else {
                        break;
                    }
                }

                if (numEnd > numStart) {
                    return negative ? -result : result;
                }
            }
        }
        return -1;
    }

    /**
     * Check if ByteBuf contains subscription notification.
     * Visible for testing.
     */
    static boolean containsSubscription(ByteBuf buf) {
        int readableBytes = buf.readableBytes();
        int readerIndex = buf.readerIndex();

        // Look for "method":"eth_subscription"
        byte[] pattern = "\"method\":\"eth_subscription\"".getBytes(StandardCharsets.UTF_8);

        outer: for (int i = readerIndex; i <= readerIndex + readableBytes - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (buf.getByte(i + j) != pattern[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Parse JSON-RPC response from ByteBuf.
     * Visible for testing.
     */
    static JsonRpcResponse parseResponseFromByteBuf(ByteBuf buf) {
        String json = buf.toString(CharsetUtil.UTF_8);

        // Quick check for error
        int errorIdx = json.indexOf("\"error\"");
        if (errorIdx != -1 && json.indexOf("\"error\":null") == -1) {
            // Has error - use Jackson for complex parsing
            try {
                return mapper.readValue(json, JsonRpcResponse.class);
            } catch (Exception e) {
                return new JsonRpcResponse("2.0", null,
                        new JsonRpcError(-32700, "Parse error", null), null);
            }
        }

        // Extract result - find "result": and extract value
        int resultIdx = json.indexOf("\"result\":");
        if (resultIdx != -1) {
            int valueStart = resultIdx + 9;
            // Skip whitespace
            while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
                valueStart++;
            }

            if (valueStart >= json.length()) {
                return new JsonRpcResponse("2.0", null, null, null);
            }

            char firstChar = json.charAt(valueStart);
            if (firstChar == 'n' && json.startsWith("null", valueStart)) {
                return new JsonRpcResponse("2.0", null, null, null);
            } else if (firstChar == '{' || firstChar == '[') {
                // Complex type - parse with Jackson
                String resultJson = extractJsonValue(json, valueStart);
                try {
                    Object result = mapper.readValue(resultJson, Object.class);
                    return new JsonRpcResponse("2.0", result, null, null);
                } catch (Exception e) {
                    // Fallback to full parse
                    try {
                        return mapper.readValue(json, JsonRpcResponse.class);
                    } catch (Exception ex) {
                        return new JsonRpcResponse("2.0", null, new JsonRpcError(-32700, "Parse error", null), null);
                    }
                }
            } else {
                // Primitive - extract directly
                String result = extractJsonValue(json, valueStart);
                return new JsonRpcResponse("2.0", result, null, null);
            }
        }

        return new JsonRpcResponse("2.0", null, null, null);
    }

    /**
     * Extract a JSON value starting at the given position.
     * Visible for testing.
     */
    static String extractJsonValue(String json, int start) {
        if (start >= json.length())
            return null;

        char c = json.charAt(start);

        if (c == '"') {
            // String value
            int end = start + 1;
            while (end < json.length()) {
                char ch = json.charAt(end);
                if (ch == '\\') {
                    end += 2;
                } else if (ch == '"') {
                    return json.substring(start + 1, end);
                } else {
                    end++;
                }
            }
        } else if (c == '{' || c == '[') {
            // Object or array - find matching bracket
            int depth = 1;
            int end = start + 1;
            char open = c;
            char close = c == '{' ? '}' : ']';

            while (end < json.length() && depth > 0) {
                char ch = json.charAt(end);
                if (ch == '"') {
                    // Skip string
                    end++;
                    while (end < json.length()) {
                        if (json.charAt(end) == '\\') {
                            end += 2;
                        } else if (json.charAt(end) == '"') {
                            end++;
                            break;
                        } else {
                            end++;
                        }
                    }
                    continue;
                }
                if (ch == open)
                    depth++;
                else if (ch == close)
                    depth--;
                end++;
            }
            return json.substring(start, end);
        } else {
            // Primitive (number, boolean, null)
            int end = start;
            while (end < json.length()) {
                char ch = json.charAt(end);
                if (ch == ',' || ch == '}' || ch == ']' || Character.isWhitespace(ch)) {
                    break;
                }
                end++;
            }
            return json.substring(start, end);
        }

        return null;
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
            // Connection lost - could implement reconnection here
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

            WebSocketFrame frame = (WebSocketFrame) msg;
            if (frame instanceof TextWebSocketFrame) {
                TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
                ByteBuf content = textFrame.content();

                // Zero-copy parse - extract ID directly from ByteBuf
                long id = parseIdFromByteBuf(content);
                if (id != -1) {
                    int slot = (int) (id & SLOT_MASK);
                    CompletableFuture<JsonRpcResponse> future = slots[slot];
                    if (future != null) {
                        slots[slot] = null;
                        // Parse response - still need to convert to string for result extraction
                        JsonRpcResponse response = parseResponseFromByteBuf(content);
                        future.complete(response);
                    }
                } else {
                    // Check for subscription notification
                    if (containsSubscription(content)) {
                        handleNotification(content);
                    }
                }
            } else if (frame instanceof CloseWebSocketFrame) {
                ch.close();
            }
        }

        private void handleNotification(ByteBuf buf) {
            try {
                String json = buf.toString(CharsetUtil.UTF_8);
                com.fasterxml.jackson.databind.JsonNode node = NettyBraneProvider.mapper.readTree(json);
                com.fasterxml.jackson.databind.JsonNode params = node.get("params");
                if (params != null) {
                    String subscriptionId = params.get("subscription").asText();
                    Consumer<JsonRpcResponse> listener = subscriptions.get(subscriptionId);
                    if (listener != null) {
                        Object result = mapper.treeToValue(params.get("result"), Object.class);
                        listener.accept(new JsonRpcResponse("2.0", result, null, null));
                    }
                }
            } catch (Exception e) {
                // Ignore notification parsing errors
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (!handshakeFuture.isDone()) {
                handshakeFuture.setFailure(cause);
            }
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
        // Generate unique ID
        long id = idGenerator.getAndIncrement();
        int slot = (int) (id & SLOT_MASK);

        // Create future first to avoid race condition
        CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();
        slots[slot] = future;

        // Direct write path - bypass Disruptor for lower latency on single requests
        // This writes directly to the Netty event loop, avoiding the Disruptor overhead
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
            future.completeExceptionally(new RpcException(-1, "Channel not active", null, (Throwable) null));
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
                throw new RpcException(response.error().code(), response.error().message(), null, (Throwable) null);
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
        disruptor.shutdown();
        if (channel != null) {
            channel.close();
        }
        group.shutdownGracefully();
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
                future.completeExceptionally(new RpcException(-1, "Channel not active", null, (Throwable) null));
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
            buf.writeBytes("null".getBytes(StandardCharsets.UTF_8));
        } else if (value instanceof String) {
            buf.writeByte('"');
            writeEscapedString(buf, (String) value);
            buf.writeByte('"');
        } else if (value instanceof Number) {
            String numStr = value.toString();
            for (int i = 0; i < numStr.length(); i++) {
                buf.writeByte(numStr.charAt(i));
            }
        } else if (value instanceof Boolean) {
            buf.writeBytes(((Boolean) value ? "true" : "false").getBytes(StandardCharsets.UTF_8));
        } else if (value instanceof List) {
            writeJsonArray(buf, (List<?>) value);
        } else if (value instanceof java.util.Map) {
            writeJsonObject(buf, (java.util.Map<?, ?>) value);
        } else {
            // Fallback - convert to string
            buf.writeByte('"');
            writeEscapedString(buf, value.toString());
            buf.writeByte('"');
        }
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
        if (value == 0) {
            buf.writeByte('0');
            return;
        }

        if (value < 0) {
            buf.writeByte('-');
            value = -value;
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
