// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.rpc;

import static sh.brane.rpc.internal.RpcUtils.WS_SCHEMES;
import static sh.brane.rpc.internal.RpcUtils.validateUrl;

import java.time.Duration;
import java.util.Objects;

import io.netty.channel.EventLoopGroup;
import org.jspecify.annotations.Nullable;

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
 * <li>{@link WaitStrategyType#BUSY_SPIN} - Lowest latency, 100% CPU on one core.
 * Requires CPU core pinning to be effective.</li>
 * <li>{@link WaitStrategyType#YIELDING} - Ultra-low latency, higher CPU usage.
 * Best for HFT/MEV.</li>
 * <li>{@link WaitStrategyType#LITE_BLOCKING} - Balanced latency/CPU.
 * Spins briefly before blocking.</li>
 * <li>{@link WaitStrategyType#BLOCKING} - Lower CPU usage, slightly higher
 * latency. Best for enterprise/batch.</li>
 * </ul>
 *
 * <p>
 * <strong>Transport Type Selection:</strong>
 * <p>
 * The {@code transportType} option controls the underlying Netty channel
 * implementation. Native transports (Epoll on Linux, KQueue on macOS/BSD)
 * provide 10-20% higher throughput compared to Java NIO by reducing system
 * call overhead and leveraging platform-specific optimizations.
 *
 * <pre>{@code
 * // Explicitly use native transport for maximum performance
 * WebSocketConfig config = WebSocketConfig.builder("wss://eth.example.com")
 *         .transportType(TransportType.AUTO)  // Auto-selects best for platform
 *         .build();
 *
 * // Force NIO for cross-platform consistency (e.g., testing)
 * WebSocketConfig config = WebSocketConfig.builder("wss://eth.example.com")
 *         .transportType(TransportType.NIO)
 *         .build();
 * }</pre>
 *
 * <ul>
 * <li>{@link TransportType#AUTO} - Automatically selects the best available
 * transport for the current platform. Default and recommended.</li>
 * <li>{@link TransportType#NIO} - Java NIO transport. Works everywhere but
 * may have slightly higher latency.</li>
 * <li>{@link TransportType#EPOLL} - Linux-only native transport.</li>
 * <li>{@link TransportType#KQUEUE} - macOS/BSD-only native transport.</li>
 * </ul>
 *
 * @param url                   the WebSocket URL (ws:// or wss://)
 * @param maxPendingRequests    maximum concurrent in-flight requests (must be
 *                              power of 2)
 * @param ringBufferSize        Disruptor ring buffer size (must be power of 2)
 * @param waitStrategy          Disruptor wait strategy type
 * @param transportType         Netty transport type (AUTO, NIO, EPOLL, KQUEUE);
 *                              native transports offer 10-20% throughput gains
 * @param defaultRequestTimeout default timeout for requests (null = no timeout)
 * @param connectTimeout        connection establishment timeout
 * @param ioThreads               number of Netty I/O threads (typically 1 for
 *                                minimal context switching)
 * @param writeBufferLowWaterMark low water mark for Netty write buffer (bytes).
 *                                When buffer size drops below this, channel
 *                                becomes writable again. Default: 8KB.
 * @param writeBufferHighWaterMark high water mark for Netty write buffer (bytes).
 *                                 When buffer size exceeds this, channel becomes
 *                                 not writable. Default: 32KB.
 * @param maxFrameSize            maximum WebSocket frame size in bytes. Default: 64KB.
 *                                Maximum: 16MB. Controls the largest single frame
 *                                the client will accept.
 * @since 0.2.0
 */
public record WebSocketConfig(
        String url,
        int maxPendingRequests,
        int ringBufferSize,
        WaitStrategyType waitStrategy,
        TransportType transportType,
        Duration defaultRequestTimeout,
        Duration connectTimeout,
        int ioThreads,
        @Nullable EventLoopGroup eventLoopGroup,
        int writeBufferLowWaterMark,
        int writeBufferHighWaterMark,
        int maxFrameSize) {

    /**
     * Disruptor wait strategy types.
     *
     * <p><b>When to use each strategy:</b>
     * <ul>
     * <li>{@link #BUSY_SPIN} - Ultra-low latency HFT/MEV with dedicated CPU cores</li>
     * <li>{@link #YIELDING} - Low latency HFT/MEV on shared infrastructure</li>
     * <li>{@link #LITE_BLOCKING} - Lower CPU usage while maintaining reasonable latency</li>
     * <li>{@link #BLOCKING} - Enterprise/batch workloads where CPU efficiency matters most</li>
     * </ul>
     */
    public enum WaitStrategyType {
        /**
         * Lowest possible latency, consumes 100% of one CPU core.
         * Uses a tight busy-spin loop with no yielding or parking.
         *
         * <p><b>Use case:</b> Ultra-low latency HFT (high-frequency trading) or MEV
         * (maximal extractable value) applications where sub-microsecond latency is
         * critical and dedicated CPU cores are available.
         *
         * <p><b>Warning:</b> This strategy requires dedicated CPU core pinning
         * (e.g., via {@code taskset} on Linux or isolcpus kernel parameter) to be
         * effective. Without core pinning, OS scheduler interference will actually
         * increase latency compared to {@link #YIELDING}.
         *
         * <p>Only use when sub-microsecond latency is required and you have
         * isolated CPU cores available. If you don't have dedicated cores, use
         * {@link #YIELDING} instead.
         */
        BUSY_SPIN,

        /**
         * Low latency with high CPU usage. Uses busy-spin with occasional yields.
         *
         * <p><b>Use case:</b> Latency-critical applications like HFT or MEV where you
         * need low latency but don't have dedicated/pinned CPU cores. This is the
         * default strategy and works well for most latency-sensitive workloads.
         *
         * <p>CPU usage will be high (near 100% on the consumer thread) but the OS
         * scheduler can still interrupt when needed.
         */
        YIELDING,

        /**
         * Balanced latency and CPU usage. Uses a spin-then-park approach.
         * Spins briefly before falling back to lock-based blocking.
         *
         * <p><b>Use case:</b> Applications that need reasonable latency but cannot
         * afford the high CPU usage of {@link #YIELDING}. Good for:
         * <ul>
         * <li>Cloud deployments where CPU is metered</li>
         * <li>Multi-tenant environments</li>
         * <li>Applications with moderate latency requirements (milliseconds, not microseconds)</li>
         * </ul>
         *
         * <p>Provides a good middle ground: spins briefly to catch quick responses,
         * then parks the thread to save CPU if the response takes longer.
         */
        LITE_BLOCKING,

        /**
         * Lower CPU usage with slightly higher latency. Uses lock-based blocking.
         *
         * <p><b>Use case:</b> Enterprise or batch processing workloads where latency
         * is less critical than CPU efficiency. Good for:
         * <ul>
         * <li>Background indexing or data synchronization</li>
         * <li>Batch transaction processing</li>
         * <li>Development and testing environments</li>
         * </ul>
         *
         * <p>The thread will park immediately when waiting, minimizing CPU usage
         * at the cost of slightly higher wake-up latency.
         */
        BLOCKING
    }

    /**
     * Netty transport types for the underlying channel implementation.
     */
    public enum TransportType {
        /**
         * Automatically select the best available transport for the current platform.
         * Uses Epoll on Linux, KQueue on macOS/BSD, NIO otherwise.
         */
        AUTO,

        /**
         * Java NIO transport. Works on all platforms but may have higher latency
         * than native transports.
         */
        NIO,

        /**
         * Linux Epoll transport. Provides lower latency and higher throughput on Linux.
         * Requires the {@code netty-transport-native-epoll} dependency.
         */
        EPOLL,

        /**
         * macOS/BSD KQueue transport. Provides lower latency on macOS and BSD systems.
         * Requires the {@code netty-transport-native-kqueue} dependency.
         */
        KQUEUE
    }

    // Defaults
    private static final int DEFAULT_MAX_PENDING = 65536;
    private static final int DEFAULT_RING_SIZE = 4096;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int DEFAULT_IO_THREADS = 1;
    private static final int DEFAULT_WRITE_BUFFER_LOW = 8 * 1024;   // 8KB
    private static final int DEFAULT_WRITE_BUFFER_HIGH = 32 * 1024; // 32KB
    private static final int DEFAULT_MAX_FRAME_SIZE = 64 * 1024;    // 64KB
    private static final int MAX_FRAME_SIZE_LIMIT = 16 * 1024 * 1024; // 16MB

    /**
     * Maximum power of 2 that fits in a signed 32-bit int: 2^30 = 1,073,741,824.
     */
    private static final int MAX_POWER_OF_2 = 1 << 30;

    /**
     * Returns the next power of 2 greater than or equal to the given value.
     *
     * @throws IllegalArgumentException if value > 2^30 (would overflow)
     */
    private static int nextPowerOf2(int value) {
        if (value <= 0) return 1;
        if (value > MAX_POWER_OF_2) {
            throw new IllegalArgumentException(
                    "value " + value + " exceeds maximum power of 2 (" + MAX_POWER_OF_2 + ")");
        }
        int highestBit = Integer.highestOneBit(value);
        return (value == highestBit) ? value : highestBit << 1;
    }

    /**
     * Compact constructor with validation and defaults.
     */
    public WebSocketConfig {
        validateUrl(url, WS_SCHEMES);

        // Apply defaults for zero/null values
        if (maxPendingRequests <= 0)
            maxPendingRequests = DEFAULT_MAX_PENDING;
        if (ringBufferSize <= 0)
            ringBufferSize = DEFAULT_RING_SIZE;
        if (waitStrategy == null)
            waitStrategy = WaitStrategyType.YIELDING;
        if (transportType == null)
            transportType = TransportType.AUTO;
        if (defaultRequestTimeout == null)
            defaultRequestTimeout = DEFAULT_TIMEOUT;
        if (connectTimeout == null)
            connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        if (ioThreads <= 0)
            ioThreads = DEFAULT_IO_THREADS;
        if (writeBufferLowWaterMark <= 0)
            writeBufferLowWaterMark = DEFAULT_WRITE_BUFFER_LOW;
        if (writeBufferHighWaterMark <= 0)
            writeBufferHighWaterMark = DEFAULT_WRITE_BUFFER_HIGH;
        if (maxFrameSize <= 0)
            maxFrameSize = DEFAULT_MAX_FRAME_SIZE;

        // Validate maxFrameSize
        if (maxFrameSize > MAX_FRAME_SIZE_LIMIT) {
            throw new IllegalArgumentException(
                    "maxFrameSize (" + maxFrameSize + ") exceeds maximum allowed (" + MAX_FRAME_SIZE_LIMIT + " bytes / 16MB)");
        }

        // Validate write buffer water marks
        if (writeBufferLowWaterMark > writeBufferHighWaterMark) {
            throw new IllegalArgumentException(
                    "writeBufferLowWaterMark (" + writeBufferLowWaterMark +
                            ") must be <= writeBufferHighWaterMark (" + writeBufferHighWaterMark + ")");
        }

        // Validate power of 2 constraints
        if ((maxPendingRequests & (maxPendingRequests - 1)) != 0) {
            throw new IllegalArgumentException(
                    "maxPendingRequests must be a power of 2, got: " + maxPendingRequests
                            + ", try: " + nextPowerOf2(maxPendingRequests));
        }
        if ((ringBufferSize & (ringBufferSize - 1)) != 0) {
            throw new IllegalArgumentException(
                    "ringBufferSize must be a power of 2, got: " + ringBufferSize
                            + ", try: " + nextPowerOf2(ringBufferSize));
        }
    }

    /**
     * Creates a configuration with all defaults for the given URL.
     *
     * @param url the WebSocket URL
     * @return a new WebSocketConfig with default settings
     */
    public static WebSocketConfig withDefaults(String url) {
        return new WebSocketConfig(url, 0, 0, null, null, null, null, 0, null, 0, 0, 0);
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
        private TransportType transportType = null;
        private Duration defaultRequestTimeout = null;
        private Duration connectTimeout = null;
        private int ioThreads = 0;
        private EventLoopGroup eventLoopGroup = null;
        private int writeBufferLowWaterMark = 0;
        private int writeBufferHighWaterMark = 0;
        private int maxFrameSize = 0;

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
         * Sets the Netty transport type.
         * Default: AUTO (selects best available for the platform).
         */
        public Builder transportType(TransportType transportType) {
            this.transportType = transportType;
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
         * Sets the low water mark for the Netty write buffer.
         * When buffer size drops below this, the channel becomes writable again.
         * Default: 8KB.
         *
         * @param bytes the low water mark in bytes
         */
        public Builder writeBufferLowWaterMark(int bytes) {
            this.writeBufferLowWaterMark = bytes;
            return this;
        }

        /**
         * Sets the high water mark for the Netty write buffer.
         * When buffer size exceeds this, the channel becomes not writable.
         * Default: 32KB.
         *
         * @param bytes the high water mark in bytes
         */
        public Builder writeBufferHighWaterMark(int bytes) {
            this.writeBufferHighWaterMark = bytes;
            return this;
        }

        /**
         * Sets both low and high water marks for the Netty write buffer.
         *
         * @param lowBytes  the low water mark in bytes
         * @param highBytes the high water mark in bytes
         */
        public Builder writeBufferWaterMark(int lowBytes, int highBytes) {
            this.writeBufferLowWaterMark = lowBytes;
            this.writeBufferHighWaterMark = highBytes;
            return this;
        }

        /**
         * Sets the maximum WebSocket frame size.
         * Default: 64KB. Maximum: 16MB.
         *
         * @param bytes the maximum frame size in bytes
         */
        public Builder maxFrameSize(int bytes) {
            this.maxFrameSize = bytes;
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
                    transportType,
                    defaultRequestTimeout,
                    connectTimeout,
                    ioThreads,
                    eventLoopGroup,
                    writeBufferLowWaterMark,
                    writeBufferHighWaterMark,
                    maxFrameSize);
        }
    }
}
