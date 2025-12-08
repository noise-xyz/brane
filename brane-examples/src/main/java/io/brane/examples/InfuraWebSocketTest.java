package io.brane.examples;

import io.brane.core.model.BlockHeader;
import io.brane.rpc.PublicClient;
import io.brane.rpc.Subscription;
import io.brane.rpc.WebSocketProvider;

import java.math.BigInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class InfuraWebSocketTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Infura WebSocket Test (Base) ===");

        String wssUrl = System.getenv("INFURA_BASE_WSS_URL");
        if (wssUrl == null || wssUrl.isEmpty()) {
            System.err.println("Error: INFURA_BASE_WSS_URL environment variable not set.");
            System.exit(1);
        }

        System.out.println("Connecting to: " + wssUrl);

        try (WebSocketProvider provider = WebSocketProvider.create(wssUrl)) {
            PublicClient client = PublicClient.from(provider);

            // 1. Get Chain ID
            BigInteger chainId = client.getChainId();
            System.out.println("✅ Connected! Chain ID: " + chainId);

            // 2. Subscribe to New Heads
            System.out.println("Waiting for new blocks (timeout 60s)...");
            CountDownLatch latch = new CountDownLatch(1);

            Subscription sub = client.subscribeToNewHeads(header -> {
                System.out.println("✅ Received New Head: #" + header.number() + " (" + header.hash() + ")");
                latch.countDown();
            });

            if (latch.await(60, TimeUnit.SECONDS)) {
                System.out.println("Success! WebSocket subscription working.");
            } else {
                System.err.println("❌ Timeout waiting for new block.");
            }

            sub.unsubscribe();
        } catch (Exception e) {
            System.err.println("❌ Test Failed:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
