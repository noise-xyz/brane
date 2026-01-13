// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc.internal;

import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import io.brane.core.InternalApi;
import io.brane.core.error.RpcException;
import io.brane.rpc.JsonRpcResponse;

/**
 * Internal helper for invoking JSON-RPC methods with standardized null handling.
 *
 * <p>This class encapsulates the common pattern of sending RPC requests and handling
 * null results. It provides three strategies for null handling:
 * <ul>
 *   <li>{@link #call} - throws {@link RpcException} if result is null</li>
 *   <li>{@link #callNullable} - returns null if result is null</li>
 *   <li>{@link #callWithDefault} - returns a default value if result is null</li>
 * </ul>
 *
 * <p><strong>Internal Use Only:</strong> This class is not part of the public API.
 * It exists to reduce code duplication in RPC client implementations.
 *
 * @since 0.1.0
 */
@InternalApi
public final class RpcInvoker {

    /**
     * Functional interface for sending JSON-RPC requests.
     *
     * <p>Implementations typically wrap the underlying transport (HTTP, WebSocket)
     * and may include retry logic.
     */
    @FunctionalInterface
    public interface RpcSender {
        /**
         * Sends a JSON-RPC request and returns the response.
         *
         * @param method the RPC method name (e.g., "eth_getBalance")
         * @param params the method parameters
         * @return the JSON-RPC response
         */
        JsonRpcResponse send(String method, List<?> params);
    }

    private final RpcSender sender;
    private final Runnable ensureOpen;

    /**
     * Creates a new RpcInvoker with the specified sender and connection checker.
     *
     * @param sender     the RPC sender for making requests
     * @param ensureOpen a runnable that throws if the connection is closed
     */
    public RpcInvoker(RpcSender sender, Runnable ensureOpen) {
        this.sender = sender;
        this.ensureOpen = ensureOpen;
    }

    /**
     * Invokes an RPC method and decodes the result, throwing if null.
     *
     * <p>This method is appropriate for RPC calls where a null result indicates
     * an error condition (e.g., eth_chainId, eth_getBalance).
     *
     * @param method  the RPC method name
     * @param params  the method parameters
     * @param decoder a function to decode the result string into the target type
     * @param <T>     the return type
     * @return the decoded result
     * @throws RpcException if the result is null
     */
    public <T> T call(String method, List<?> params, Function<String, T> decoder) {
        ensureOpen.run();
        JsonRpcResponse response = sender.send(method, params);
        Object result = response.result();
        if (result == null) {
            throw RpcException.fromNullResult(method);
        }
        return decoder.apply(result.toString());
    }

    /**
     * Invokes an RPC method and decodes the result, returning null if the result is null.
     *
     * <p>This method is appropriate for RPC calls where a null result is valid
     * (e.g., eth_getTransactionByHash for non-existent transactions).
     *
     * @param method  the RPC method name
     * @param params  the method parameters
     * @param decoder a function to decode the result string into the target type
     * @param <T>     the return type
     * @return the decoded result, or null if the result is null
     */
    public <T> @Nullable T callNullable(String method, List<?> params, Function<String, T> decoder) {
        ensureOpen.run();
        JsonRpcResponse response = sender.send(method, params);
        Object result = response.result();
        if (result == null) {
            return null;
        }
        return decoder.apply(result.toString());
    }

    /**
     * Invokes an RPC method and decodes the result, returning a default value if null.
     *
     * <p>This method is appropriate for RPC calls where a null result should be
     * replaced with a sensible default (e.g., eth_getCode returning empty hex).
     *
     * @param method       the RPC method name
     * @param params       the method parameters
     * @param decoder      a function to decode the result string into the target type
     * @param defaultValue the value to return if the result is null
     * @param <T>          the return type
     * @return the decoded result, or the default value if the result is null
     */
    public <T> T callWithDefault(String method, List<?> params, Function<String, T> decoder, T defaultValue) {
        ensureOpen.run();
        JsonRpcResponse response = sender.send(method, params);
        Object result = response.result();
        if (result == null) {
            return defaultValue;
        }
        return decoder.apply(result.toString());
    }
}
