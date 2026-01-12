// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc;

/**
 * Represents an active subscription to a real-time event stream.
 *
 * <p>Subscriptions are created via {@link BraneProvider#subscribe(String, java.util.List,
 * java.util.function.Consumer)} and remain active until explicitly cancelled via
 * {@link #unsubscribe()} or the underlying connection is closed.
 *
 * <p><strong>Thread Safety:</strong> Implementations must be thread-safe. The subscription
 * ID is immutable after creation, and {@link #unsubscribe()} may be called from any thread.
 *
 * <p><strong>Lifecycle:</strong>
 * <ol>
 *   <li>Subscription is created and starts receiving events</li>
 *   <li>Events are delivered to the registered callback</li>
 *   <li>{@link #unsubscribe()} is called to stop receiving events</li>
 *   <li>No further events will be delivered after unsubscribe completes</li>
 * </ol>
 *
 * @see BraneProvider#subscribe(String, java.util.List, java.util.function.Consumer)
 */
public interface Subscription {
    /**
     * Returns the unique identifier for this subscription.
     *
     * <p>The subscription ID is assigned by the server and is used internally
     * to route incoming notifications to the correct callback. This ID is
     * immutable and remains constant for the lifetime of the subscription.
     *
     * @return the subscription ID, never {@code null}
     */
    String id();

    /**
     * Unsubscribes from the event stream, stopping further event delivery.
     *
     * <p><strong>Idempotency:</strong> This method is idempotent. Calling it multiple
     * times has no additional effect after the first successful call. Subsequent calls
     * will return silently without throwing exceptions.
     *
     * <p><strong>Error Handling:</strong> If the unsubscribe RPC fails (e.g., due to
     * network issues or the connection being already closed), the error is logged but
     * not thrown. The subscription is considered terminated regardless.
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe and may be called
     * from any thread, including the callback thread.
     *
     * <p><strong>Blocking Behavior:</strong> This method may block briefly while
     * sending the unsubscribe request to the server. It does not wait for confirmation
     * that all in-flight events have been processed.
     */
    void unsubscribe();
}
