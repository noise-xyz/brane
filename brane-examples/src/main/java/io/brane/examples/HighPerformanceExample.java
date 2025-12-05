package io.brane.examples;

import io.brane.rpc.JsonRpcResponse;
import io.brane.rpc.NettyBraneProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstrates the high-performance capabilities of the NettyBraneProvider.
 * <p>
 * Features shown:
 * <ul>
 * <li>Async batch processing using {@code sendAsyncBatch}</li>
 * <li>Real-time subscriptions using {@code subscribe}</li>
 * <li>High-throughput request handling</li>
 * </ul>
 */
public class HighPerformanceExample {

    // Use a local node for best performance, or a public one for testing
    private static final String NODE_URL = System.getProperty("brane.node.url", "ws://127.0.0.1:8545");

    public static void main(String[] args) throws Exception {
        System.out.println("Connecting to " + NODE_URL + "...");

        try (NettyBraneProvider provider = NettyBraneProvider.create(NODE_URL)) {

            // 1. Warmup
            System.out.println("Warming up...");
            for (int i = 0; i < 100; i++) {
                provider.send("eth_blockNumber", Collections.emptyList());
            }

            // 2. Async Batch Processing
            // Fetch the latest 1000 blocks in parallel
            int batchSize = 1000;
            System.out.println("Sending " + batchSize + " async requests...");

            long start = System.nanoTime();
            List<CompletableFuture<JsonRpcResponse>> futures = new ArrayList<>(batchSize);

            // Get current block number first
            String hexBlock = (String) provider.send("eth_blockNumber", Collections.emptyList()).result();
            long currentBlock = Long.decode(hexBlock);

            for (int i = 0; i < batchSize; i++) {
                long blockNum = Math.max(0, currentBlock - i);
                String hexNum = "0x" + Long.toHexString(blockNum);

                // Use sendAsyncBatch for high throughput
                futures.add(provider.sendAsyncBatch("eth_getBlockByNumber", List.of(hexNum, false)));
            }

            // Wait for all to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            long duration = System.nanoTime() - start;

            double opsPerSec = (double) batchSize / (duration / 1_000_000_000.0);
            System.out.printf("Processed %d requests in %.2f ms (%.2f ops/s)%n",
                    batchSize, duration / 1_000_000.0, opsPerSec);

            // 3. Real-time Subscriptions
            System.out.println("\nSubscribing to new heads...");
            AtomicInteger notificationCount = new AtomicInteger(0);

            String subId = provider.subscribe("newHeads", Collections.emptyList(), result -> {
                System.out.println("Received notification: " + result);
                notificationCount.incrementAndGet();
            });

            System.out.println("Subscribed with ID: " + subId);
            System.out.println("Waiting for events (press Ctrl+C to stop)...");

            // Keep alive for a bit to receive events
            Thread.sleep(5000);

            provider.unsubscribe(subId);
            System.out.println("Unsubscribed. Received " + notificationCount.get() + " notifications.");
        }
    }
}
