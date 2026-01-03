package io.brane.rpc;

import org.jspecify.annotations.Nullable;

import io.brane.core.error.RpcException;

/**
 * A simplified RPC client interface for making typed JSON-RPC calls.
 *
 * <p>This interface provides a convenience layer over {@link BraneProvider} that
 * automatically deserializes the RPC response to the requested type. Use this when
 * you need simple, direct RPC calls with automatic type conversion.
 *
 * <h2>When to use each client layer:</h2>
 * <ul>
 *   <li>{@link BraneProvider} - Raw RPC transport; returns {@link JsonRpcResponse}</li>
 *   <li>{@code Client} - Typed RPC calls; returns deserialized result</li>
 *   <li>{@link PublicClient} - High-level Ethereum read operations</li>
 *   <li>{@link WalletClient} - High-level Ethereum read and write operations</li>
 * </ul>
 *
 * <h2>Example usage:</h2>
 * <pre>{@code
 * Client client = new RpcClient(provider);
 * BigInteger blockNumber = client.call("eth_blockNumber", BigInteger.class);
 * String chainId = client.call("eth_chainId", String.class);
 * }</pre>
 *
 * <p>For most Ethereum operations, prefer {@link PublicClient} or {@link WalletClient}
 * which provide type-safe methods for standard operations.
 *
 * <p><b>Thread Safety:</b> Implementations should be thread-safe for concurrent use.
 *
 * @see RpcClient
 * @see BraneProvider
 * @see PublicClient
 */
public interface Client {

    /**
     * Executes a JSON-RPC call and returns the result deserialized to the specified type.
     *
     * <p>Supported response types include:
     * <ul>
     *   <li>{@link String} - Returns the result as a string</li>
     *   <li>{@link java.math.BigInteger} - Parses hex strings (0x-prefixed) or decimal strings</li>
     *   <li>Any Jackson-deserializable type - Uses ObjectMapper conversion</li>
     * </ul>
     *
     * <h3>Null result handling:</h3>
     * <p>This method returns {@code null} when the JSON-RPC response contains a null result.
     * This is valid for certain RPC methods where null indicates absence of data, such as:
     * <ul>
     *   <li>{@code eth_getTransactionByHash} - returns null if the transaction doesn't exist</li>
     *   <li>{@code eth_getBlockByNumber/Hash} - returns null if the block doesn't exist</li>
     *   <li>{@code eth_call} - may return null for certain contract calls</li>
     * </ul>
     * <p>Callers should handle null returns appropriately based on the RPC method semantics.
     *
     * @param method       the JSON-RPC method name (e.g., "eth_blockNumber")
     * @param responseType the class to deserialize the result to
     * @param params       the method parameters (may be empty)
     * @param <T>          the response type
     * @return the deserialized result, or {@code null} if the RPC result is null
     * @throws RpcException if the RPC call fails or the result cannot be deserialized
     */
    <T> @Nullable T call(String method, Class<T> responseType, Object... params) throws RpcException;
}
