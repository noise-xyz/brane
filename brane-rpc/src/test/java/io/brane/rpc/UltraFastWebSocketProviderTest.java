package io.brane.rpc;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UltraFastWebSocketProviderTest {

    @Test
    void testProcessMessageFast_Primitive() {
        try (UltraFastWebSocketProvider provider = new UltraFastWebSocketProvider("ws://localhost:8545", false)) {
            long id = 1;
            int slot = (int) (id & 16383); // Hardcoded mask 16383 (16384 - 1)

            CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();
            provider.slots[slot] = future;

            UltraFastWebSocketProvider.UltraFastListener listener = provider.new UltraFastListener();
            String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x123\"}";

            listener.processMessageFast(json);

            assertTrue(future.isDone());
            JsonRpcResponse response = future.join();
            assertEquals("0x123", response.result());
            assertNull(response.error());
        }
    }

    @Test
    void testProcessMessageFast_Object() {
        try (UltraFastWebSocketProvider provider = new UltraFastWebSocketProvider("ws://localhost:8545", false)) {
            long id = 2;
            int slot = (int) (id & 16383);

            CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();
            provider.slots[slot] = future;

            UltraFastWebSocketProvider.UltraFastListener listener = provider.new UltraFastListener();
            String json = "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"foo\":\"bar\"}}";

            listener.processMessageFast(json);

            assertTrue(future.isDone());
            JsonRpcResponse response = future.join();
            assertTrue(response.result() instanceof Map);
            assertEquals("bar", ((Map<?, ?>) response.result()).get("foo"));
        }
    }

    @Test
    void testProcessMessageFast_Error() {
        try (UltraFastWebSocketProvider provider = new UltraFastWebSocketProvider("ws://localhost:8545", false)) {
            long id = 3;
            int slot = (int) (id & 16383);

            CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();
            provider.slots[slot] = future;

            UltraFastWebSocketProvider.UltraFastListener listener = provider.new UltraFastListener();
            String json = "{\"jsonrpc\":\"2.0\",\"id\":3,\"error\":{\"code\":-32600,\"message\":\"Invalid Request\"}}";

            listener.processMessageFast(json);

            assertTrue(future.isDone());
            JsonRpcResponse response = future.join();
            assertNull(response.result());
            assertNotNull(response.error());
            assertEquals(-32600, response.error().code());
            assertEquals("Invalid Request", response.error().message());
        }
    }

    @Test
    void testProcessMessageFast_Batch() {
        try (UltraFastWebSocketProvider provider = new UltraFastWebSocketProvider("ws://localhost:8545", false)) {
            long id1 = 4;
            long id2 = 5;
            int slot1 = (int) (id1 & 16383);
            int slot2 = (int) (id2 & 16383);

            CompletableFuture<JsonRpcResponse> f1 = new CompletableFuture<>();
            CompletableFuture<JsonRpcResponse> f2 = new CompletableFuture<>();
            provider.slots[slot1] = f1;
            provider.slots[slot2] = f2;

            UltraFastWebSocketProvider.UltraFastListener listener = provider.new UltraFastListener();
            String json = "[{\"jsonrpc\":\"2.0\",\"id\":4,\"result\":\"A\"},{\"jsonrpc\":\"2.0\",\"id\":5,\"result\":\"B\"}]";

            listener.processMessageFast(json);

            assertTrue(f1.isDone());
            assertTrue(f2.isDone());
            assertEquals("A", f1.join().result());
            assertEquals("B", f2.join().result());
        }
    }
}
