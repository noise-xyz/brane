package io.brane.rpc;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.brane.core.chain.ChainProfile;
import io.brane.core.error.RpcException;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Wei;
import io.brane.rpc.internal.RpcUtils;

/**
 * Automatically fills missing gas parameters (limit, price, fees) for
 * transactions.
 *
 * <p>
 * This strategy implements intelligent defaults for gas-related transaction
 * fields
 * while preserving any user-specified values. It handles both legacy and
 * EIP-1559 transactions.
 *
 * <p>
 * <strong>⚠️ WARNING: EIP-1559 FALLBACK BEHAVIOR ⚠️</strong>
 * <p>
 * When an EIP-1559 transaction is requested ({@code isEip1559 = true}) but
 * the chain does not provide {@code baseFeePerGas} (pre-London fork chains,
 * or nodes that don't return base fee), this strategy may <strong>silently
 * fall back to legacy gas pricing</strong>. This means:
 * <ul>
 * <li>The returned transaction will have {@code isEip1559 = false}</li>
 * <li>Legacy {@code gasPrice} will be used instead of EIP-1559 fees</li>
 * <li>Check {@link GasFilledRequest#fellBackToLegacy()} to detect this</li>
 * </ul>
 *
 * <p>
 * Configure fallback behavior via {@link Eip1559FallbackBehavior}:
 * <ul>
 * <li>{@code THROW}: Fail fast when EIP-1559 unavailable</li>
 * <li>{@code FALLBACK_WARN}: Log warning and use legacy (default)</li>
 * <li>{@code FALLBACK_SILENT}: Use legacy without logging</li>
 * </ul>
 *
 * <p>
 * <strong>Gas Limit Estimation:</strong>
 * <ul>
 * <li>Calls {@code eth_estimateGas} to get base estimate</li>
 * <li>Applies a buffer multiplier (default 20%) to reduce out-of-gas
 * failures</li>
 * <li>Formula: {@code gasLimit = estimate × (120/100)}</li>
 * <li>Rationale: Node estimates can be slightly low; buffer provides safety
 * margin</li>
 * </ul>
 *
 * <p>
 * <strong>EIP-1559 Fee Calculation:</strong>
 * <ul>
 * <li>{@code maxPriorityFeePerGas}: Uses chain default (e.g., 2 Gwei for
 * Ethereum mainnet)</li>
 * <li>{@code maxFeePerGas}: {@code (baseFee × 2) + maxPriorityFee}</li>
 * <li>The 2x multiplier protects against base fee spikes over multiple
 * blocks</li>
 * <li>If baseFee doubles in next block, transaction still has headroom</li>
 * </ul>
 *
 * <p>
 * <strong>Legacy Transaction Gas Price:</strong>
 * <ul>
 * <li>Fetches current {@code eth_gasPrice} from node</li>
 * <li>Returns node's suggested gas price (usually network median)</li>
 * </ul>
 *
 * <p>
 * <strong>User Overrides:</strong> If user provides any gas field in the
 * request,
 * this strategy will NOT override it. Only fills {@code null} fields.
 *
 * @see TransactionRequest
 * @see WalletClient
 * @see GasFilledRequest
 */
final class SmartGasStrategy {

    private static final Logger log = LoggerFactory.getLogger(SmartGasStrategy.class);

    static final BigInteger DEFAULT_GAS_LIMIT_BUFFER_NUMERATOR = BigInteger.valueOf(120);
    static final BigInteger DEFAULT_GAS_LIMIT_BUFFER_DENOMINATOR = BigInteger.valueOf(100);
    static final BigInteger BASE_FEE_MULTIPLIER = BigInteger.valueOf(2);

