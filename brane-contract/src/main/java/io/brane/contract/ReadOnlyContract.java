package io.brane.contract;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.brane.core.RevertDecoder;
import io.brane.core.abi.Abi;
import io.brane.core.error.AbiDecodingException;
import io.brane.core.error.AbiEncodingException;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.types.Address;
import io.brane.rpc.PublicClient;

/**
 * Read-only façade for smart contract interactions.
 *
 * <p>Provides a convenient API for calling view/pure contract functions and
 * decoding events from transaction logs. This class wraps the lower-level
 * {@link Abi} encoding/decoding with direct RPC calls via {@link PublicClient}.
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * // Create contract instance
 * Abi abi = Abi.fromJson(abiJson);
 * ReadOnlyContract contract = ReadOnlyContract.from(
 *     new Address("0x..."),
 *     abi,
 *     publicClient
 * );
 *
 * // Call view functions
 * BigInteger balance = contract.call("balanceOf", BigInteger.class, holderAddress);
 * String name = contract.call("name", String.class);
 *
 * // Decode events from logs
 * List<TransferEvent> transfers = contract.decodeEvents(
 *     "Transfer",
 *     receipt.logs(),
 *     TransferEvent.class
 * );
 * }</pre>
 *
 * <p>For type-safe contract interaction with compile-time checking,
 * consider using {@link BraneContract#bind} with a Java interface instead.
 *
 * @see BraneContract#bind
 * @see ReadWriteContract
 * @see PublicClient
 */
public class ReadOnlyContract {

    private final Address address;
    private final Abi abi;
    private final PublicClient client;

    /**
     * Creates a new ReadOnlyContract instance.
     *
     * @param address the deployed contract address
     * @param abi     the contract ABI
     * @param client  the public client for RPC calls
     */
    protected ReadOnlyContract(final Address address, final Abi abi, final PublicClient client) {
        this.address = Objects.requireNonNull(address, "address must not be null");
        this.abi = Objects.requireNonNull(abi, "abi must not be null");
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    /**
     * Creates a new ReadOnlyContract for the specified contract.
     *
     * @param address the deployed contract address
     * @param abi     the contract ABI
     * @param client  the public client for RPC calls
     * @return a new ReadOnlyContract instance
     * @throws NullPointerException if any parameter is null
     */
    public static ReadOnlyContract from(
            final Address address, final Abi abi, final PublicClient client) {
        return new ReadOnlyContract(address, abi, client);
    }

    /**
     * Returns the contract address.
     *
     * @return the contract address
     */
    public Address address() {
        return address;
    }

    /**
     * Returns the contract ABI.
     *
     * @return the contract ABI
     */
    public Abi abi() {
        return abi;
    }

    /**
     * Calls a view/pure contract function and returns the decoded result.
     *
     * <p>This method encodes the function call using the ABI, sends it via
     * {@code eth_call}, and decodes the result to the specified return type.
     *
     * @param <T>          the return type
     * @param functionName the contract function name
     * @param returnType   the expected return type class
     * @param args         the function arguments
     * @return the decoded return value
     * @throws RpcException         if the RPC call fails
     * @throws RevertException      if the contract reverts
     * @throws AbiEncodingException if encoding the function call fails
     * @throws AbiDecodingException if decoding the result fails
     */
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
            RevertDecoder.throwIfRevert(e);
            throw e;
        }
    }

    /**
     * Decodes events from transaction logs.
     *
     * <p>Filters the provided logs for events matching the specified name and
     * decodes them into the given event type.
     *
     * @param <T>       the event type
     * @param eventName the event name as defined in the ABI
     * @param logs      the log entries to decode (typically from a transaction receipt)
     * @param eventType the class representing the event structure
     * @return a list of decoded events
     */
    public <T> java.util.List<T> decodeEvents(
            final String eventName,
            final java.util.List<io.brane.core.model.LogEntry> logs,
            final Class<T> eventType) {
        return abi.decodeEvents(eventName, logs, eventType);
    }
}
