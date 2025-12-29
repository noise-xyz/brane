package io.brane.contract;

import io.brane.core.RevertDecoder;
import io.brane.core.abi.Abi;
import io.brane.core.error.AbiDecodingException;
import io.brane.core.error.AbiEncodingException;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.types.Address;
import io.brane.rpc.PublicClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only façade for contract calls using Abi + PublicClient.
 */
public class ReadOnlyContract {

    private final Address address;
    private final Abi abi;
    private final PublicClient client;

    protected ReadOnlyContract(final Address address, final Abi abi, final PublicClient client) {
        this.address = Objects.requireNonNull(address, "address must not be null");
        this.abi = Objects.requireNonNull(abi, "abi must not be null");
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    public static ReadOnlyContract from(
            final Address address, final Abi abi, final PublicClient client) {
        return new ReadOnlyContract(address, abi, client);
    }

    public Address address() {
        return address;
    }

    public Abi abi() {
        return abi;
    }

    public <T> T call(final String functionName, final Class<T> returnType, final Object... args)
            throws RpcException, RevertException, AbiEncodingException, AbiDecodingException {
        final Abi.FunctionCall fnCall = abi.encodeFunction(functionName, args);

        final Map<String, Object> callObject = new LinkedHashMap<>();
        callObject.put("to", address.value());
        callObject.put("data", fnCall.data());

        try {
            final String raw = client.call(callObject, "latest");
            if (raw == null || raw.isBlank()) {
                throw new AbiDecodingException(
                        "eth_call returned empty result for function '" + functionName + "'");
            }
            return fnCall.decode(raw, returnType);
        } catch (RpcException e) {
            handlePotentialRevert(e);
            throw e;
        }
    }

    public <T> java.util.List<T> decodeEvents(
            final String eventName,
            final java.util.List<io.brane.core.model.LogEntry> logs,
            final Class<T> eventType) {
        return abi.decodeEvents(eventName, logs, eventType);
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
