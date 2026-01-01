package io.brane.rpc;

import io.brane.core.chain.ChainProfile;
import io.brane.core.error.RpcException;
import io.brane.core.model.AccessListEntry;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.Wei;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.brane.rpc.internal.RpcUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 */
final class SmartGasStrategy {

    private static final Logger log = LoggerFactory.getLogger(SmartGasStrategy.class);

    static final BigInteger DEFAULT_GAS_LIMIT_BUFFER_NUMERATOR = BigInteger.valueOf(120);
    static final BigInteger DEFAULT_GAS_LIMIT_BUFFER_DENOMINATOR = BigInteger.valueOf(100);
    static final BigInteger BASE_FEE_MULTIPLIER = BigInteger.valueOf(2);

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
     * any user-supplied
     * values intact.
     */
    TransactionRequest applyDefaults(final TransactionRequest request, final Address defaultFrom) {
        Objects.requireNonNull(defaultFrom, "defaultFrom");

        TransactionRequest withFrom = request.from() != null ? request : copyWithFrom(request, defaultFrom);
        TransactionRequest withLimit = ensureGasLimit(withFrom);
        return ensureFees(withLimit);
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
        final var from = tx.get("from");
        final var to = tx.get("to");
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

        final var latest = publicClient.getLatestBlock();
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
