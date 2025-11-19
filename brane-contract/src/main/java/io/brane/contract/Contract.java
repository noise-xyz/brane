package io.brane.contract;

import io.brane.core.RevertDecoder;
import io.brane.core.RevertException;
import io.brane.core.RpcException;
import io.brane.rpc.Client;
import java.util.Map;

public final class Contract {

    private final String address;
    private final Abi abi;
    private final Client client;

    public Contract(final String address, final Abi abi, final Client client) {
        this.address = address;
        this.abi = abi;
        this.client = client;
    }

    public <T> T read(final String fn, final Class<T> returnType, final Object... args)
            throws RpcException, RevertException {
        final Abi.FunctionCall call = abi.encodeFunction(fn, args);
        final String data = call.data();

        final Map<String, String> callObject = Map.of("to", address, "data", data);

        try {
            final String result =
                    client.call("eth_call", String.class, callObject, "latest");
            return call.decode(result, returnType);
        } catch (RpcException e) {
            final String raw = e.data();
            if (raw != null && raw.startsWith("0x") && raw.length() > 10) {
                final var decoded = RevertDecoder.decode(raw);
                throw new RevertException(decoded.reason(), decoded.rawDataHex(), e);
            }

            throw e;
        }
    }
}
