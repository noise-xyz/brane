package io.brane.rpc;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.brane.core.error.RpcException;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.rpc.internal.RpcUtils;

/**
 * Default implementation of {@link ImpersonationSession} for test nodes.
 *
 * <p>Allows sending transactions from an impersonated address without possessing
 * its private key. The session automatically stops impersonation when closed.
 *
 * @since 0.3.0
 */
final class DefaultImpersonationSession implements ImpersonationSession {

    private static final Logger log = LoggerFactory.getLogger(DefaultImpersonationSession.class);

    private final DefaultTester tester;
    private final Address address;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    DefaultImpersonationSession(final DefaultTester tester, final Address address) {
        this.tester = tester;
        this.address = address;
    }

    @Override
    public Address address() {
        return address;
    }

    @Override
    public Hash sendTransaction(final TransactionRequest request) {
        ensureOpen();
        // Build transaction params with the impersonated address as from
        final Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("from", address.value());
        if (request.to() != null) {
            tx.put("to", request.to().value());
        }
        if (request.value() != null) {
            tx.put("value", RpcUtils.toQuantityHex(request.value().value()));
        }
        if (request.data() != null && request.data().byteLength() > 0) {
            tx.put("data", request.data().value());
        }
        if (request.gasLimit() != null) {
            tx.put("gas", RpcUtils.toQuantityHex(BigInteger.valueOf(request.gasLimit())));
        }
        if (request.gasPrice() != null) {
            tx.put("gasPrice", RpcUtils.toQuantityHex(request.gasPrice().value()));
        }
        if (request.maxFeePerGas() != null) {
            tx.put("maxFeePerGas", RpcUtils.toQuantityHex(request.maxFeePerGas().value()));
        }
        if (request.maxPriorityFeePerGas() != null) {
            tx.put("maxPriorityFeePerGas", RpcUtils.toQuantityHex(request.maxPriorityFeePerGas().value()));
        }
        if (request.nonce() != null) {
            tx.put("nonce", RpcUtils.toQuantityHex(BigInteger.valueOf(request.nonce())));
        }

        final JsonRpcResponse response = sendWithRetry("eth_sendTransaction", List.of(tx));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
        final Object result = response.result();
        if (result == null) {
            throw new RpcException(-32000, "eth_sendTransaction returned null", (String) null, (Throwable) null);
        }
        return new Hash(result.toString());
    }

    @Override
    public TransactionReceipt sendTransactionAndWait(final TransactionRequest request) {
        return sendTransactionAndWait(request, Brane.Signer.DEFAULT_TIMEOUT_MILLIS, Brane.Signer.DEFAULT_POLL_INTERVAL_MILLIS);
    }

    @Override
    public TransactionReceipt sendTransactionAndWait(
            final TransactionRequest request, final long timeoutMillis, final long pollIntervalMillis) {
        final Hash txHash = sendTransaction(request);

        final long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        long currentInterval = pollIntervalMillis;
        final long maxPollInterval = 10_000L;

        while (System.nanoTime() - deadlineNanos < 0) {
            final TransactionReceipt receipt = tester.getTransactionReceipt(txHash);
            if (receipt != null) {
                if (!receipt.status()) {
                    throw new io.brane.core.error.RevertException(
                            io.brane.core.RevertDecoder.RevertKind.UNKNOWN,
                            "Transaction reverted (txHash: " + txHash.value() + ")",
                            null,
                            null);
                }
                return receipt;
            }
            try {
                Thread.sleep(currentInterval);
                currentInterval = Math.min(currentInterval * 2, maxPollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RpcException(-32000, "Interrupted while waiting for receipt", null, e);
            }
        }

        throw new RpcException(
                -32000,
                "Timed out waiting for transaction receipt for " + txHash.value(),
                null,
                null,
                null);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                tester.stopImpersonating(address);
            } catch (Exception e) {
                log.warn("Failed to stop impersonating {}: {}", address.value(), e.getMessage());
            }
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("ImpersonationSession has been closed");
        }
    }

    private JsonRpcResponse sendWithRetry(final String method, final List<?> params) {
        return RpcRetry.runRpc(
                () -> tester.provider().send(method, params),
                tester.maxRetries() + 1,
                tester.retryConfig());
    }
}
