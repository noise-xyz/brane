package io.brane.rpc;

import java.time.Duration;

/**
 * Interface for collecting metrics from the Brane RPC layer.
 *
 * <p>
 * Implementations can integrate with popular metrics libraries like Micrometer,
 * Prometheus, or custom monitoring solutions. By default, a no-op
 * implementation
 * is used ({@link #noop()}).
 *
 * <p>
 * <strong>Usage:</strong>
 * 
 * <pre>{@code
 * BraneMetrics metrics = new MyMicrometerMetrics(meterRegistry);
 * WebSocketProvider provider = WebSocketProvider.create(config);
 * provider.setMetrics(metrics);
 * }</pre>
 *
 * <p>
 * <strong>Thread Safety:</strong> Implementations must be thread-safe as
 * methods
 * may be called from multiple threads concurrently.
 */
public interface BraneMetrics {

    /**
     * Called when a request is started.
     *
     * @param method the JSON-RPC method name (e.g., "eth_call")
     */
    default void onRequestStarted(String method) {
    }

    /**
     * Called when a request completes successfully.
     *
     * @param method  the JSON-RPC method name
     * @param latency the request latency
     */
    default void onRequestCompleted(String method, Duration latency) {
    }

    /**
     * Called when a request times out.
     *
     * @param method the JSON-RPC method name
     */
    default void onRequestTimeout(String method) {
    }

    /**
     * Called when a request fails with an error.
     *
     * @param method the JSON-RPC method name
     * @param error  the error that occurred
     */
    default void onRequestFailed(String method, Throwable error) {
    }

    /**
     * Called when a request is rejected due to backpressure
     * (too many in-flight requests).
     */
    default void onBackpressure() {
    }

    /**
     * Called when the WebSocket connection is lost.
     */
    default void onConnectionLost() {
    }

    /**
     * Called when the WebSocket connection is re-established.
     */
    default void onReconnect() {
    }

    /**
     * Called when a subscription notification is received.
     *
     * @param subscriptionId the subscription ID
     */
    default void onSubscriptionNotification(String subscriptionId) {
    }

    /**
     * Called when the Disruptor ring buffer is nearing saturation.
     *
     * <p>
     * This is triggered when the ring buffer's remaining capacity falls below
     * a threshold (e.g., 10% remaining). This is a leading indicator of potential
     * backpressure issues.
     *
     * @param remainingCapacity the number of slots remaining in the ring buffer
     * @param bufferSize        the total ring buffer size
     */
    default void onRingBufferSaturation(long remainingCapacity, int bufferSize) {
    }

    /**
     * Returns a no-op metrics implementation that does nothing.
     *
     * @return a no-op BraneMetrics instance
     */
    static BraneMetrics noop() {
        return NoopMetrics.INSTANCE;
    }
}

/**
 * Internal no-op implementation of BraneMetrics.
 */
enum NoopMetrics implements BraneMetrics {
    INSTANCE
}
