package io.brane.contract;

import io.brane.core.RevertDecoder;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.types.Address;
import io.brane.internal.web3j.crypto.RawTransaction;
import io.brane.internal.web3j.utils.Numeric;
import io.brane.rpc.Client;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Contract {

    private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(300_000L);

    private final Address address;
    private final Abi abi;
    private final Client client;

    public Contract(final Address address, final Abi abi, final Client client) {
        this.address = address;
        this.abi = abi;
        this.client = client;
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
            handlePotentialRevert(e);
            throw e;
        }
    }

    public String write(final Signer signer, final String functionName, final Object... args)
            throws RpcException, RevertException {
        final Abi.FunctionCall call = abi.encodeFunction(functionName, args);
        final String data = call.data();

        final String from = signer.address().value();
        final BigInteger nonce =
                decodeQuantity(
                        client.call("eth_getTransactionCount", String.class, from, "latest"));
        final BigInteger gasPrice = decodeQuantity(client.call("eth_gasPrice", String.class));

        final RawTransaction tx =
                RawTransaction.createTransaction(
                        nonce, gasPrice, DEFAULT_GAS_LIMIT, address.value(), BigInteger.ZERO, data);

        final String signedHex = signer.signTransaction(tx);
        try {
            return client.call("eth_sendRawTransaction", String.class, signedHex);
        } catch (RpcException e) {
            handlePotentialRevert(e);
            throw e;
        }
    }

    private static BigInteger decodeQuantity(final String value) {
        return Numeric.decodeQuantity(value);
    }

    private static void handlePotentialRevert(final RpcException e) throws RevertException {
        final String raw = e.data();
        if (raw != null && raw.startsWith("0x") && raw.length() > 10) {
            final var decoded = RevertDecoder.decode(raw);
            // Always throw RevertException for revert data, even if kind is UNKNOWN
            // (UNKNOWN just means we couldn't decode it, but it's still a revert)
            throw new RevertException(decoded.kind(), decoded.reason(), decoded.rawDataHex(), e);
        }
    }
}