    /**
     * Result of gas estimation containing the filled request and metadata about
     * any transaction type changes.
     *
     * <p>
     * <strong>Checking for EIP-1559 fallback:</strong>
     * <pre>{@code
     * GasFilledRequest result = gasStrategy.applyDefaults(request, from);
     * if (result.fellBackToLegacy()) {
     *     log.warn("EIP-1559 requested but fell back to legacy gas pricing");
     * }
     * TransactionRequest filled = result.request();
     * }</pre>
     *
     * @param request the transaction request with gas fields populated
     * @param requestedEip1559 true if the original request specified EIP-1559
     * @param actualEip1559 true if the returned request uses EIP-1559 (may differ from requested)
     * @since 0.1.0-alpha
     */
    record GasFilledRequest(
            TransactionRequest request,
            boolean requestedEip1559,
            boolean actualEip1559) {

        /**
         * Returns true if EIP-1559 was requested but the transaction fell back to legacy
         * gas pricing because baseFeePerGas was unavailable from the node.
         *
         * <p>
         * This can happen when:
         * <ul>
         * <li>The chain doesn't support EIP-1559 (pre-London fork)</li>
         * <li>The node returns null baseFeePerGas for the latest block</li>
         * </ul>
         *
         * @return true if fallback occurred, false otherwise
         */
        public boolean fellBackToLegacy() {
            return requestedEip1559 && !actualEip1559;
        }
    }

    /**
     * Configures behavior when EIP-1559 is requested but baseFeePerGas is unavailable.
     */
    public enum Eip1559FallbackBehavior {
        /**
         * Throw {@link RpcException} when EIP-1559 requested but unavailable.
         * Use this when you want explicit failures rather than silent degradation.
         */
        THROW,

        /**
         * Silently fall back to legacy gas pricing without logging.
         * Use this when fallback is expected (e.g., multi-chain apps).
         */
        FALLBACK_SILENT,

        /**
         * Log a warning and fall back to legacy gas pricing (default).
         * Use this for debugging unexpected fallbacks.
         */
        FALLBACK_WARN
    }

    private final PublicClient publicClient;
    private final BraneProvider provider;
    private final ChainProfile profile;
    private final BigInteger gasLimitBufferNumerator;
    private final BigInteger gasLimitBufferDenominator;
    private final Eip1559FallbackBehavior eip1559FallbackBehavior;

    SmartGasStrategy(
            final PublicClient publicClient, final BraneProvider provider, final ChainProfile profile) {
        this(
                publicClient,
                provider,
                profile,
                DEFAULT_GAS_LIMIT_BUFFER_NUMERATOR,
                DEFAULT_GAS_LIMIT_BUFFER_DENOMINATOR,
                Eip1559FallbackBehavior.FALLBACK_WARN);
    }

    SmartGasStrategy(
            final PublicClient publicClient,
            final BraneProvider provider,
            final ChainProfile profile,
            final BigInteger gasLimitBufferNumerator,
            final BigInteger gasLimitBufferDenominator) {
        this(publicClient, provider, profile, gasLimitBufferNumerator, gasLimitBufferDenominator,
                Eip1559FallbackBehavior.FALLBACK_WARN);
    }

    SmartGasStrategy(
            final PublicClient publicClient,
            final BraneProvider provider,
            final ChainProfile profile,
            final BigInteger gasLimitBufferNumerator,
            final BigInteger gasLimitBufferDenominator,
            final Eip1559FallbackBehavior eip1559FallbackBehavior) {
        this.publicClient = Objects.requireNonNull(publicClient, "publicClient");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.gasLimitBufferNumerator = requirePositive(gasLimitBufferNumerator, "gasLimitBufferNumerator");
        this.gasLimitBufferDenominator = requirePositive(gasLimitBufferDenominator, "gasLimitBufferDenominator");
        this.eip1559FallbackBehavior = Objects.requireNonNull(eip1559FallbackBehavior, "eip1559FallbackBehavior");
    }

