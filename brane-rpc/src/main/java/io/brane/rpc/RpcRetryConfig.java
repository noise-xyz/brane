// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc;


/**
 * Configuration for RPC retry behavior with exponential backoff.
 *
 * <p>This record holds the configurable constants for retry backoff timing:
 * <ul>
 *   <li>{@code backoffBaseMs} - Base delay in milliseconds for first retry (default: 200ms)</li>
 *   <li>{@code backoffMaxMs} - Maximum delay cap in milliseconds (default: 5000ms)</li>
 *   <li>{@code jitterMin} - Minimum jitter percentage (default: 0.10 = 10%)</li>
 *   <li>{@code jitterMax} - Maximum jitter percentage (default: 0.25 = 25%)</li>
 * </ul>
 *
 * <p><strong>Backoff Formula:</strong>
 * <pre>
 *   delay = min(base * 2^(attempt-1), max)
 *   finalDelay = delay + delay * random(jitterMin, jitterMax)
 * </pre>
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * // Default configuration
 * RpcRetryConfig config = RpcRetryConfig.defaults();
 *
 * // Custom aggressive retry (shorter delays)
 * RpcRetryConfig aggressive = RpcRetryConfig.builder()
 *     .backoffBaseMs(100)
 *     .backoffMaxMs(2000)
 *     .build();
 *
 * // Custom conservative retry (longer delays)
 * RpcRetryConfig conservative = RpcRetryConfig.builder()
 *     .backoffBaseMs(500)
 *     .backoffMaxMs(30_000)
 *     .jitterMin(0.05)
 *     .jitterMax(0.15)
 *     .build();
 * }</pre>
 *
 * @param backoffBaseMs base delay in milliseconds (must be &gt; 0)
 * @param backoffMaxMs  maximum delay cap in milliseconds (must be &gt;= backoffBaseMs)
 * @param jitterMin     minimum jitter percentage (must be &gt;= 0 and &lt; jitterMax)
 * @param jitterMax     maximum jitter percentage (must be &gt; jitterMin)
 * @see RpcRetry
 * @since 1.0.0
 */
public record RpcRetryConfig(
        long backoffBaseMs,
        long backoffMaxMs,
        double jitterMin,
        double jitterMax) {

    /** Default base delay: 200ms. */
    public static final long DEFAULT_BACKOFF_BASE_MS = 200;

    /** Default maximum delay: 5000ms. */
    public static final long DEFAULT_BACKOFF_MAX_MS = 5000;

    /** Default minimum jitter: 10%. */
    public static final double DEFAULT_JITTER_MIN = 0.10;

    /** Default maximum jitter: 25%. */
    public static final double DEFAULT_JITTER_MAX = 0.25;

    /**
     * Canonical constructor with validation.
     *
     * @param backoffBaseMs base delay in milliseconds
     * @param backoffMaxMs  maximum delay cap in milliseconds
     * @param jitterMin     minimum jitter percentage
     * @param jitterMax     maximum jitter percentage
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public RpcRetryConfig {
        if (backoffBaseMs <= 0) {
            throw new IllegalArgumentException("backoffBaseMs must be > 0, got: " + backoffBaseMs);
        }
        if (backoffMaxMs < backoffBaseMs) {
            throw new IllegalArgumentException(
                    "backoffMaxMs must be >= backoffBaseMs, got: " + backoffMaxMs + " < " + backoffBaseMs);
        }
        if (jitterMin < 0) {
            throw new IllegalArgumentException("jitterMin must be >= 0, got: " + jitterMin);
        }
        if (jitterMax <= jitterMin) {
            throw new IllegalArgumentException(
                    "jitterMax must be > jitterMin, got: " + jitterMax + " <= " + jitterMin);
        }
    }

    /**
     * Returns the default configuration.
     *
     * @return default config with 200ms base, 5000ms max, 10-25% jitter
     */
    public static RpcRetryConfig defaults() {
        return new RpcRetryConfig(
                DEFAULT_BACKOFF_BASE_MS,
                DEFAULT_BACKOFF_MAX_MS,
                DEFAULT_JITTER_MIN,
                DEFAULT_JITTER_MAX);
    }

    /**
     * Creates a new builder for custom configuration.
     *
     * @return a new builder initialized with default values
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating custom {@link RpcRetryConfig} instances.
     *
     * <p>All values are initialized to defaults and can be selectively overridden.
     */
    public static final class Builder {
        private long backoffBaseMs = DEFAULT_BACKOFF_BASE_MS;
        private long backoffMaxMs = DEFAULT_BACKOFF_MAX_MS;
        private double jitterMin = DEFAULT_JITTER_MIN;
        private double jitterMax = DEFAULT_JITTER_MAX;

        private Builder() {}

        /**
         * Sets the base backoff delay.
         *
         * @param backoffBaseMs base delay in milliseconds (must be &gt; 0)
         * @return this builder
         */
        public Builder backoffBaseMs(long backoffBaseMs) {
            this.backoffBaseMs = backoffBaseMs;
            return this;
        }

        /**
         * Sets the maximum backoff delay cap.
         *
         * @param backoffMaxMs maximum delay in milliseconds
         * @return this builder
         */
        public Builder backoffMaxMs(long backoffMaxMs) {
            this.backoffMaxMs = backoffMaxMs;
            return this;
        }

        /**
         * Sets the minimum jitter percentage.
         *
         * @param jitterMin minimum jitter (e.g., 0.10 for 10%)
         * @return this builder
         */
        public Builder jitterMin(double jitterMin) {
            this.jitterMin = jitterMin;
            return this;
        }

        /**
         * Sets the maximum jitter percentage.
         *
         * @param jitterMax maximum jitter (e.g., 0.25 for 25%)
         * @return this builder
         */
        public Builder jitterMax(double jitterMax) {
            this.jitterMax = jitterMax;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return new immutable {@link RpcRetryConfig}
         * @throws IllegalArgumentException if any parameter is invalid
         */
        public RpcRetryConfig build() {
            return new RpcRetryConfig(backoffBaseMs, backoffMaxMs, jitterMin, jitterMax);
        }
    }
}
