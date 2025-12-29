package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.brane.core.types.Address;
import java.math.BigInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MulticallBatchTest {

    private PublicClient publicClient;
    private MulticallBatch batch;
    private final Address contractAddress = new Address("0x" + "1".repeat(40));
    private final String abiJson = """
            [
                {
                    "type": "function",
                    "name": "balanceOf",
                    "stateMutability": "view",
                    "inputs": [{"name": "owner", "type": "address"}],
                    "outputs": [{"name": "", "type": "uint256"}]
                },
                {
                    "type": "function",
                    "name": "symbol",
                    "stateMutability": "view",
                    "inputs": [],
                    "outputs": [{"name": "", "type": "string"}]
                },
                {
                    "type": "function",
                    "name": "isPaused",
                    "stateMutability": "view",
                    "inputs": [],
                    "outputs": [{"name": "", "type": "bool"}]
                },
                {
                    "type": "function",
                    "name": "transfer",
                    "stateMutability": "nonpayable",
                    "inputs": [{"name": "to", "type": "address"}, {"name": "amount", "type": "uint256"}],
                    "outputs": []
                }
            ]
            """;

    interface TestContract {
        BigInteger balanceOf(Address owner);

        String symbol();

        boolean isPaused();

        void transfer(Address to, BigInteger amount);
    }

    @BeforeEach
    void setUp() {
        publicClient = mock(PublicClient.class);
        // Using the new API from SPEC.md
        batch = MulticallBatch.create(publicClient);
        when(publicClient.createBatch()).thenReturn(batch);
    }

    @Test
    void recordsCallsAndReturnsHandles() {
        TestContract proxy = batch.bind(TestContract.class, contractAddress, abiJson);
        Address owner = new Address("0x" + "2".repeat(40));

        // 1. Call proxy (records call)
        BigInteger recordedResult = proxy.balanceOf(owner);
        assertNull(recordedResult, "Recording proxy should return null for Object types");

        // 2. Add to batch (returns handle)
        BatchHandle<BigInteger> balanceHandle = batch.add(recordedResult);
        assertNotNull(balanceHandle);

        // 3. Repeat for another call
        String recordedSymbol = proxy.symbol();
        assertNull(recordedSymbol);
        BatchHandle<String> symbolHandle = batch.add(recordedSymbol);
        assertNotNull(symbolHandle);

        assertThrows(IllegalStateException.class, balanceHandle::result, "Result should not be available before execution");
    }

    @Test
    void returnsDefaultValuesForPrimitives() {
        TestContract proxy = batch.bind(TestContract.class, contractAddress, abiJson);

        boolean recordedBool = proxy.isPaused();
        assertFalse(recordedBool, "Recording proxy should return false for boolean primitive");

        BatchHandle<Boolean> handle = batch.add(recordedBool);
        assertNotNull(handle);
    }

    @Test
    void throwsOnStateChangingCall() {
        TestContract proxy = batch.bind(TestContract.class, contractAddress, abiJson);
        Address to = new Address("0x" + "2".repeat(40));

        assertThrows(IllegalArgumentException.class, () -> proxy.transfer(to, BigInteger.TEN),
                "Should throw when attempting to batch a non-view function");
    }

    @Test
    void throwsOnAddWithoutCall() {
        assertThrows(IllegalStateException.class, () -> batch.add(null),
                "Should throw if add() is called without a preceding proxy call");
    }

    @Test
    void handlesObjectMethodsOnProxy() {
        TestContract proxy = batch.bind(TestContract.class, contractAddress, abiJson);

        assertNotNull(proxy.toString());
        assertTrue(proxy.toString().contains("MulticallRecordingProxy"));
        assertEquals(proxy, proxy);
        assertNotEquals(proxy, new Object());
    }

    @Test
    void allowsSettingFailurePolicy() {
        assertNotNull(batch.allowFailure(true));
        assertNotNull(batch.allowFailure(false));
    }

    @Test
    void executesBatchAndUpdatesHandles() {
        TestContract proxy = batch.bind(TestContract.class, contractAddress, abiJson);
        Address owner = new Address("0x" + "2".repeat(40));

        BatchHandle<BigInteger> balanceHandle = batch.add(proxy.balanceOf(owner));

        // Mock RPC response for aggregate3: (bool success, bytes returnData)[]
        // Length 1, success=true, balance=100 (0x64)
        String mockResponseHex = "0x" +
                "0000000000000000000000000000000000000000000000000000000000000020" + // Offset to array
                "0000000000000000000000000000000000000000000000000000000000000001" + // Array length
                "0000000000000000000000000000000000000000000000000000000000000020" + // Tuple 1 offset (0x20+0x20=0x40)
                "0000000000000000000000000000000000000000000000000000000000000001" + // success=true
                "0000000000000000000000000000000000000000000000000000000000000040" + // offset to bytes (0x40+0x40=0x80)
                "0000000000000000000000000000000000000000000000000000000000000020" + // bytes length 32
                "0000000000000000000000000000000000000000000000000000000000000064"; // balance=100

        when(publicClient.call(any(), eq("latest"))).thenReturn(mockResponseHex);

        batch.execute();

        assertTrue(balanceHandle.result().success());
        assertEquals(BigInteger.valueOf(100), balanceHandle.result().data());
    }

    @Test
    void executesInChunks() {
        batch.chunkSize(1);
        TestContract proxy = batch.bind(TestContract.class, contractAddress, abiJson);

        BatchHandle<BigInteger> handle1 = batch.add(proxy.balanceOf(new Address("0x" + "2".repeat(40))));
        BatchHandle<BigInteger> handle2 = batch.add(proxy.balanceOf(new Address("0x" + "3".repeat(40))));

        // Mock response for single call chunk
        String mockResponseHex = "0x" +
                "0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000040" +
                "0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000064";

        when(publicClient.call(any(), eq("latest"))).thenReturn(mockResponseHex);

        batch.execute();

        // Verify publicClient.call was called twice (once per chunk)
        verify(publicClient, times(2)).call(any(), eq("latest"));

        assertTrue(handle1.result().success());
        assertTrue(handle2.result().success());
        assertEquals(BigInteger.valueOf(100), handle1.result().data());
        assertEquals(BigInteger.valueOf(100), handle2.result().data());
    }

    @Test
    void throwsOnExcessiveChunkSize() {
        assertThrows(IllegalArgumentException.class, () -> batch.chunkSize(1001),
                "Should throw if chunkSize exceeds 1000");
    }

    @Test
    void decodesRevertReasons() {
        TestContract proxy = batch.bind(TestContract.class, contractAddress, abiJson);

        BatchHandle<String> symbolHandle = batch.add(proxy.symbol());

        // Mock response for failed call with Error("Unauthorized")
        // Selector: 0x08c379a0
        // String offset: 32
        // String length: 12
        // String data: "Unauthorized"
        String revertData = "0x" +
                "08c379a0" +
                "0000000000000000000000000000000000000000000000000000000000000020" +
                "000000000000000000000000000000000000000000000000000000000000000c" +
                "556e617574686f72697a65640000000000000000000000000000000000000000";

        String mockResponseHex = "0x" +
                "0000000000000000000000000000000000000000000000000000000000000020" + // Offset to array
                "0000000000000000000000000000000000000000000000000000000000000001" + // Array length
                "0000000000000000000000000000000000000000000000000000000000000020" + // Tuple 1 offset (0x20+0x20=0x40)
                "0000000000000000000000000000000000000000000000000000000000000000" + // success=false
                "0000000000000000000000000000000000000000000000000000000000000040" + // offset to bytes (0x60+0x40=0xa0)
                "0000000000000000000000000000000000000000000000000000000000000064" + // bytes length 100 (0x64)
                revertData.substring(2); // data (already padded)

        when(publicClient.call(any(), eq("latest"))).thenReturn(mockResponseHex);

        batch.execute();

        assertFalse(symbolHandle.result().success());
        assertEquals("Unauthorized", symbolHandle.result().revertReason());
    }
}