    /**
     * Fills in missing transaction fields (from, gas limit, and fees) while keeping
     * any user-supplied values intact.
     *
     * <p>
     * <strong>⚠️ WARNING:</strong> Check {@link GasFilledRequest#fellBackToLegacy()} to detect
     * if an EIP-1559 request silently fell back to legacy gas pricing.
     *
     * @param request the transaction request with potentially missing gas fields
     * @param defaultFrom the address to use if request.from() is null
     * @return filled request with metadata indicating actual transaction type used
     */
    GasFilledRequest applyDefaults(final TransactionRequest request, final Address defaultFrom) {
        Objects.requireNonNull(defaultFrom, "defaultFrom");

        final boolean requestedEip1559 = request.isEip1559();
        TransactionRequest withFrom = request.from() != null ? request : copyWithFrom(request, defaultFrom);
        TransactionRequest withLimit = ensureGasLimit(withFrom);
        TransactionRequest withFees = ensureFees(withLimit);
        return new GasFilledRequest(withFees, requestedEip1559, withFees.isEip1559());
    }

    /**
     * Ensures a gas limit is present by estimating and applying the configured
     * buffer multiplier.
     */
    private TransactionRequest ensureGasLimit(final TransactionRequest request) {
        if (request.gasLimit() != null) {
            return request; // User provided gas limit - don't override
        }
        final Map<String, Object> tx = toTxObject(request);
        final String estimateHex = RpcRetry.run(() -> callEstimateGas(tx), 3);
        final BigInteger estimate = RpcUtils.decodeHexBigInteger(estimateHex);

        // Apply safety buffer to prevent out-of-gas failures
        // Default: estimate × (120/100) = 20% buffer
        final BigInteger buffered = estimate.multiply(gasLimitBufferNumerator).divide(gasLimitBufferDenominator);
        return copyWithGasFields(request, buffered.longValueExact(), request.gasPrice(), request.maxPriorityFeePerGas(),
                request.maxFeePerGas(), request.isEip1559());
    }

    private String callEstimateGas(final Map<String, Object> tx) {
        return RpcUtils.timedEstimateGas(tx, () -> {
            final JsonRpcResponse response = provider.send("eth_estimateGas", List.of(tx));
            if (response.hasError()) {
                final JsonRpcError err = response.error();
                throw new RpcException(
                        err.code(),
                        formatEstimateGasError(tx, err.message()),
                        err.data() != null ? err.data().toString() : null,
                        null,
                        null);
            }
            final Object resultObj = response.result();
            if (resultObj == null) {
                throw new RpcException(
                        -32000,
                        formatEstimateGasError(tx, "returned null result"),
                        (String) null,
                        (Throwable) null);
            }
            return resultObj.toString();
        });
    }

    /**
     * Formats an error message with transaction context for gas estimation failures.
     */
    private static String formatEstimateGasError(final Map<String, Object> tx, final String errorDetail) {
        final Object from = tx.get("from");
        final Object to = tx.get("to");
        final var sb = new StringBuilder("eth_estimateGas failed");
        if (from != null) {
            sb.append(" for tx from ").append(from);
        }
        if (to != null) {
            sb.append(" to ").append(to);
        }
        sb.append(": ").append(errorDetail);
        return sb.toString();
    }

    private TransactionRequest ensureFees(final TransactionRequest request) {
        // Route to appropriate fee calculation based on transaction type
        if (profile.supportsEip1559() && request.isEip1559()) {
            return ensureEip1559Fees(request);
        }
        return ensureLegacyFees(request);
    }

    /**
     * Derives {@code maxFeePerGas} and {@code maxPriorityFeePerGas} for EIP-1559
     * transactions using the
     * latest {@code baseFeePerGas}. Falls back to legacy fee estimation when base
     * fee data is unavailable.
     */
    private TransactionRequest ensureEip1559Fees(final TransactionRequest request) {
        if (request.maxFeePerGas() != null && request.maxPriorityFeePerGas() != null) {
            return request; // User provided both fees - don't override
        }

        final BlockHeader latest = publicClient.getLatestBlock();
        if (latest != null && latest.baseFeePerGas() != null) {
            final Wei baseFee = latest.baseFeePerGas();

            // Priority fee (miner tip): Use user value or chain default
            final Wei priority = request.maxPriorityFeePerGas() != null
                    ? request.maxPriorityFeePerGas()
                    : defaultPriority();

            // Max fee: (baseFee × 2) + priority
            // The 2x multiplier protects against base fee volatility:
            // - Base fee can increase up to 12.5% per block (EIP-1559)
            // - 2x buffer provides headroom for several blocks of increases
            // - If baseFee spikes, transaction won't fail with "max fee too low"
            final Wei maxFee = request.maxFeePerGas() != null
                    ? request.maxFeePerGas()
                    : new Wei(baseFee.value().multiply(BASE_FEE_MULTIPLIER).add(priority.value()));

            return copyWithGasFields(
                    request,
                    request.gasLimit(),
                    request.gasPrice(),
                    priority,
                    maxFee,
                    true);
        }

        // Fallback: If node doesn't provide baseFee (non-EIP-1559 chain), handle per config.
        // This can happen when:
        // 1. The chain doesn't support EIP-1559 (pre-London fork)
        // 2. The node returns null baseFeePerGas for the latest block
        return handleEip1559Fallback(request);
    }

