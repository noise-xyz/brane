package io.brane.rpc;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for HTTP-based RPC providers.
 *
 * @param url            the RPC endpoint URL (must be a valid HTTP/HTTPS URL)
 * @param chainId        optional expected chain ID for validation
 * @param connectTimeout connection timeout (must be positive, default: 10s)
 * @param readTimeout    read timeout (must be positive, default: 30s)
 * @param headers        additional HTTP headers
 * @since 0.2.0
 */
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

        // Validate URL format
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException(
                        "url must use http or https scheme, got: " + (scheme == null ? "null" : scheme));
            }
            if (uri.getHost() == null || uri.getHost().isEmpty()) {
                throw new IllegalArgumentException("url must have a valid host");
            }
        } catch (IllegalArgumentException e) {
            if (e.getMessage().startsWith("url must")) {
                throw e;
            }
            throw new IllegalArgumentException("url is not a valid URI: " + e.getMessage(), e);
        }

        // Apply defaults
        connectTimeout = connectTimeout == null ? DEFAULT_CONNECT : connectTimeout;
        readTimeout = readTimeout == null ? DEFAULT_READ : readTimeout;
        headers = headers == null ? Map.of() : Map.copyOf(headers);

        // Validate timeouts are positive
        if (connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("connectTimeout must be positive, got: " + connectTimeout);
        }
        if (readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("readTimeout must be positive, got: " + readTimeout);
        }
    }

    public static RpcConfig withDefaults(final String url) {
        return new RpcConfig(url, null, DEFAULT_CONNECT, DEFAULT_READ, Map.of());
    }
}
