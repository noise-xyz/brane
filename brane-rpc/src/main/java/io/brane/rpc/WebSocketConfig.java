package io.brane.rpc;

import io.netty.channel.EventLoopGroup;
import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for {@link WebSocketProvider}.
 *
 * <p>
 * This record provides fine-grained control over the WebSocket provider's
 * behavior, including Disruptor settings, timeout configuration, and threading
 * options.
 *
 * <p>
 * <strong>Usage:</strong>
 * 
 * <pre>{@code
 * WebSocketConfig config = WebSocketConfig.builder("wss://eth.example.com")
 *         .maxPendingRequests(32768)
 *         .ringBufferSize(8192)
 *         .waitStrategy(WaitStrategyType.BLOCKING)
 *         .defaultRequestTimeout(Duration.ofSeconds(30))
 *         .build();
 *
 * WebSocketProvider provider = WebSocketProvider.create(config);
 * }</pre>
 *
 * <p>
 * <strong>Wait Strategy Guidelines:</strong>
 * <ul>
 * <li>{@link WaitStrategyType#YIELDING} - Ultra-low latency, higher CPU usage.
 * Best for HFT/MEV.</li>
 * <li>{@link WaitStrategyType#BLOCKING} - Lower CPU usage, slightly higher
 * latency. Best for enterprise/batch.</li>
 * </ul>
 *
 * @param url                   the WebSocket URL (ws:// or wss://)
 * @param maxPendingRequests    maximum concurrent in-flight requests (must be
 *                              power of 2)
 * @param ringBufferSize        Disruptor ring buffer size (must be power of 2)
 * @param waitStrategy          Disruptor wait strategy type
 * @param defaultRequestTimeout default timeout for requests (null = no timeout)
 * @param connectTimeout        connection establishment timeout
 * @param ioThreads             number of Netty I/O threads (typically 1 for
 *                              minimal context switching)
 * @since 0.2.0
 */
public record WebSocketConfig(
        String url,
        int maxPendingRequests,
        int ringBufferSize,
        WaitStrategyType waitStrategy,
        Duration defaultRequestTimeout,
        Duration connectTimeout,
        int ioThreads,
        EventLoopGroup eventLoopGroup) {

    /**
     * Disruptor wait strategy types.
     */
    public enum WaitStrategyType {
        /**
         * Low latency, high CPU usage. Uses busy-spin/yield.
         * Best for latency-critical applications like HFT or MEV.
         */
        YIELDING,

        /**
         * Lower CPU usage, slightly higher latency. Uses locks.
         * Best for enterprise or batch processing workloads.
         */
        BLOCKING
    }

    // Defaults
    private static final int DEFAULT_MAX_PENDING = 65536;
    private static final int DEFAULT_RING_SIZE = 4096;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int DEFAULT_IO_THREADS = 1;

    /**
     * Compact constructor with validation and defaults.
     */
    public WebSocketConfig {
        Objects.requireNonNull(url, "url");

        // Apply defaults for zero/null values
        if (maxPendingRequests <= 0)
            maxPendingRequests = DEFAULT_MAX_PENDING;
        if (ringBufferSize <= 0)
            ringBufferSize = DEFAULT_RING_SIZE;
        if (waitStrategy == null)
            waitStrategy = WaitStrategyType.YIELDING;
        if (defaultRequestTimeout == null)
            defaultRequestTimeout = DEFAULT_TIMEOUT;
        if (connectTimeout == null)
            connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        if (ioThreads <= 0)
            ioThreads = DEFAULT_IO_THREADS;

        // Validate power of 2 constraints
        if ((maxPendingRequests & (maxPendingRequests - 1)) != 0) {
            throw new IllegalArgumentException(
                    "maxPendingRequests must be a power of 2, got: " + maxPendingRequests);
        }
        if ((ringBufferSize & (ringBufferSize - 1)) != 0) {
            throw new IllegalArgumentException(
                    "ringBufferSize must be a power of 2, got: " + ringBufferSize);
        }
    }

    /**
     * Creates a configuration with all defaults for the given URL.
     *
     * @param url the WebSocket URL
     * @return a new WebSocketConfig with default settings
     */
    public static WebSocketConfig withDefaults(String url) {
        return new WebSocketConfig(url, 0, 0, null, null, null, 0, null);
    }

    /**
     * Creates a builder for constructing a WebSocketConfig.
     *
     * @param url the WebSocket URL
     * @return a new builder
     */
    public static Builder builder(String url) {
        return new Builder(url);
    }

    /**
     * Builder for {@link WebSocketConfig}.
     */
    public static final class Builder {
        private final String url;
        private int maxPendingRequests = 0;
        private int ringBufferSize = 0;
        private WaitStrategyType waitStrategy = null;
        private Duration defaultRequestTimeout = null;
        private Duration connectTimeout = null;
        private int ioThreads = 0;
        private EventLoopGroup eventLoopGroup = null;

        private Builder(String url) {
            this.url = Objects.requireNonNull(url, "url");
        }

        /**
         * Sets the maximum number of pending requests.
         * Must be a power of 2. Default: 65536.
         */
        public Builder maxPendingRequests(int maxPendingRequests) {
            this.maxPendingRequests = maxPendingRequests;
            return this;
        }

        /**
         * Sets the Disruptor ring buffer size.
         * Must be a power of 2. Default: 4096.
         */
        public Builder ringBufferSize(int ringBufferSize) {
            this.ringBufferSize = ringBufferSize;
            return this;
        }

        /**
         * Sets the Disruptor wait strategy.
         * Default: YIELDING (low latency, high CPU).
         */
        public Builder waitStrategy(WaitStrategyType waitStrategy) {
            this.waitStrategy = waitStrategy;
            return this;
        }

        /**
         * Sets the default request timeout.
         * Default: 60 seconds.
         */
        public Builder defaultRequestTimeout(Duration timeout) {
            this.defaultRequestTimeout = timeout;
            return this;
        }

        /**
         * Sets the connection timeout.
         * Default: 10 seconds.
         */
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        /**
         * Sets the number of Netty I/O threads.
         * Default: 1 (minimal context switching).
         * Ignored if eventLoopGroup is provided.
         */
        public Builder ioThreads(int ioThreads) {
            this.ioThreads = ioThreads;
            return this;
        }

        /**
         * Sets a custom Netty EventLoopGroup.
         * For advanced users integrating with existing Netty infrastructure.
         * When set, ioThreads is ignored.
         * The caller is responsible for shutting down this group.
         */
        public Builder eventLoopGroup(EventLoopGroup group) {
            this.eventLoopGroup = group;
            return this;
        }

        /**
         * Builds the WebSocketConfig.
         *
         * @return a new WebSocketConfig
         */
        public WebSocketConfig build() {
            return new WebSocketConfig(
                    url,
                    maxPendingRequests,
                    ringBufferSize,
                    waitStrategy,
                    defaultRequestTimeout,
                    connectTimeout,
                    ioThreads,
                    eventLoopGroup);
        }
    }
}
