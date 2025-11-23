package io.brane.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.brane.core.DebugLogger;
import io.brane.core.error.RpcException;
import java.lang.reflect.Array;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class HttpBraneProvider implements BraneProvider {

    private final RpcConfig config;
    private final java.net.http.HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong ids = new AtomicLong(1L);

    private HttpBraneProvider(final RpcConfig config) {
        this.config = config;
        this.httpClient =
                java.net.http.HttpClient.newBuilder()
                        .executor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor())
                        .connectTimeout(config.connectTimeout())
                        .build();
    }

    public static Builder builder(final String url) {
        return new Builder(url);
    }

    @Override
    public JsonRpcResponse send(final String method, final List<?> params) throws RpcException {
        final List<?> safeParams = params == null ? List.of() : params;
        final JsonRpcRequest request =
                new JsonRpcRequest("2.0", method, safeParams, String.valueOf(ids.getAndIncrement()));

        final String payload = serialize(request);
        final HttpRequest httpRequest = buildRequest(payload);

        final long start = System.nanoTime();
        final HttpResponse<String> response = execute(httpRequest);
        final long durationMicros = (System.nanoTime() - start) / 1_000L;

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            DebugLogger.log(
                    "[RPC-ERROR] method=%s status=%s durationMicros=%s body=%s",
                    method, response.statusCode(), durationMicros, response.body());
            throw new RpcException(
                    -32001,
                    "HTTP error for method " + method + ": " + response.statusCode(),
                    response.body(),
                    null);
        }

        final String responseBody = response.body();
        final JsonRpcResponse rpcResponse = parseResponse(method, responseBody);
        if (rpcResponse.hasError()) {
            final JsonRpcError err = rpcResponse.error();
            DebugLogger.log(
                    "[RPC-ERROR] method=%s code=%s message=%s data=%s durationMicros=%s",
                    method, err.code(), err.message(), err.data(), durationMicros);
            throw new RpcException(err.code(), err.message(), extractErrorData(err.data()), null);
        }

        DebugLogger.log(
                "[RPC] method=%s durationMicros=%s request=%s response=%s",
                method, durationMicros, payload, responseBody);
        return rpcResponse;
    }

    private String serialize(final JsonRpcRequest request) throws RpcException {
        try {
            return mapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RpcException(
                    -32700, "Unable to serialize JSON-RPC request for " + request.method(), null, e);
        }
    }

    private HttpRequest buildRequest(final String payload) {
        final HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create(config.url()))
                        .header("Content-Type", "application/json")
                        .timeout(config.readTimeout())
                        .POST(HttpRequest.BodyPublishers.ofString(payload));

        for (Map.Entry<String, String> entry : config.headers().entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }

        return builder.build();
    }

    private HttpResponse<String> execute(final HttpRequest request) throws RpcException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RpcException(-32000, "Network error during JSON-RPC call", null, e);
        } catch (IOException e) {
            throw new RpcException(-32000, "Network error during JSON-RPC call", null, e);
        }
    }

    private JsonRpcResponse parseResponse(final String method, final String body) throws RpcException {
        try {
            return mapper.readValue(body, JsonRpcResponse.class);
        } catch (JsonProcessingException e) {
            throw new RpcException(
                    -32700, "Unable to parse JSON-RPC response for method " + method, body, e);
        }
    }

    private String extractErrorData(final Object dataValue) {
        return switch (dataValue) {
            case null -> null;
            case String s when s.trim().startsWith("0x") -> s;
            case Map<?, ?> map -> extractFromIterable(map.values(), dataValue);
            case Iterable<?> iterable -> extractFromIterable(iterable, dataValue);
            case Object array when dataValue.getClass().isArray() -> extractFromArray(array, dataValue);
            default -> dataValue.toString();
        };
    }

    private String extractFromIterable(final Iterable<?> iterable, final Object fallback) {
        for (Object value : iterable) {
            final String nested = extractErrorData(value);
            if (nested != null) {
                return nested;
            }
        }
        return fallback.toString();
    }

    private String extractFromArray(final Object array, final Object fallback) {
        final int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            final String nested = extractErrorData(Array.get(array, i));
            if (nested != null) {
                return nested;
            }
        }
        return fallback.toString();
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
            final RpcConfig config =
                    new RpcConfig(url, chainId, connectTimeout, readTimeout, new LinkedHashMap<>(headers));
            return new HttpBraneProvider(config);
        }
    }
}