    private TransactionRequest handleEip1559Fallback(final TransactionRequest request) {
        switch (eip1559FallbackBehavior) {
            case THROW -> throw new RpcException(
                    -32000,
                    "EIP-1559 transaction requested but baseFeePerGas unavailable from node. " +
                    "Chain may not support EIP-1559.",
                    (String) null);
            case FALLBACK_WARN -> log.warn(
                    "EIP-1559 transaction requested but baseFeePerGas unavailable from node; " +
                    "falling back to legacy gas pricing. Chain may not support EIP-1559.");
            case FALLBACK_SILENT -> { /* no logging */ }
        }
        final TransactionRequest legacy = copyWithGasFields(
                request, request.gasLimit(), request.gasPrice(), null, null, false);
        return ensureLegacyFees(legacy);
    }

    private TransactionRequest ensureLegacyFees(final TransactionRequest request) {
        if (request.gasPrice() != null) {
            return request;
        }
        final String gasPriceHex = RpcRetry.run(() -> callGasPrice(), 3);
        final BigInteger gasPrice = RpcUtils.decodeHexBigInteger(gasPriceHex);
        return copyWithGasFields(request, request.gasLimit(), new Wei(gasPrice), null, null, false);
    }

    private String callGasPrice() {
        final JsonRpcResponse response = provider.send("eth_gasPrice", List.of());
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(
                    err.code(), err.message(), err.data() != null ? err.data().toString() : null, null, null);
        }
        final Object resultObj = response.result();
        if (resultObj == null) {
            throw new RpcException(-32000, "eth_gasPrice returned null result", (String) null, (Throwable) null);
        }
        return resultObj.toString();
    }

    private Wei defaultPriority() {
        if (profile.defaultPriorityFeePerGas() != null) {
            return profile.defaultPriorityFeePerGas();
        }
        return Wei.of(1_000_000_000L);
    }

    private TransactionRequest copyWithFrom(final TransactionRequest request, final Address from) {
        return new TransactionRequest(
                from,
                request.to(),
                request.value(),
                request.gasLimit(),
                request.gasPrice(),
                request.maxPriorityFeePerGas(),
                request.maxFeePerGas(),
                request.nonce(),
                request.data(),
                request.isEip1559(),
                request.accessList());
    }

    private TransactionRequest copyWithGasFields(
            final TransactionRequest request,
            final Long gasLimit,
            final Wei gasPrice,
            final Wei maxPriorityFeePerGas,
            final Wei maxFeePerGas,
            final boolean isEip1559) {
        return new TransactionRequest(
                request.from(),
                request.to(),
                request.value(),
                gasLimit,
                gasPrice,
                maxPriorityFeePerGas,
                maxFeePerGas,
                request.nonce(),
                request.data(),
                isEip1559,
                request.accessList());
    }

    Map<String, Object> toTxObject(final TransactionRequest request) {
        return RpcUtils.buildTxObject(request, request.from());
    }

    private BigInteger requirePositive(final BigInteger value, final String name) {
        final BigInteger checked = Objects.requireNonNull(value, name);
        if (checked.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return checked;
    }
}
