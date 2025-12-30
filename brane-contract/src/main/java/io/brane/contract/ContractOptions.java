package io.brane.contract;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration options for contract interactions.
 *
 * <p>Allows customization of gas limits, timeouts, and polling intervals
 * for contract calls and transactions.
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * var options = ContractOptions.builder()
 *     .gasLimit(500_000L)
 *     .timeout(Duration.ofSeconds(30))
 *     .pollInterval(Duration.ofMillis(100))
 *     .build();
 * }</pre>
 */
public final class ContractOptions {

    /** Default gas limit for transactions (300,000 gas). */
    public static final long DEFAULT_GAS_LIMIT = 300_000L;

    /** Default timeout for waiting on transaction receipts (10 seconds). */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    /** Default polling interval when waiting for receipts (500ms). */
    public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(500);

    private static final ContractOptions DEFAULTS = new ContractOptions(
            DEFAULT_GAS_LIMIT, DEFAULT_TIMEOUT, DEFAULT_POLL_INTERVAL);

    private final long gasLimit;
    private final Duration timeout;
    private final Duration pollInterval;

    private ContractOptions(final long gasLimit, final Duration timeout, final Duration pollInterval) {
        this.gasLimit = gasLimit;
        this.timeout = timeout;
        this.pollInterval = pollInterval;
    }

    /**
     * Returns a ContractOptions instance with all default values.
     *
     * @return default options
     */
    public static ContractOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Creates a new builder for ContractOptions.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the gas limit for transactions.
     *
     * @return gas limit
     */
    public long gasLimit() {
        return gasLimit;
    }

    /**
     * Returns the timeout duration for waiting on transaction receipts.
     *
     * @return timeout duration
     */
    public Duration timeout() {
        return timeout;
    }

    /**
     * Returns the timeout in milliseconds.
     *
     * @return timeout in milliseconds
     */
    public long timeoutMillis() {
        return timeout.toMillis();
    }

    /**
     * Returns the polling interval for checking transaction status.
     *
     * @return poll interval duration
     */
    public Duration pollInterval() {
        return pollInterval;
    }

    /**
     * Returns the polling interval in milliseconds.
     *
     * @return poll interval in milliseconds
     */
    public long pollIntervalMillis() {
        return pollInterval.toMillis();
    }

    /**
     * Builder for creating ContractOptions instances.
     */
    public static final class Builder {
        private long gasLimit = DEFAULT_GAS_LIMIT;
        private Duration timeout = DEFAULT_TIMEOUT;
        private Duration pollInterval = DEFAULT_POLL_INTERVAL;

        private Builder() {
        }

        /**
         * Sets the gas limit for transactions.
         *
         * @param gasLimit the gas limit (must be positive)
         * @return this builder
         * @throws IllegalArgumentException if gasLimit is not positive
         */
        public Builder gasLimit(final long gasLimit) {
            if (gasLimit <= 0) {
                throw new IllegalArgumentException("gasLimit must be positive");
            }
            this.gasLimit = gasLimit;
            return this;
        }

        /**
         * Sets the timeout for waiting on transaction receipts.
         *
         * @param timeout the timeout duration (must not be null or negative)
         * @return this builder
         * @throws NullPointerException if timeout is null
         * @throws IllegalArgumentException if timeout is negative
         */
        public Builder timeout(final Duration timeout) {
            Objects.requireNonNull(timeout, "timeout must not be null");
            if (timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must not be negative");
            }
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the polling interval for checking transaction status.
         *
         * @param pollInterval the poll interval (must not be null or negative)
         * @return this builder
         * @throws NullPointerException if pollInterval is null
         * @throws IllegalArgumentException if pollInterval is negative or zero
         */
        public Builder pollInterval(final Duration pollInterval) {
            Objects.requireNonNull(pollInterval, "pollInterval must not be null");
            if (pollInterval.isNegative() || pollInterval.isZero()) {
                throw new IllegalArgumentException("pollInterval must be positive");
            }
            this.pollInterval = pollInterval;
            return this;
        }

        /**
         * Builds the ContractOptions instance.
         *
         * @return a new ContractOptions instance
         */
        public ContractOptions build() {
            return new ContractOptions(gasLimit, timeout, pollInterval);
        }
    }
}
