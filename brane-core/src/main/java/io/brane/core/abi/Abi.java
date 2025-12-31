package io.brane.core.abi;

import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.crypto.Keccak256;
import io.brane.primitives.Hex;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Application Binary Interface (ABI) for encoding/decoding smart contract
 * function calls and events.
 * 
 * <p>
 * This interface provides methods to:
 * <ul>
 * <li>Encode function calls to hex calldata</li>
 * <li>Decode function return values from hex to Java types</li>
 * <li>Decode event logs to Java objects</li>
 * <li>Compute function selectors and event topics</li>
 * </ul>
 * 
 * <p>
 * <strong>ABI Encoding:</strong> Converts Java method calls to hex-encoded EVM
 * calldata.
 * The calldata consists of a 4-byte function selector (first 4 bytes of
 * keccak256 hash
 * of function signature) followed by ABI-encoded arguments.
 * 
 * <p>
 * <strong>Function Selectors:</strong>
 * 
 * <pre>{@code
 * // transfer(address,uint256) -> 0xa9059cbb
 * HexData selector = Abi.functionSelector("transfer(address,uint256)");
 * }</pre>
 * 
 * <p>
 * <strong>Event Topics:</strong>
 * 
 * <pre>{@code
 * // Transfer(address,address,uint256) -> topic0
 * Hash topic = Abi.eventTopic("Transfer(address,address,uint256)");
 * }</pre>
 * 
 * <p>
 * <strong>Usage Example:</strong>
 * 
 * <pre>{@code
 * // Load ABI from JSON
 * Abi abi = Abi.fromJson(abiJsonString);
 * 
 * // Encode function call
 * FunctionCall call = abi.encodeFunction(
 *         "transfer",
 *         new Address("0x..."),
 *         BigInteger.valueOf(1000000));
 * String calldata = call.data(); // "0xa9059cbb000000..."
 * 
 * // Decode return value
 * String returnHex = "0x0000000000000000000000000000000000000000000000000000000000000001";
 * Boolean success = call.decode(returnHex, Boolean.class);
 * }</pre>
 * 
 * @see InternalAbi
 */
public interface Abi {

    /**
     * Creates an ABI instance from JSON contract metadata.
     * 
     * @param json the ABI JSON string (array of function/event definitions)
     * @return an ABI instance
     * @throws IllegalArgumentException if JSON is invalid
     */
    static Abi fromJson(final String json) {
        return new InternalAbi(json);
    }

    /**
     * Computes the event topic (topic0) from an event signature.
     * 
     * <p>
     * The topic is the keccak256 hash of the event signature string.
     * Event signature format: {@code EventName(type1,type2,...)}
     * 
     * @param eventSignature the event signature (e.g.,
     *                       "Transfer(address,address,uint256)")
     * @return the event topic hash
     * @throws IllegalArgumentException if eventSignature is null or blank
     */
    static Hash eventTopic(final String eventSignature) {
        final String signature = requireNonEmpty(eventSignature, "eventSignature");
        final byte[] digest = Keccak256.hash(signature.getBytes(StandardCharsets.UTF_8));
        return new Hash(Hex.encode(digest));
    }

    /**
     * Computes the 4-byte function selector from a function signature.
     * 
     * <p>
     * The selector is the first 4 bytes of the keccak256 hash of the function
     * signature.
     * Function signature format: {@code functionName(type1,type2,...)}
     * 
     * @param functionSignature the function signature (e.g.,
     *                          "transfer(address,uint256)")
     * @return the 4-byte function selector
     * @throws IllegalArgumentException if functionSignature is null or blank
     */
    static HexData functionSelector(final String functionSignature) {
        final String signature = requireNonEmpty(functionSignature, "functionSignature");
        final byte[] digest = Keccak256.hash(
                signature.getBytes(StandardCharsets.UTF_8));
        final String hex = Hex.encode(digest).substring(0, 10);
        return new HexData(hex);
    }

