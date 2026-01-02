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
 *
 * @since 0.2.0
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
     * @deprecated Use {@link #onRequestTimeout(String, long)} instead for better debugging context
     */
    @Deprecated(since = "0.5.0", forRemoval = true)
    default void onRequestTimeout(String method) {
    }

    /**
     * Called when a request times out.
     *
     * <p>This method provides the request ID for correlation with logs and other
     * metrics. Use this to track which specific requests are timing out and correlate
     * with server-side logs.
     *
     * @param method    the JSON-RPC method name (e.g., "eth_call")
     * @param requestId the unique request ID for correlation with logs
     * @since 0.5.0
     */
    @SuppressWarnings("deprecation")
    default void onRequestTimeout(String method, long requestId) {
        // Default implementation calls legacy method for backward compatibility
        onRequestTimeout(method);
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
     *
     * @deprecated Use {@link #onBackpressure(int, int)} instead for better debugging context
     */
    @Deprecated(since = "0.5.0", forRemoval = true)
    default void onBackpressure() {
    }

    /**
     * Called when a request is rejected due to backpressure.
     *
     * <p>This method provides context about the current queue state to help
     * diagnose backpressure issues.
     *
     * @param pendingCount       the current number of pending requests when backpressure triggered
     * @param maxPendingRequests the maximum number of pending requests allowed
     * @since 0.5.0
     */
    @SuppressWarnings("deprecation")
    default void onBackpressure(int pendingCount, int maxPendingRequests) {
        // Default implementation calls legacy method for backward compatibility
        onBackpressure();
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
     * Called when an orphaned response is received.
     *
     * <p>An orphaned response is a response received with no matching pending request.
     * This typically occurs when:
     * <ul>
     *   <li>The response ID cannot be parsed</li>
     *   <li>The request timed out before the response arrived</li>
     *   <li>The request was cancelled</li>
     * </ul>
     *
     * <p>High orphan counts may indicate network issues, server-side delays, or
     * timeout values that are too aggressive.
     *
     * @param reason a description of why the response was orphaned (e.g., "no pending request",
     *               "unparseable ID")
     * @since 0.5.0
     */
    default void onOrphanedResponse(String reason) {
    }

    /**
     * Called when a subscription callback throws an exception.
     *
     * <p>This is triggered when user-provided subscription listeners throw an exception
     * during notification processing. The exception is caught to prevent it from affecting
     * other subscriptions or the WebSocket connection.
     *
     * <p>Implementations should use this to:
     * <ul>
     *   <li>Track error rates per subscription</li>
     *   <li>Alert on misbehaving callbacks</li>
     *   <li>Debug callback issues in production</li>
     * </ul>
     *
     * @param subscriptionId the subscription ID that encountered the error
     * @param error          the exception thrown by the callback
     * @since 0.5.0
     */
    default void onSubscriptionCallbackError(String subscriptionId, Throwable error) {
    }

    /**
     * Returns a no-op metrics implementation that does nothing.
     *
     * @return a no-op BraneMetrics instance
     */
    static BraneMetrics noop() {
        return Noop.INSTANCE;
    }

    /**
     * Private no-op implementation of BraneMetrics.
     */
    final class Noop implements BraneMetrics {
        static final Noop INSTANCE = new Noop();

        private Noop() {
        }
    }
}
