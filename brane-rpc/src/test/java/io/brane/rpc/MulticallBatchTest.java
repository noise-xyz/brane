package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.brane.core.types.Address;

class MulticallBatchTest {

    private BraneProvider provider;
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
        provider = mock(BraneProvider.class);
        publicClient = new DefaultPublicClient(provider);
        // Using the official API to get our batch
        batch = publicClient.createBatch();
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
    void clearsThreadLocalOnOrphanedCallDetection() {
        TestContract proxy = batch.bind(TestContract.class, contractAddress, abiJson);
        Address owner = new Address("0x" + "2".repeat(40));

        // Step 1: Call proxy (sets pendingCall)
        proxy.balanceOf(owner);
        assertTrue(batch.hasPending(), "Should have pending call after proxy method");

        // Step 2: Call proxy AGAIN without add() - should throw and clear ThreadLocal
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> proxy.balanceOf(owner),
                "Should throw when there's an orphaned call");
        assertTrue(ex.getMessage().contains("already recorded"));

        // Step 3: Verify ThreadLocal was cleared BEFORE throwing
        assertFalse(batch.hasPending(), "ThreadLocal should be cleared after exception");
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

        when(provider.send(eq("eth_call"), any()))
                .thenReturn(new JsonRpcResponse("2.0", mockResponseHex, null, "1"));

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

        when(provider.send(eq("eth_call"), any()))
                .thenReturn(new JsonRpcResponse("2.0", mockResponseHex, null, "1"));

        batch.execute();

        // Verify provider.send was called twice (once per chunk)
        verify(provider, times(2)).send(eq("eth_call"), any());

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

        when(provider.send(eq("eth_call"), any()))
                .thenReturn(new JsonRpcResponse("2.0", mockResponseHex, null, "1"));

        batch.execute();

