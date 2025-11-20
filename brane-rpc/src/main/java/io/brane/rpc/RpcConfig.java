package io.brane.rpc;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public record RpcConfig(
        String url,
        Long chainId,
        Duration connectTimeout,
        Duration readTimeout,
        Map<String, String> headers) {

    private static final Duration DEFAULT_CONNECT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_READ = Duration.ofSeconds(30);

    public RpcConfig {
        Objects.requireNonNull(url, "url");
        connectTimeout = connectTimeout == null ? DEFAULT_CONNECT : connectTimeout;
        readTimeout = readTimeout == null ? DEFAULT_READ : readTimeout;
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public static RpcConfig withDefaults(final String url) {
        return new RpcConfig(url, null, DEFAULT_CONNECT, DEFAULT_READ, Map.of());
    }
}
