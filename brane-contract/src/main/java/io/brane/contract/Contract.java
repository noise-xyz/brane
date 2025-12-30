package io.brane.contract;

import io.brane.core.RevertDecoder;
import io.brane.core.abi.Abi;
import io.brane.core.crypto.Signature;
import io.brane.core.crypto.Signer;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.tx.Eip1559Transaction;
import io.brane.core.tx.LegacyTransaction;
import io.brane.core.tx.UnsignedTransaction;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.primitives.Hex;
import io.brane.rpc.Client;
import io.brane.rpc.internal.RpcUtils;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Contract {

    private final Address address;
    private final Abi abi;
    private final Client client;
    private final ContractOptions options;

    /**
     * Creates a new Contract with default options.
     *
     * @param address the contract address
     * @param abi the contract ABI
     * @param client the RPC client
     */
    public Contract(final Address address, final Abi abi, final Client client) {
        this(address, abi, client, ContractOptions.defaults());
    }

    /**
     * Creates a new Contract with custom options.
     *
     * @param address the contract address
     * @param abi the contract ABI
     * @param client the RPC client
     * @param options the contract options for gas limit, timeouts, etc.
     */
    public Contract(final Address address, final Abi abi, final Client client, final ContractOptions options) {
        this.address = Objects.requireNonNull(address, "address must not be null");
        this.abi = Objects.requireNonNull(abi, "abi must not be null");
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.options = Objects.requireNonNull(options, "options must not be null");
    }

    public <T> T read(final String fn, final Class<T> returnType, final Object... args)
            throws RpcException, RevertException {
        final Abi.FunctionCall call = abi.encodeFunction(fn, args);
        final String data = call.data();

        final Map<String, Object> callObject = new LinkedHashMap<>();
        callObject.put("to", address.value());
        callObject.put("data", data);

        try {
            final String result = client.call("eth_call", String.class, callObject, "latest");
            if (result == null) {
                throw new RpcException(
                        -32002,
                        "eth_call returned null result for function '" + fn + "'",
                        null,
                        (Long) null);
            }
            return call.decode(result, returnType);
        } catch (RpcException e) {
            RevertDecoder.throwIfRevert(e);
            throw e;
        }
    }

    public String write(final Signer signer, final String functionName, final Object... args)
            throws RpcException, RevertException {
        final Abi.FunctionCall call = abi.encodeFunction(functionName, args);
        final String data = call.data();

        final String from = signer.address().value();

        // Get chainId
        final String chainIdHex = client.call("eth_chainId", String.class);
        final long chainId = RpcUtils.decodeHexBigInteger(chainIdHex).longValue();

        final BigInteger nonce = RpcUtils.decodeHexBigInteger(
                client.call("eth_getTransactionCount", String.class, from, "latest"));

        final UnsignedTransaction tx;
        final Signature signature;

        if (options.transactionType() == ContractOptions.TransactionType.EIP1559) {
            // EIP-1559 transaction
            final BigInteger baseFee = RpcUtils.decodeHexBigInteger(client.call("eth_gasPrice", String.class));
            final Wei maxPriorityFee = options.maxPriorityFee();
            final Wei maxFeePerGas = Wei.of(baseFee.add(maxPriorityFee.value()));

            final Eip1559Transaction eip1559Tx = new Eip1559Transaction(
                    chainId,
                    nonce.longValue(),
                    maxPriorityFee,
                    maxFeePerGas,
                    options.gasLimit(),
                    address,
                    Wei.of(0),
                    new HexData(data),
                    List.of());

            tx = eip1559Tx;
            // For EIP-1559, v is just yParity (0 or 1), no EIP-155 adjustment needed
            signature = signer.signTransaction(eip1559Tx, chainId);
        } else {
            // Legacy transaction
            final BigInteger gasPrice = RpcUtils.decodeHexBigInteger(client.call("eth_gasPrice", String.class));

            final LegacyTransaction legacyTx = new LegacyTransaction(
                    nonce.longValue(),
                    Wei.of(gasPrice),
                    options.gasLimit(),
                    address,
                    Wei.of(0),
                    new HexData(data));

            tx = legacyTx;
            final Signature baseSig = signer.signTransaction(legacyTx, chainId);
            // Adjust V for Legacy Transaction (EIP-155)
            final int v = (int) (chainId * 2 + 35 + baseSig.v());
            signature = new Signature(baseSig.r(), baseSig.s(), v);
        }

        final byte[] envelope = tx.encodeAsEnvelope(signature);
        final String signedHex = Hex.encode(envelope);
        try {
            return client.call("eth_sendRawTransaction", String.class, signedHex);
        } catch (RpcException e) {
            RevertDecoder.throwIfRevert(e);
            throw e;
        }
    }
}
