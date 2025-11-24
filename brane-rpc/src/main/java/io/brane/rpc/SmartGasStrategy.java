package io.brane.rpc;

import io.brane.core.chain.ChainProfile;
import io.brane.core.error.RpcException;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Wei;
import io.brane.internal.web3j.utils.Numeric;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.brane.rpc.JsonRpcError;
import io.brane.rpc.JsonRpcResponse;


final class SmartGasStrategy {

    private final PublicClient publicClient;
    private final BraneProvider provider;
    private final ChainProfile profile;

    SmartGasStrategy(
            final PublicClient publicClient, final BraneProvider provider, final ChainProfile profile) {
        this.publicClient = Objects.requireNonNull(publicClient, "publicClient");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    TransactionRequest applyDefaults(final TransactionRequest request, final Address defaultFrom) {
        Objects.requireNonNull(defaultFrom, "defaultFrom");

        TransactionRequest withFrom = request.from() != null ? request : copyWithFrom(request, defaultFrom);
        TransactionRequest withLimit = ensureGasLimit(withFrom);
        return ensureFees(withLimit);
    }

    private TransactionRequest ensureGasLimit(final TransactionRequest request) {
        if (request.gasLimit() != null) {
            return request;
        }
        final Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("from", request.from().value());
        request.toOpt().ifPresent(address -> tx.put("to", address.value()));
        request.valueOpt().ifPresent(v -> tx.put("value", toQuantityHex(v.value())));
        if (request.data() != null) {
            tx.put("data", request.data().value());
        }
        final String estimateHex = RpcRetry.run(() -> callEstimateGas(tx), 3);
        final BigInteger estimate = Numeric.decodeQuantity(estimateHex);
        final BigInteger buffered = estimate.multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100));
        return copyWithGasFields(request, buffered.longValueExact(), request.gasPrice(), request.maxPriorityFeePerGas(), request.maxFeePerGas(), request.isEip1559());
    }

    private String callEstimateGas(final Map<String, Object> tx) {
        final JsonRpcResponse response = provider.send("eth_estimateGas", List.of(tx));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(
                    err.code(), err.message(), err.data() != null ? err.data().toString() : null, null, null);
        }
        return response.result().toString();
    }

    private TransactionRequest ensureFees(final TransactionRequest request) {
        if (profile.supportsEip1559() && request.isEip1559()) {
            return ensureEip1559Fees(request);
        }
        return ensureLegacyFees(request);
    }

    private TransactionRequest ensureEip1559Fees(final TransactionRequest request) {
        if (request.maxFeePerGas() != null && request.maxPriorityFeePerGas() != null) {
            return request;
        }

        final var latest = publicClient.getLatestBlock();
        if (latest != null && latest.baseFeePerGas() != null) {
            final Wei baseFee = latest.baseFeePerGas();
            final Wei priority =
                    request.maxPriorityFeePerGas() != null
                            ? request.maxPriorityFeePerGas()
                            : defaultPriority();
            final Wei maxFee =
                    request.maxFeePerGas() != null
                            ? request.maxFeePerGas()
                            : new Wei(baseFee.value().multiply(BigInteger.valueOf(2)).add(priority.value()));

            return copyWithGasFields(
                    request,
                    request.gasLimit(),
                    request.gasPrice(),
                    priority,
                    maxFee,
                    true);
        }

        // Fallback to legacy path when base fee is missing
        final TransactionRequest legacy =
                copyWithGasFields(request, request.gasLimit(), request.gasPrice(), null, null, false);
        return ensureLegacyFees(legacy);
    }

    private TransactionRequest ensureLegacyFees(final TransactionRequest request) {
        if (request.gasPrice() != null) {
            return request;
        }
        final String gasPriceHex = RpcRetry.run(() -> callGasPrice(), 3);
        final BigInteger gasPrice = Numeric.decodeQuantity(gasPriceHex);
        return copyWithGasFields(request, request.gasLimit(), new Wei(gasPrice), null, null, false);
    }

    private String callGasPrice() {
        final JsonRpcResponse response = provider.send("eth_gasPrice", List.of());
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(
                    err.code(), err.message(), err.data() != null ? err.data().toString() : null, null, null);
        }
        return response.result().toString();
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
                request.isEip1559());
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
                isEip1559);
    }

    private String toQuantityHex(final BigInteger value) {
        return "0x" + value.toString(16);
    }
}
