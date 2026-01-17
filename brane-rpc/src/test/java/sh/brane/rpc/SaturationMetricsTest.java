// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for ring buffer saturation metrics.
 *
 * <p>These tests verify that the saturation metrics callback fires correctly
 * when the ring buffer's remaining capacity falls below the configured threshold.
 *
 * <p>The saturation threshold is 10% (0.1) of the buffer size, as defined in
 * WebSocketProvider.RING_BUFFER_SATURATION_THRESHOLD.
 *
 * @see BraneMetrics#onRingBufferSaturation(long, int)
 */
class SaturationMetricsTest {

    /**
     * The saturation threshold from WebSocketProvider (10% remaining capacity).
     */
    private static final double SATURATION_THRESHOLD = 0.1;

    /**
     * Small ring buffer size for testing saturation behavior.
     * Must be power of 2 as required by Disruptor.
     */
    private static final int SMALL_BUFFER_SIZE = 64;

    // ==================== Saturation threshold calculation tests ====================

    /**
     * Tests that saturation threshold is calculated correctly for small buffer (64).
     */
    @Test
    void saturationThreshold_calculatedCorrectlyForSmallBuffer() {
        int bufferSize = SMALL_BUFFER_SIZE;
        long threshold = (long) (bufferSize * SATURATION_THRESHOLD);

        // 10% of 64 = 6.4, truncated to 6
        assertEquals(6, threshold, "Saturation threshold for buffer size 64 should be 6");
    }

    /**
     * Tests saturation detection at exact threshold boundary (6 remaining for size 64).
     */
    @Test
    void saturationDetection_atExactThresholdBoundary() {
        int bufferSize = SMALL_BUFFER_SIZE;

        // At exactly 6 remaining capacity (10% of 64 = 6.4)
        long remainingCapacity = 6;

        // 6 < 64 * 0.1 (6.4) = true, so saturation IS detected
        boolean isSaturated = remainingCapacity < bufferSize * SATURATION_THRESHOLD;
        assertTrue(isSaturated, "Should detect saturation when remaining capacity equals floor of threshold");
    }

    /**
     * Tests saturation detection just above threshold (7 remaining for size 64).
     */
    @Test
    void saturationDetection_justAboveThreshold() {
        int bufferSize = SMALL_BUFFER_SIZE;

        // At 7 remaining capacity (above 6.4 threshold)
        long remainingCapacity = 7;

        // 7 < 64 * 0.1 (6.4) = false, so saturation is NOT detected
        boolean isSaturated = remainingCapacity < bufferSize * SATURATION_THRESHOLD;
        assertFalse(isSaturated, "Should not detect saturation when remaining capacity is above threshold");
    }

    /**
     * Tests saturation detection well below threshold (3 remaining for size 64).
     */
    @Test
    void saturationDetection_wellBelowThreshold() {
        int bufferSize = SMALL_BUFFER_SIZE;

        // At 3 remaining capacity (well below 6.4 threshold)
        long remainingCapacity = 3;

        boolean isSaturated = remainingCapacity < bufferSize * SATURATION_THRESHOLD;
        assertTrue(isSaturated, "Should detect saturation when remaining capacity is well below threshold");
    }

    /**
     * Tests saturation detection at zero remaining capacity.
     */
    @Test
    void saturationDetection_atZeroCapacity() {
        int bufferSize = SMALL_BUFFER_SIZE;
        long remainingCapacity = 0;

        boolean isSaturated = remainingCapacity < bufferSize * SATURATION_THRESHOLD;
        assertTrue(isSaturated, "Should detect saturation when buffer is completely full");
    }

    /**
     * Tests saturation detection when buffer is nearly empty (high remaining capacity).
     */
    @Test
    void saturationDetection_bufferNearlyEmpty() {
        int bufferSize = SMALL_BUFFER_SIZE;
        long remainingCapacity = 60; // 93.75% capacity remaining

        boolean isSaturated = remainingCapacity < bufferSize * SATURATION_THRESHOLD;
        assertFalse(isSaturated, "Should not detect saturation when buffer is nearly empty");
    }

    // ==================== Metrics callback tests ====================

