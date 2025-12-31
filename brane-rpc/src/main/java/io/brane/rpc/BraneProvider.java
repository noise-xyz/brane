package io.brane.rpc;

import io.brane.core.error.RpcException;
import java.util.List;

/**
 * Low-level abstraction for sending JSON-RPC requests to an Ethereum node.
 * 
 * <p>
 * This interface abstracts the transport layer (HTTP, WebSocket, IPC, etc.)
 * from the RPC business logic. Implementations handle:
 * <ul>
 * <li>Serializing requests to JSON</li>
 * <li>Sending requests over the wire (HTTP, WS, etc.)</li>
 * <li>Deserializing responses from JSON</li>
 * <li>Error handling and retries (implementation-specific)</li>
 * </ul>
 * 
 * <p>
 * <strong>Thread Safety:</strong> Implementations must be thread-safe.
 * 
 * <p>
 * <strong>Usage:</strong> Most users should use {@link PublicClient} or
 * {@link WalletClient} instead of calling this directly. This interface is
 * primarily for internal use and custom provider implementations.
 * 
 * <p>
 * <strong>Built-in Implementations:</strong>
 * <ul>
 * <li>{@link HttpBraneProvider} - HTTP/HTTPS transport (default)</li>
 * </ul>
 * 
 * @see HttpBraneProvider
 * @see PublicClient#from(BraneProvider)
 */
public interface BraneProvider extends AutoCloseable {

    /**
     * Sends a JSON-RPC request.
     *
     * @param method the JSON-RPC method name
     * @param params the list of parameters
     * @return the JSON-RPC response
     * @throws RpcException if the request fails or returns an error
     */
    JsonRpcResponse send(String method, List<?> params) throws RpcException;

    /**
     * Subscribes to real-time events.
     *
     * @param method   the subscription method (e.g., "eth_subscribe")
     * @param params   the subscription parameters (e.g., ["newHeads"])
     * @param callback the callback to invoke when a notification is received
     * @return the subscription ID
     * @throws RpcException                  if the subscription fails
     * @throws UnsupportedOperationException if the provider does not support
     *                                       subscriptions
     */
    default String subscribe(String method, List<?> params, java.util.function.Consumer<Object> callback)
            throws RpcException {
        throw new UnsupportedOperationException("This provider does not support subscriptions");
    }

    /**
     * Unsubscribes from a real-time event.
     *
     * @param subscriptionId the subscription ID
     * @return true if unsubscribed successfully
     * @throws RpcException                  if the unsubscription fails
     * @throws UnsupportedOperationException if the provider does not support
     *                                       subscriptions
     */
    default boolean unsubscribe(String subscriptionId) throws RpcException {
        throw new UnsupportedOperationException("This provider does not support subscriptions");
    }

    /**
     * Creates a default HTTP provider.
     *
     * @param url the JSON-RPC endpoint URL
     * @return a new BraneProvider instance
     */
    static BraneProvider http(final String url) {
        return HttpBraneProvider.builder(url).build();
    }

    /**
     * Closes this provider and releases any associated resources.
     * <p>
     * After calling this method, the provider should not be used for further requests.
     * The default implementation does nothing. Implementations that hold resources
     * (e.g., thread pools, connections) should override this method.
     */
    @Override
    default void close() {
        // Default no-op for providers that don't need cleanup
    }
}