    /**
     * Computes the 4-byte function selector as a raw hex string (no 0x prefix).
     * 
     * @param functionSignature the function signature
     * @return 8-character hex string
     */
    static String getSelector(final String functionSignature) {
        return functionSelector(functionSignature).value().substring(2);
    }

    /**
     * Decodes the return value of a Multicall3 aggregate3 call.
     * The expected format is (bool success, bytes returnData)[]
     * 
     * @param hex the raw hex data from the RPC response (with or without 0x prefix)
     * @return a list of MulticallResult records
     */
    static List<io.brane.core.model.MulticallResult> decodeMulticallResults(String hex) {
        return InternalAbi.decodeMulticallResults(hex);
    }

    /**
     * Encodes a function call with arguments into calldata.
     *
     * @param name the function name
     * @param args the function arguments in order
     * @return the encoded function call
     * @throws io.brane.core.error.AbiEncodingException if the function is not found in the ABI,
     *         argument count doesn't match, or argument types cannot be encoded
     */
    FunctionCall encodeFunction(String name, Object... args);

    /**
     * Encodes constructor arguments into hex data.
     *
     * @param args the constructor arguments in order
     * @return the encoded constructor arguments (without bytecode)
     * @throws io.brane.core.error.AbiEncodingException if the constructor is not defined but arguments
     *         are provided, argument count doesn't match, or argument types cannot be encoded
     */
    HexData encodeConstructor(Object... args);

    Optional<FunctionMetadata> getFunction(String name);

    /**
     * Decodes matching event logs into instances of the specified type.
     *
     * <p>
     * Filters the provided logs to those matching the event signature, then decodes
     * each matching log into an instance of {@code eventType}.
     *
     * <h3>Type Mapping</h3>
     * <p>
     * The {@code eventType} can be:
     * <ul>
     * <li>{@code List.class} - returns decoded values as a List</li>
     * <li>{@code Object[].class} - returns decoded values as an array</li>
     * <li>A record or class with a constructor matching the event parameters</li>
     * </ul>
     *
     * <h3>Java Module System Requirements</h3>
     * <p>
     * <b>Important:</b> When using custom event types (records or classes), the type
     * and its constructor must be accessible:
     * <ul>
     * <li>The event type must be {@code public}</li>
     * <li>The constructor must be {@code public}</li>
     * <li>For modular applications (Java 9+), the package must be exported or opened</li>
     * </ul>
     *
     * <p>
     * If using the Java module system with strong encapsulation, you may need to add
     * {@code --add-opens} JVM arguments or use {@code opens} directives in your
     * {@code module-info.java}.
     *
     * <h3>Example</h3>
     * <pre>{@code
     * // Define a public record matching the event
     * public record Transfer(Address from, Address to, BigInteger value) {}
     *
     * // Decode Transfer events from transaction logs
     * List<Transfer> transfers = abi.decodeEvents("Transfer", receipt.logs(), Transfer.class);
     * }</pre>
     *
     * @param eventName the name of the event to decode
     * @param logs      the logs to filter and decode
     * @param eventType the type to decode events into
     * @param <T>       the event type
     * @return list of decoded events (empty if no matching logs)
     * @throws io.brane.core.error.AbiDecodingException if decoding fails or the event
     *         type cannot be instantiated (e.g., non-public constructor, module access denied)
     */
    <T> java.util.List<T> decodeEvents(String eventName, java.util.List<io.brane.core.model.LogEntry> logs,
            Class<T> eventType);

    record FunctionMetadata(String name, String stateMutability, List<String> inputs, List<String> outputs) {
        public boolean isView() {
            return "view".equals(stateMutability) || "pure".equals(stateMutability);
        }

        public boolean isPayable() {
            return "payable".equals(stateMutability);
        }
    }

    private static String requireNonEmpty(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be provided");
        }
        return value;
    }

    interface FunctionCall {
        String data();

        <T> T decode(String output, Class<T> returnType);
    }
}

