package io.brane.rpc;

import static io.brane.rpc.internal.RpcUtils.MAPPER;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Represents a JSON-RPC 2.0 response from an Ethereum node.
 * <p>
 * This record holds the response from a JSON-RPC call, which may contain either
 * a successful result or an error. Use {@link #hasError()} to check which is present.
 * <p>
 * <strong>Accessing Results:</strong>
 * <ul>
 * <li>{@link #resultAsString()} - for simple string results (e.g., eth_chainId)</li>
 * <li>{@link #resultAsMap()} - for object results (e.g., eth_getBlockByNumber)</li>
 * <li>{@link #resultAsList()} - for array results (e.g., eth_getLogs)</li>
 * <li>{@link #resultAs(Class)} - for typed conversion using Jackson</li>
 * </ul>
 *
 * @param jsonrpc the JSON-RPC version (always "2.0")
 * @param result the result object if successful, or {@code null} if error
 * @param error the error object if failed, or {@code null} if successful
 * @param id the request ID that this response corresponds to
 * @since 0.1.0-alpha
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcResponse(
        String jsonrpc,
        @Nullable Object result,
        @Nullable JsonRpcError error,
        String id) {

    /**
     * Checks if this response contains an error.
     *
     * @return {@code true} if the response has an error, {@code false} otherwise
     */
    public boolean hasError() {
        return error != null;
    }

    /**
     * Returns the result as a String.
     * <p>
     * This is useful for RPC methods that return hex-encoded values
     * (e.g., eth_chainId, eth_gasPrice, eth_call).
     *
     * @return the result as a string, or {@code null} if result is null
     */
    public @Nullable String resultAsString() {
        return result != null ? result.toString() : null;
    }

    /**
     * Returns the result as a Map.
     * <p>
     * This is useful for RPC methods that return objects
     * (e.g., eth_getBlockByNumber, eth_getTransactionByHash).
     *
     * @return the result as a map, or {@code null} if result is null
     * @throws IllegalArgumentException if the result cannot be converted to a map
     */
    @SuppressWarnings("unchecked")
    public @Nullable Map<String, Object> resultAsMap() {
        if (result == null) {
            return null;
        }
        if (result instanceof Map<?, ?>) {
            return (Map<String, Object>) result;
        }
        return MAPPER.convertValue(result, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Returns the result as a List.
     * <p>
     * This is useful for RPC methods that return arrays
     * (e.g., eth_getLogs, eth_accounts).
     *
     * @return the result as a list, or {@code null} if result is null
     * @throws IllegalArgumentException if the result cannot be converted to a list
     */
    @SuppressWarnings("unchecked")
    public @Nullable List<Object> resultAsList() {
        if (result == null) {
            return null;
        }
        if (result instanceof List<?>) {
            return (List<Object>) result;
        }
        return MAPPER.convertValue(result, new TypeReference<List<Object>>() {});
    }

    /**
     * Converts the result to the specified type using Jackson.
     * <p>
     * This method provides type-safe conversion of the result object.
     *
     * @param type the target class type
     * @param <T> the target type
     * @return the result converted to the specified type, or {@code null} if result is null
     * @throws IllegalArgumentException if the result cannot be converted to the target type
     */
    public <T> @Nullable T resultAs(Class<T> type) {
        if (result == null) {
            return null;
        }
        return MAPPER.convertValue(result, type);
    }

    /**
     * Converts the result to the specified type using a TypeReference.
     * <p>
     * This method is useful for generic types like {@code List<Map<String, Object>>}.
     *
     * @param typeRef the type reference for the target type
     * @param <T> the target type
     * @return the result converted to the specified type, or {@code null} if result is null
     * @throws IllegalArgumentException if the result cannot be converted to the target type
     */
    public <T> @Nullable T resultAs(TypeReference<T> typeRef) {
        if (result == null) {
            return null;
        }
        return MAPPER.convertValue(result, typeRef);
    }
}
