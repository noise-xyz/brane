// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc;

import static io.brane.rpc.internal.RpcUtils.HTTP_SCHEMES;
import static io.brane.rpc.internal.RpcUtils.validateUrl;

import java.time.Duration;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Configuration for HTTP-based RPC providers.
 *
 * @param url            the RPC endpoint URL (must be a valid HTTP/HTTPS URL)
 * @param chainId        optional expected chain ID for validation (may be {@code null})
 * @param connectTimeout connection timeout (must be positive, default: 10s)
 * @param readTimeout    read timeout (must be positive, default: 30s)
 * @param headers        additional HTTP headers
 * @since 0.2.0
 */
public record RpcConfig(
        String url,
        @Nullable Long chainId,
        Duration connectTimeout,
        Duration readTimeout,
        Map<String, String> headers) {

    private static final Duration DEFAULT_CONNECT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_READ = Duration.ofSeconds(30);

    public RpcConfig {
        validateUrl(url, HTTP_SCHEMES);

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