        assertFalse(symbolHandle.result().success());
        assertEquals("Unauthorized", symbolHandle.result().revertReason());
    }

    @Test
    void throwsOnReturnTypeMismatch() {
        interface MismatchedContract {
            String balanceOf(Address owner); // ABI says uint256 (BigInteger)
        }

        MismatchedContract proxy = batch.bind(MismatchedContract.class, contractAddress, abiJson);
        batch.add(proxy.balanceOf(new Address("0x" + "2".repeat(40))));

        // Mock response for uint256=100
        String mockResponseHex = "0x" +
                "0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000040" +
                "0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000064";

        when(provider.send(eq("eth_call"), any()))
                .thenReturn(new JsonRpcResponse("2.0", mockResponseHex, null, "1"));

        assertThrows(io.brane.core.error.AbiDecodingException.class, batch::execute);
    }

    @Test
    void throwsOnDoubleExecution() {
        TestContract proxy = batch.bind(TestContract.class, contractAddress, abiJson);
        batch.add(proxy.balanceOf(new Address("0x" + "2".repeat(40))));

        // Mock response for single call
        String mockResponseHex = "0x" +
                "0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000040" +
                "0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000064";

        when(provider.send(eq("eth_call"), any()))
                .thenReturn(new JsonRpcResponse("2.0", mockResponseHex, null, "1"));

        batch.execute(); // First execution should succeed

        IllegalStateException ex = assertThrows(IllegalStateException.class, batch::execute,
                "Should throw when attempting to execute a batch twice");
        assertTrue(ex.getMessage().contains("already been executed"));
    }

    @Test
    void emptyBatchExecutesWithoutError() {
        // Empty batch should not make any RPC calls and should not throw
        batch.execute();

        // Verify no RPC calls were made
        verify(provider, times(0)).send(any(), any());
    }

    @Test
    void acceptsMaximumChunkSize() {
        // Chunk size of 1000 (the maximum) should be accepted
        assertDoesNotThrow(() -> batch.chunkSize(1000));
    }

    @Test
    void handlesMixedSuccessAndFailureInBatch() {
        TestContract proxy = batch.bind(TestContract.class, contractAddress, abiJson);

        BatchHandle<BigInteger> successHandle = batch.add(proxy.balanceOf(new Address("0x" + "2".repeat(40))));
        BatchHandle<BigInteger> failureHandle = batch.add(proxy.balanceOf(new Address("0x" + "3".repeat(40))));

        // Mock response with 2 results: first succeeds (balance=100), second fails
        // Structure: (bool success, bytes returnData)[]
        // Offset to array (0x20), length (2), then offsets to each tuple, then tuple data
        String mockResponseHex = "0x" +
                "0000000000000000000000000000000000000000000000000000000000000020" + // offset to array
                "0000000000000000000000000000000000000000000000000000000000000002" + // array length = 2
                "0000000000000000000000000000000000000000000000000000000000000040" + // offset to tuple[0]
                "00000000000000000000000000000000000000000000000000000000000000c0" + // offset to tuple[1]
                // Tuple[0]: success=true, returnData encodes uint256=100
                "0000000000000000000000000000000000000000000000000000000000000001" + // success=true
                "0000000000000000000000000000000000000000000000000000000000000040" + // offset to bytes
                "0000000000000000000000000000000000000000000000000000000000000020" + // bytes length=32
                "0000000000000000000000000000000000000000000000000000000000000064" + // data=100
                // Tuple[1]: success=false, returnData is empty (simple failure)
                "0000000000000000000000000000000000000000000000000000000000000000" + // success=false
                "0000000000000000000000000000000000000000000000000000000000000040" + // offset to bytes
                "0000000000000000000000000000000000000000000000000000000000000000"; // bytes length=0 (empty)

        when(provider.send(eq("eth_call"), any()))
                .thenReturn(new JsonRpcResponse("2.0", mockResponseHex, null, "1"));

        batch.execute();

        // First call should succeed
        assertTrue(successHandle.result().success());
        assertEquals(BigInteger.valueOf(100), successHandle.result().data());
        assertNull(successHandle.result().revertReason());

        // Second call should fail
        assertFalse(failureHandle.result().success());
        assertNull(failureHandle.result().data());
        assertNotNull(failureHandle.result().revertReason());
    }

    // ========== Type Variation Tests ==========

    @Test
    void decodesStringReturnType() {
        TestContract proxy = batch.bind(TestContract.class, contractAddress, abiJson);

        BatchHandle<String> symbolHandle = batch.add(proxy.symbol());

        // Mock response for string "USDC"
        // String encoding: offset (0x20), length (4), data ("USDC" = 0x55534443)
        String stringData =
                "0000000000000000000000000000000000000000000000000000000000000020" + // offset to string
                "0000000000000000000000000000000000000000000000000000000000000004" + // length = 4
                "5553444300000000000000000000000000000000000000000000000000000000"; // "USDC"

        String mockResponseHex = "0x" +
                "0000000000000000000000000000000000000000000000000000000000000020" + // offset to array
                "0000000000000000000000000000000000000000000000000000000000000001" + // array length = 1
                "0000000000000000000000000000000000000000000000000000000000000020" + // offset to tuple[0]
                "0000000000000000000000000000000000000000000000000000000000000001" + // success=true
                "0000000000000000000000000000000000000000000000000000000000000040" + // offset to bytes
                "0000000000000000000000000000000000000000000000000000000000000060" + // bytes length = 96 (3 words)
                stringData;

        when(provider.send(eq("eth_call"), any()))
                .thenReturn(new JsonRpcResponse("2.0", mockResponseHex, null, "1"));

        batch.execute();

        assertTrue(symbolHandle.result().success());
        assertEquals("USDC", symbolHandle.result().data());
    }

    @Test
    void decodesArrayReturnType() {
        // ABI for function that returns uint256[]
        String arrayAbiJson = """
                [{
                    "type": "function",
                    "name": "getBalances",
                    "stateMutability": "view",
                    "inputs": [],
                    "outputs": [{"name": "", "type": "uint256[]"}]
                }]
                """;

        interface ArrayContract {
            List<BigInteger> getBalances();
        }

        ArrayContract proxy = batch.bind(ArrayContract.class, contractAddress, arrayAbiJson);
        BatchHandle<List<BigInteger>> handle = batch.add(proxy.getBalances());

        // Mock response for uint256[] with values [100, 200, 300]
        // Array encoding: offset (0x20), length (3), values
        String arrayData =
                "0000000000000000000000000000000000000000000000000000000000000020" + // offset to array
                "0000000000000000000000000000000000000000000000000000000000000003" + // length = 3
                "0000000000000000000000000000000000000000000000000000000000000064" + // 100
                "00000000000000000000000000000000000000000000000000000000000000c8" + // 200
                "000000000000000000000000000000000000000000000000000000000000012c"; // 300

        String mockResponseHex = "0x" +
                "0000000000000000000000000000000000000000000000000000000000000020" + // offset to result array
                "0000000000000000000000000000000000000000000000000000000000000001" + // result array length = 1
                "0000000000000000000000000000000000000000000000000000000000000020" + // offset to tuple[0]
                "0000000000000000000000000000000000000000000000000000000000000001" + // success=true
                "0000000000000000000000000000000000000000000000000000000000000040" + // offset to bytes
                "00000000000000000000000000000000000000000000000000000000000000a0" + // bytes length = 160 (5 words)
                arrayData;

        when(provider.send(eq("eth_call"), any()))
                .thenReturn(new JsonRpcResponse("2.0", mockResponseHex, null, "1"));

        batch.execute();

        assertTrue(handle.result().success());
        List<BigInteger> balances = handle.result().data();
        assertEquals(3, balances.size());
        assertEquals(BigInteger.valueOf(100), balances.get(0));
        assertEquals(BigInteger.valueOf(200), balances.get(1));
        assertEquals(BigInteger.valueOf(300), balances.get(2));
    }

    @Test
    void decodesTupleReturnType() {
        // ABI for function that returns multiple values (treated as tuple)
        // The current implementation returns List<Object> for multi-value returns
        String tupleAbiJson = """
                [{
                    "type": "function",
                    "name": "getTokenInfo",
                    "stateMutability": "view",
                    "inputs": [],
                    "outputs": [
                        {"name": "name", "type": "string"},
                        {"name": "decimals", "type": "uint8"}
                    ]
                }]
                """;

        interface TupleContract {
            List<Object> getTokenInfo();
        }

        TupleContract proxy = batch.bind(TupleContract.class, contractAddress, tupleAbiJson);
        BatchHandle<List<Object>> handle = batch.add(proxy.getTokenInfo());

        // Mock response for tuple (name="Test", decimals=18)
        // Multi-value encoding: string offset, uint8, then string data
        String tupleData =
                "0000000000000000000000000000000000000000000000000000000000000040" + // offset to string "name"
                "0000000000000000000000000000000000000000000000000000000000000012" + // decimals = 18
                "0000000000000000000000000000000000000000000000000000000000000004" + // string length = 4
                "5465737400000000000000000000000000000000000000000000000000000000"; // "Test"

        String mockResponseHex = "0x" +
                "0000000000000000000000000000000000000000000000000000000000000020" + // offset to result array
                "0000000000000000000000000000000000000000000000000000000000000001" + // result array length = 1
                "0000000000000000000000000000000000000000000000000000000000000020" + // offset to tuple[0]
                "0000000000000000000000000000000000000000000000000000000000000001" + // success=true
                "0000000000000000000000000000000000000000000000000000000000000040" + // offset to bytes
                "0000000000000000000000000000000000000000000000000000000000000080" + // bytes length = 128 (4 words)
                tupleData;

        when(provider.send(eq("eth_call"), any()))
                .thenReturn(new JsonRpcResponse("2.0", mockResponseHex, null, "1"));

        batch.execute();

        assertTrue(handle.result().success());
        List<Object> info = handle.result().data();
        assertNotNull(info);
        assertEquals(2, info.size());
        assertEquals("Test", info.get(0));
        assertEquals(BigInteger.valueOf(18), info.get(1));
    }

    // ========== Error Condition Tests ==========

    @Test
    void throwsOnNetworkFailure() {
        TestContract proxy = batch.bind(TestContract.class, contractAddress, abiJson);
        batch.add(proxy.balanceOf(new Address("0x" + "2".repeat(40))));

        // Simulate network failure by throwing RuntimeException
        when(provider.send(eq("eth_call"), any()))
                .thenThrow(new RuntimeException("Connection refused"));

        RuntimeException ex = assertThrows(RuntimeException.class, batch::execute);
        assertTrue(ex.getMessage().contains("Connection refused"));
    }

    @Test
    void throwsOnRpcError() {
        TestContract proxy = batch.bind(TestContract.class, contractAddress, abiJson);
        batch.add(proxy.balanceOf(new Address("0x" + "2".repeat(40))));

        // Simulate RPC error (e.g., Multicall3 contract not deployed)
        JsonRpcError error = new JsonRpcError(-32000, "execution reverted", null);
        when(provider.send(eq("eth_call"), any()))
                .thenReturn(new JsonRpcResponse("2.0", null, error, "1"));

        io.brane.core.error.RpcException ex = assertThrows(
                io.brane.core.error.RpcException.class, batch::execute);
        assertTrue(ex.getMessage().contains("execution reverted"));
    }

    @Test
    void throwsOnEmptyMulticallResponse() {
        TestContract proxy = batch.bind(TestContract.class, contractAddress, abiJson);
        batch.add(proxy.balanceOf(new Address("0x" + "2".repeat(40))));

        // Simulate empty response (contract not deployed returns 0x)
        when(provider.send(eq("eth_call"), any()))
                .thenReturn(new JsonRpcResponse("2.0", "0x", null, "1"));

        io.brane.core.error.AbiDecodingException ex = assertThrows(
                io.brane.core.error.AbiDecodingException.class, batch::execute);
        assertTrue(ex.getMessage().contains("empty data") || ex.getMessage().contains("not deployed"));
    }

    @Test
    void throwsOnInvalidAbiJson() {
        String invalidAbi = "not valid json";

        assertThrows(RuntimeException.class,
                () -> batch.bind(TestContract.class, contractAddress, invalidAbi),
                "Should throw when ABI JSON is malformed");
    }

    @Test
    void throwsOnEmptyAbi() {
        String emptyAbi = "[]";

        // Should throw when binding an interface to an ABI with no matching functions
        assertThrows(IllegalArgumentException.class,
                () -> batch.bind(TestContract.class, contractAddress, emptyAbi),
                "Should throw when ABI has no matching functions for interface");
    }
}