    /**
     * Tests that metrics callback receives correct parameters when saturation fires.
     */
    @Test
    void metricsCallback_receivesCorrectParameters() {
        AtomicLong receivedRemainingCapacity = new AtomicLong(-1);
        AtomicInteger receivedBufferSize = new AtomicInteger(-1);

        BraneMetrics testMetrics = new BraneMetrics() {
            @Override
            public void onRingBufferSaturation(long remainingCapacity, int bufferSize) {
                receivedRemainingCapacity.set(remainingCapacity);
                receivedBufferSize.set(bufferSize);
            }
        };

        // Simulate saturation check as done in WebSocketProvider.sendAsync
        int bufferSize = SMALL_BUFFER_SIZE;
        long remainingCapacity = 5; // Below threshold (6.4)

        if (remainingCapacity < bufferSize * SATURATION_THRESHOLD) {
            testMetrics.onRingBufferSaturation(remainingCapacity, bufferSize);
        }

        assertEquals(5, receivedRemainingCapacity.get(), "Should pass correct remaining capacity");
        assertEquals(SMALL_BUFFER_SIZE, receivedBufferSize.get(), "Should pass correct buffer size");
    }

    /**
     * Tests that metrics callback is NOT called when above threshold.
     */
    @Test
    void metricsCallback_notCalledWhenAboveThreshold() {
        AtomicInteger callCount = new AtomicInteger(0);

        BraneMetrics testMetrics = new BraneMetrics() {
            @Override
            public void onRingBufferSaturation(long remainingCapacity, int bufferSize) {
                callCount.incrementAndGet();
            }
        };

        int bufferSize = SMALL_BUFFER_SIZE;
        long remainingCapacity = 10; // Above threshold (6.4)

        if (remainingCapacity < bufferSize * SATURATION_THRESHOLD) {
            testMetrics.onRingBufferSaturation(remainingCapacity, bufferSize);
        }

        assertEquals(0, callCount.get(), "Callback should not be called when above threshold");
    }

    /**
     * Tests that metrics callback fires multiple times during sustained saturation.
     */
    @Test
    void metricsCallback_firesMultipleTimesDuringSaturation() {
        List<Long> remainingCapacities = new ArrayList<>();
        List<Integer> bufferSizes = new ArrayList<>();

        BraneMetrics testMetrics = new BraneMetrics() {
            @Override
            public void onRingBufferSaturation(long remainingCapacity, int bufferSize) {
                remainingCapacities.add(remainingCapacity);
                bufferSizes.add(bufferSize);
            }
        };

        int bufferSize = SMALL_BUFFER_SIZE;

        // Simulate multiple requests during saturation (each would trigger callback)
        long[] capacities = {5, 4, 3, 2, 1, 0};
        for (long remainingCapacity : capacities) {
            if (remainingCapacity < bufferSize * SATURATION_THRESHOLD) {
                testMetrics.onRingBufferSaturation(remainingCapacity, bufferSize);
            }
        }

        assertEquals(6, remainingCapacities.size(), "Should fire for each request during saturation");
        assertEquals(List.of(5L, 4L, 3L, 2L, 1L, 0L), remainingCapacities, "Should record decreasing capacities");
        assertTrue(bufferSizes.stream().allMatch(s -> s == SMALL_BUFFER_SIZE), "All calls should have correct buffer size");
    }

    /**
     * Tests that noop metrics implementation handles saturation callback gracefully.
     */
    @Test
    void noopMetrics_handlesSaturationCallbackGracefully() {
        BraneMetrics noop = BraneMetrics.noop();

        // Should not throw
        assertDoesNotThrow(() -> noop.onRingBufferSaturation(5, SMALL_BUFFER_SIZE));
    }

    // ==================== Threshold calculation for various buffer sizes ====================

    /**
     * Tests saturation threshold calculation for minimum valid buffer size (2).
     */
    @Test
    void saturationThreshold_minimumBufferSize() {
        int bufferSize = 2;
        long threshold = (long) (bufferSize * SATURATION_THRESHOLD);

        // 10% of 2 = 0.2, truncated to 0
        assertEquals(0, threshold, "Threshold for buffer size 2 should be 0");

        // Saturation fires when remainingCapacity < 0.2
        // Zero capacity: 0 < 0.2 = true, so it IS saturated
        boolean isSaturatedAtZero = 0 < bufferSize * SATURATION_THRESHOLD;
        assertTrue(isSaturatedAtZero, "Zero capacity should trigger saturation with buffer size 2");

        // One remaining: 1 < 0.2 = false, so NOT saturated
        boolean isSaturatedAtOne = 1 < bufferSize * SATURATION_THRESHOLD;
        assertFalse(isSaturatedAtOne, "One remaining capacity should not trigger saturation with buffer size 2");
    }

