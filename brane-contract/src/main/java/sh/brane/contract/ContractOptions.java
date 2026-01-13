// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.contract;

import java.time.Duration;
import java.util.Objects;

import sh.brane.core.types.Wei;

/**
 * Configuration options for contract interactions.
 *
 * <p>Allows customization of gas limits, timeouts, polling intervals,
 * and transaction types for contract calls and transactions.
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * var options = ContractOptions.builder()
 *     .gasLimit(500_000L)
 *     .timeout(Duration.ofSeconds(30))
 *     .pollInterval(Duration.ofMillis(100))
 *     .transactionType(TransactionType.EIP1559)
 *     .maxPriorityFee(Wei.gwei(2))
 *     .build();
 * }</pre>
 */
public final class ContractOptions {

    /**
     * Transaction type for contract write operations.
     */
    public enum TransactionType {
        /** EIP-1559 dynamic fee transaction (recommended for post-London chains). */
        EIP1559,
        /** Legacy transaction with single gas price. */
        LEGACY
    }

    /** Default gas limit for transactions (300,000 gas). */
    public static final long DEFAULT_GAS_LIMIT = 300_000L;

    /** Default timeout for waiting on transaction receipts (10 seconds). */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    /** Default polling interval when waiting for receipts (500ms). */
    public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(500);

    /** Default transaction type (EIP-1559 for better gas optimization). */
    public static final TransactionType DEFAULT_TRANSACTION_TYPE = TransactionType.EIP1559;

    /** Default max priority fee for EIP-1559 transactions (2 gwei). */
    public static final Wei DEFAULT_MAX_PRIORITY_FEE = Wei.gwei(2);

    private static final ContractOptions DEFAULTS = new ContractOptions(
            DEFAULT_GAS_LIMIT,
            DEFAULT_TIMEOUT,
            DEFAULT_POLL_INTERVAL,
            DEFAULT_TRANSACTION_TYPE,
            DEFAULT_MAX_PRIORITY_FEE);

    private final long gasLimit;
    private final Duration timeout;
    private final Duration pollInterval;
    private final TransactionType transactionType;
    private final Wei maxPriorityFee;

    private ContractOptions(
            final long gasLimit,
            final Duration timeout,
            final Duration pollInterval,
            final TransactionType transactionType,
            final Wei maxPriorityFee) {
        this.gasLimit = gasLimit;
        this.timeout = timeout;
        this.pollInterval = pollInterval;
        this.transactionType = transactionType;
        this.maxPriorityFee = maxPriorityFee;
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
     * Returns the transaction type to use for write operations.
     *
     * @return transaction type (EIP1559 or LEGACY)
     */
    public TransactionType transactionType() {
        return transactionType;
    }

    /**
     * Returns the max priority fee (miner tip) for EIP-1559 transactions.
     *
     * @return max priority fee in Wei
     */
    public Wei maxPriorityFee() {
        return maxPriorityFee;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ContractOptions other)) {
            return false;
        }
        return gasLimit == other.gasLimit
                && Objects.equals(timeout, other.timeout)
                && Objects.equals(pollInterval, other.pollInterval)
                && transactionType == other.transactionType
                && Objects.equals(maxPriorityFee, other.maxPriorityFee);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gasLimit, timeout, pollInterval, transactionType, maxPriorityFee);
    }

    @Override
    public String toString() {
        return "ContractOptions{"
                + "gasLimit=" + gasLimit
                + ", timeout=" + timeout
                + ", pollInterval=" + pollInterval
                + ", transactionType=" + transactionType
                + ", maxPriorityFee=" + maxPriorityFee
                + '}';
    }

    /**
     * Builder for creating ContractOptions instances.
     */
    public static final class Builder {
        private long gasLimit = DEFAULT_GAS_LIMIT;
        private Duration timeout = DEFAULT_TIMEOUT;
        private Duration pollInterval = DEFAULT_POLL_INTERVAL;
        private TransactionType transactionType = DEFAULT_TRANSACTION_TYPE;
        private Wei maxPriorityFee = DEFAULT_MAX_PRIORITY_FEE;

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
         * @param timeout the timeout duration (must be positive)
         * @return this builder
         * @throws NullPointerException if timeout is null
         * @throws IllegalArgumentException if timeout is zero or negative
         */
        public Builder timeout(final Duration timeout) {
            Objects.requireNonNull(timeout, "timeout must not be null");
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("timeout must be positive");
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
         * Sets the transaction type for write operations.
         *
         * <p>EIP-1559 is recommended for post-London chains as it provides
         * better gas price optimization. Use LEGACY for chains that don't
         * support EIP-1559.
         *
         * @param transactionType the transaction type
         * @return this builder
         * @throws NullPointerException if transactionType is null
         */
        public Builder transactionType(final TransactionType transactionType) {
            Objects.requireNonNull(transactionType, "transactionType must not be null");
            this.transactionType = transactionType;
            return this;
        }

        /**
         * Sets the max priority fee (miner tip) for EIP-1559 transactions.
         *
         * <p>This is only used when transactionType is EIP1559. A reasonable
         * default is 1-2 gwei for mainnet, but may vary by network conditions.
         *
         * @param maxPriorityFee the max priority fee
         * @return this builder
         * @throws NullPointerException if maxPriorityFee is null
         */
        public Builder maxPriorityFee(final Wei maxPriorityFee) {
            Objects.requireNonNull(maxPriorityFee, "maxPriorityFee must not be null");
            this.maxPriorityFee = maxPriorityFee;
            return this;
        }

        /**
         * Builds the ContractOptions instance.
         *
         * @return a new ContractOptions instance
         */
        public ContractOptions build() {
            return new ContractOptions(gasLimit, timeout, pollInterval, transactionType, maxPriorityFee);
        }
    }
}
