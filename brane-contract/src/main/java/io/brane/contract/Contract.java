package io.brane.contract;

import io.brane.core.RevertDecoder;
import io.brane.core.abi.Abi;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.tx.LegacyTransaction;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.Client;
import io.brane.rpc.internal.RpcUtils;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import io.brane.primitives.Hex;

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
            RevertDecoder.throwIfRevert(e);
            throw e;
        }
    }

    public String write(final io.brane.core.crypto.Signer signer, final String functionName, final Object... args)
            throws RpcException, RevertException {
        final Abi.FunctionCall call = abi.encodeFunction(functionName, args);
        final String data = call.data();

        final String from = signer.address().value();

        // Get chainId
        final String chainIdHex = client.call("eth_chainId", String.class);
        final long chainId = RpcUtils.decodeHexBigInteger(chainIdHex).longValue();

        final BigInteger nonce = RpcUtils.decodeHexBigInteger(
                client.call("eth_getTransactionCount", String.class, from, "latest"));
        final BigInteger gasPrice = RpcUtils.decodeHexBigInteger(client.call("eth_gasPrice", String.class));

        final LegacyTransaction tx = new LegacyTransaction(
                nonce.longValue(),
                Wei.of(gasPrice),
                DEFAULT_GAS_LIMIT.longValue(),
                address,
                Wei.of(0),
                new HexData(data));

        final io.brane.core.crypto.Signature baseSig = signer.signTransaction(tx, chainId);

        // Adjust V for Legacy Transaction (EIP-155)
        final int v = (int) (chainId * 2 + 35 + baseSig.v());
        final io.brane.core.crypto.Signature signature = new io.brane.core.crypto.Signature(baseSig.r(), baseSig.s(),
                v);

        final byte[] envelope = tx.encodeAsEnvelope(signature);
        final String signedHex = io.brane.primitives.Hex.encode(envelope);
        try {
            return client.call("eth_sendRawTransaction", String.class, signedHex);
        } catch (RpcException e) {
            RevertDecoder.throwIfRevert(e);
            throw e;
        }
    }
}
