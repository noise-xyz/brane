package io.brane.rpc;

/**
 * Represents an active subscription to a real-time event stream.
 */
public interface Subscription {
    /**
     * Returns the unique identifier for this subscription.
     *
     * @return the subscription ID
     */
    String id();

    /**
     * Unsubscribes from the event stream.
     */
    void unsubscribe();
}