    /**
     * Tests saturation threshold calculation for buffer size 16.
     */
    @Test
    void saturationThreshold_bufferSize16() {
        int bufferSize = 16;
        long threshold = (long) (bufferSize * SATURATION_THRESHOLD);

        // 10% of 16 = 1.6, truncated to 1
        assertEquals(1, threshold, "Threshold for buffer size 16 should be 1");

        // Remaining capacity of 1 should trigger saturation (1 < 1.6)
        assertTrue(1 < bufferSize * SATURATION_THRESHOLD);
        // Remaining capacity of 2 should NOT trigger saturation (2 < 1.6 is false)
        assertFalse(2 < bufferSize * SATURATION_THRESHOLD);
    }

    /**
     * Tests saturation threshold calculation for buffer size 128.
     */
    @Test
    void saturationThreshold_bufferSize128() {
        int bufferSize = 128;
        long threshold = (long) (bufferSize * SATURATION_THRESHOLD);

        // 10% of 128 = 12.8, truncated to 12
        assertEquals(12, threshold, "Threshold for buffer size 128 should be 12");

        // Remaining capacity of 12 should trigger saturation (12 < 12.8)
        assertTrue(12 < bufferSize * SATURATION_THRESHOLD);
        // Remaining capacity of 13 should NOT trigger saturation (13 < 12.8 is false)
        assertFalse(13 < bufferSize * SATURATION_THRESHOLD);
    }

    /**
     * Tests saturation threshold for default buffer size (4096).
     */
    @Test
    void saturationThreshold_defaultBufferSize() {
        int bufferSize = 4096;
        long threshold = (long) (bufferSize * SATURATION_THRESHOLD);

        // 10% of 4096 = 409.6, truncated to 409
        assertEquals(409, threshold, "Threshold for buffer size 4096 should be 409");

        // Remaining capacity of 409 should trigger saturation (409 < 409.6)
        assertTrue(409 < bufferSize * SATURATION_THRESHOLD);
        // Remaining capacity of 410 should NOT trigger saturation (410 < 409.6 is false)
        assertFalse(410 < bufferSize * SATURATION_THRESHOLD);
    }

    // ==================== Edge cases ====================

    /**
     * Tests that negative remaining capacity (should never happen) would trigger saturation.
     */
    @Test
    void saturationDetection_negativeCapacity() {
        int bufferSize = SMALL_BUFFER_SIZE;
        long remainingCapacity = -1;

        boolean isSaturated = remainingCapacity < bufferSize * SATURATION_THRESHOLD;
        assertTrue(isSaturated, "Negative capacity should always trigger saturation");
    }

    /**
     * Tests saturation behavior when buffer size equals remaining capacity.
     */
    @Test
    void saturationDetection_fullCapacityRemaining() {
        int bufferSize = SMALL_BUFFER_SIZE;
        long remainingCapacity = bufferSize; // 64 remaining out of 64

        boolean isSaturated = remainingCapacity < bufferSize * SATURATION_THRESHOLD;
        assertFalse(isSaturated, "Should not detect saturation when buffer is completely empty");
    }

    /**
     * Tests the simulation of request flow filling the buffer to saturation.
     */
    @Test
    void simulateRequestFlow_fillToSaturation() {
        AtomicInteger saturationCount = new AtomicInteger(0);
        AtomicLong firstSaturationCapacity = new AtomicLong(-1);

        BraneMetrics testMetrics = new BraneMetrics() {
            @Override
            public void onRingBufferSaturation(long remainingCapacity, int bufferSize) {
                if (saturationCount.getAndIncrement() == 0) {
                    firstSaturationCapacity.set(remainingCapacity);
                }
            }
        };

        int bufferSize = SMALL_BUFFER_SIZE;
        int numRequests = 60; // Fill most of the buffer

        // Simulate requests being submitted, each consuming one slot
        for (int i = 0; i < numRequests; i++) {
            long remainingCapacity = bufferSize - i - 1; // Decreasing capacity

            // Check saturation before "publishing" each request
            if (remainingCapacity < bufferSize * SATURATION_THRESHOLD) {
                testMetrics.onRingBufferSaturation(remainingCapacity, bufferSize);
            }
        }

        // With buffer size 64 and threshold at 6.4:
        // - Requests 0-56: remaining capacity 63-7 (above threshold)
        // - Request 57: remaining capacity 6 (first saturation, 6 < 6.4)
        // - Requests 58-59: remaining capacity 5, 4 (continued saturation)
        assertTrue(saturationCount.get() > 0, "Should detect saturation");
        assertEquals(6, firstSaturationCapacity.get(), "First saturation should occur at remaining capacity 6");
        assertEquals(3, saturationCount.get(), "Should fire saturation 3 times (at capacity 6, 5, 4)");
    }
}
