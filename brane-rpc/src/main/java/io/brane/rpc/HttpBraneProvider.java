// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc;

import static io.brane.rpc.internal.RpcUtils.HTTP_SCHEMES;
import static io.brane.rpc.internal.RpcUtils.MAPPER;
import static io.brane.rpc.internal.RpcUtils.validateUrl;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.brane.core.DebugLogger;
import io.brane.core.LogFormatter;
import io.brane.core.RevertDecoder;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.rpc.internal.RpcUtils;

public final class HttpBraneProvider implements BraneProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpBraneProvider.class);

    private final RpcConfig config;
    private final java.net.http.HttpClient httpClient;
    private final java.util.concurrent.ExecutorService executor;
    private final AtomicLong ids = new AtomicLong(1L);
    private volatile BraneMetrics metrics = BraneMetrics.noop();

    private HttpBraneProvider(final RpcConfig config) {
        this.config = config;
        this.executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        this.httpClient = java.net.http.HttpClient.newBuilder()
                .executor(executor)
                .connectTimeout(config.connectTimeout())
                .build();
    }

    /**
     * Default timeout in seconds for graceful shutdown of the executor.
     * After this timeout, any remaining tasks will be forcibly cancelled.
     */
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    /**
     * Closes this provider and releases associated resources.
     *
     * <p>This method performs a graceful shutdown of the internal HTTP client and
     * executor service:
     * <ol>
     *   <li>Initiates orderly shutdown (no new tasks accepted)</li>
     *   <li>Waits up to {@value #SHUTDOWN_TIMEOUT_SECONDS} seconds for in-flight requests</li>
     *   <li>Forces termination if timeout expires</li>
     * </ol>
     *
     * <p><strong>Blocking behavior:</strong> This method may block for up to
     * {@value #SHUTDOWN_TIMEOUT_SECONDS} seconds while waiting for in-flight
     * requests to complete. After the timeout, any remaining requests are cancelled.
     *
     * <p>After calling this method, the provider should not be used for further requests.
     */
    @Override
    public void close() {
        httpClient.close();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sets a custom metrics collector for observability.
     *
     * <p>Use this to integrate with monitoring systems like Micrometer, Prometheus,
     * or custom metrics collectors. When set, the provider will report HTTP errors
     * via {@link BraneMetrics#onRequestFailed(String, Throwable)}.
     *
     * @param metrics the metrics collector (must not be null)
     * @throws NullPointerException if metrics is null
     * @since 0.5.0
     */
    public void setMetrics(BraneMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * Creates a new builder for configuring an {@link HttpBraneProvider}.
     *
     * <p>This is the primary entry point for creating HTTP-based RPC providers.
     * The builder allows configuration of timeouts, custom headers, and chain ID.
     *
     * <p><strong>Example usage:</strong>
     * <pre>{@code
     * HttpBraneProvider provider = HttpBraneProvider.builder("https://eth-mainnet.g.alchemy.com/v2/key")
     *     .connectTimeout(Duration.ofSeconds(5))
     *     .readTimeout(Duration.ofSeconds(30))
     *     .header("Authorization", "Bearer token")
     *     .build();
     * }</pre>
     *
     * @param url the HTTP or HTTPS URL of the Ethereum JSON-RPC endpoint
     * @return a new builder instance
     * @throws NullPointerException if url is null
     * @throws IllegalArgumentException if url is not a valid HTTP/HTTPS URL
     * @see Builder#build()
     */
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
            log.warn("HTTP error for RPC method '{}': status={}, requestId={}, latencyMicros={}",
                    method, response.statusCode(), requestId, durationMicros);
            final var ex = new RpcException(
                    -32001,
                    "HTTP error for method " + method + ": " + response.statusCode(),
                    response.body(),
                    requestId,
                    null);
            metrics.onRequestFailed(method, ex);
            throw ex;
        }

        final String responseBody = response.body();
        final JsonRpcResponse rpcResponse = parseResponse(method, responseBody, requestId);
        if (rpcResponse.hasError()) {
            final JsonRpcError err = rpcResponse.error();
            DebugLogger.logRpc(
                    LogFormatter.formatRpcError(method, err.code(), err.message(), durationMicros));
            final String data = RpcUtils.extractErrorData(err.data());
            // Check if this is a revert error with data
            if (data != null && data.startsWith("0x") && data.length() > 10) {
                final RevertDecoder.Decoded decoded = RevertDecoder.decode(data);
                throw new RevertException(decoded.kind(), decoded.reason(), decoded.rawDataHex(), null);
            }
            throw new RpcException(err.code(), err.message(), data, requestId);
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
            // Validate URL format immediately for better error locality
            validateUrl(url, HTTP_SCHEMES);
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
