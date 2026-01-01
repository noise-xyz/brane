package io.brane.rpc;

import static io.brane.rpc.internal.RpcUtils.MAPPER;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.brane.core.DebugLogger;
import io.brane.core.LogFormatter;
import io.brane.core.error.RpcException;
import io.brane.rpc.internal.RpcUtils;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class HttpBraneProvider implements BraneProvider {

    private final RpcConfig config;
    private final java.net.http.HttpClient httpClient;
    private final java.util.concurrent.ExecutorService executor;
    private final AtomicLong ids = new AtomicLong(1L);

    private HttpBraneProvider(final RpcConfig config) {
        this.config = config;
        this.executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        this.httpClient = java.net.http.HttpClient.newBuilder()
                .executor(executor)
                .connectTimeout(config.connectTimeout())
                .build();
    }

    /**
     * Closes this provider and releases associated resources.
     * <p>
     * This method shuts down the internal HTTP client and its executor service.
     * After calling this method, the provider should not be used for further requests.
     */
    @Override
    public void close() {
        httpClient.close();
        executor.close();
    }

    public static Builder builder(final String url) {
        return new Builder(url);
    }

    @Override
    public JsonRpcResponse send(final String method, final List<?> params) throws RpcException {
        Objects.requireNonNull(method, "method");
        final List<?> safeParams = params == null ? List.of() : params;
        final long requestId = ids.getAndIncrement();
        final JsonRpcRequest request = new JsonRpcRequest("2.0", method, safeParams, String.valueOf(requestId));

        final String payload = serialize(request, requestId);
        final HttpRequest httpRequest = buildRequest(payload);

        final long start = System.nanoTime();
        final HttpResponse<String> response = execute(httpRequest, requestId);
        final long durationMicros = (System.nanoTime() - start) / 1_000L;

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            DebugLogger.logRpc(
                    LogFormatter.formatRpcError(method, response.statusCode(),
                            "HTTP " + response.statusCode(), durationMicros));
            throw new RpcException(
                    -32001,
                    "HTTP error for method " + method + ": " + response.statusCode(),
                    response.body(),
                    requestId,
                    null);
        }

        final String responseBody = response.body();
        final JsonRpcResponse rpcResponse = parseResponse(method, responseBody, requestId);
        if (rpcResponse.hasError()) {
            final JsonRpcError err = rpcResponse.error();
            DebugLogger.logRpc(
                    LogFormatter.formatRpcError(method, err.code(), err.message(), durationMicros));
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), requestId);
        }

        DebugLogger.logRpc(LogFormatter.formatRpc(method, durationMicros));
        return rpcResponse;
    }

    private String serialize(final JsonRpcRequest request, final long requestId) throws RpcException {
        try {
            return MAPPER.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RpcException(
                    -32700,
                    "Unable to serialize JSON-RPC request for " + request.method(),
                    null,
                    requestId,
                    e);
        }
    }

    private HttpRequest buildRequest(final String payload) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.url()))
                .header("Content-Type", "application/json")
                .timeout(config.readTimeout())
                .POST(HttpRequest.BodyPublishers.ofString(payload));

        for (Map.Entry<String, String> entry : config.headers().entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }

        return builder.build();
    }

    private HttpResponse<String> execute(final HttpRequest request, final long requestId)
            throws RpcException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RpcException(
                    -32000, "Network error during JSON-RPC call", null, requestId, e);
        } catch (IOException e) {
            throw new RpcException(-32000, "Network error during JSON-RPC call", null, requestId, e);
        }
    }

    private JsonRpcResponse parseResponse(final String method, final String body, final long requestId)
            throws RpcException {
        try {
            return MAPPER.readValue(body, JsonRpcResponse.class);
        } catch (JsonProcessingException e) {
            throw new RpcException(
                    -32700,
                    "Unable to parse JSON-RPC response for method " + method,
                    body,
                    requestId,
                    e);
        }
    }

    public static final class Builder {
        private final String url;
        private Long chainId;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);
        private final Map<String, String> headers = new LinkedHashMap<>();

        private Builder(final String url) {
            this.url = url;
        }

        public Builder chainId(final Long chainId) {
            this.chainId = chainId;
            return this;
        }

        public Builder connectTimeout(final Duration connectTimeout) {
            if (connectTimeout != null) {
                this.connectTimeout = connectTimeout;
            }
            return this;
        }

        public Builder readTimeout(final Duration readTimeout) {
            if (readTimeout != null) {
                this.readTimeout = readTimeout;
            }
            return this;
        }

        public Builder header(final String key, final String value) {
            headers.put(key, value);
            return this;
        }

        public HttpBraneProvider build() {
            final RpcConfig config = new RpcConfig(url, chainId, connectTimeout, readTimeout,
                    new LinkedHashMap<>(headers));
            return new HttpBraneProvider(config);
        }
    }
}
