package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.brane.core.chain.ChainProfile;
import io.brane.core.model.AccessListEntry;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.Wei;

class SmartGasStrategyTest {

    private final PublicClient unusedPublicClient = new NoopPublicClient();
    private final BraneProvider unusedProvider = (method, params) -> null;
    private final ChainProfile profile = ChainProfile.of(1L, null, true, Wei.of(1_000_000_000L));
    private final SmartGasStrategy strategy = new SmartGasStrategy(unusedPublicClient, unusedProvider, profile);

    // ========== EIP-1559 Fee Tests ==========

    @Test
    void eip1559FeeCalculationWithBaseFee() {
        final Wei mockBaseFee = Wei.of(30_000_000_000L); // 30 Gwei
        final Wei defaultPriority = Wei.of(1_000_000_000L); // 1 Gwei (from profile)

        final BraneProvider mockProvider = (method, params) -> {
            if ("eth_estimateGas".equals(method)) {
                return new JsonRpcResponse("2.0", "0x5208", null, "1"); // 21000 in hex
            }
            return null;
        };

        final PublicClient mockClient = createMockPublicClient(
                new io.brane.core.model.BlockHeader(
                        new Hash("0x" + "0".repeat(64)), 1L, new Hash("0x" + "1".repeat(64)),
                        System.currentTimeMillis() / 1000, mockBaseFee));

        final SmartGasStrategy testStrategy = new SmartGasStrategy(mockClient, mockProvider, profile);

        final TransactionRequest request = new TransactionRequest(
                new Address("0x" + "f".repeat(40)),
                new Address("0x" + "e".repeat(40)),
                null, null, null, null, null, 1L, null, true, null);

        final SmartGasStrategy.GasFilledRequest result = testStrategy.applyDefaults(request, request.from());
        final TransactionRequest filled = result.request();

        // Verify EIP-1559 fees were calculated
        assertTrue(filled.isEip1559());
        assertNotNull(filled.maxPriorityFeePerGas());
        assertNotNull(filled.maxFeePerGas());
        assertFalse(result.fellBackToLegacy()); // No fallback expected

        // maxPriorityFee should be chain default (1 Gwei)
        assertEquals(defaultPriority.value(), filled.maxPriorityFeePerGas().value());

        // maxFee should be (baseFee * 2) + priority = (30 * 2) + 1 = 61 Gwei
        java.math.BigInteger expectedMaxFee = mockBaseFee.value()
                .multiply(java.math.BigInteger.valueOf(2))
                .add(defaultPriority.value());
        assertEquals(expectedMaxFee, filled.maxFeePerGas().value());
    }

    @Test
    void fallsBackToLegacyWhenBaseFeeNotAvailable() {
        final Wei mockGasPrice = Wei.of(20_000_000_000L); // 20 Gwei

        final BraneProvider mockProvider = (method, params) -> {
            if ("eth_estimateGas".equals(method)) {
                return new JsonRpcResponse("2.0", "0x5208", null, "1");
            } else if ("eth_gasPrice".equals(method)) {
                return new JsonRpcResponse("2.0", "0x4a817c800", null, "1"); // 20 Gwei
            }
            return null;
        };

        // Block WITHOUT baseFeePerGas (pre-London fork)
        final PublicClient mockClient = createMockPublicClient(
                new io.brane.core.model.BlockHeader(
                        new Hash("0x" + "0".repeat(64)), 1L, new Hash("0x" + "1".repeat(64)),
                        System.currentTimeMillis() / 1000, null));

        final SmartGasStrategy testStrategy = new SmartGasStrategy(mockClient, mockProvider, profile);

        // Request EIP-1559 but should fall back to legacy
        final TransactionRequest request = new TransactionRequest(
                new Address("0x" + "f".repeat(40)),
                new Address("0x" + "e".repeat(40)),
                null, null, null, null, null, 1L, null, true, null);

        final SmartGasStrategy.GasFilledRequest result = testStrategy.applyDefaults(request, request.from());
        final TransactionRequest filled = result.request();

        // Should have fallen back to legacy
        assertFalse(filled.isEip1559());
        assertNotNull(filled.gasPrice());
        assertNull(filled.maxFeePerGas());
        assertNull(filled.maxPriorityFeePerGas());
        assertEquals(mockGasPrice.value(), filled.gasPrice().value());

        // Verify fallback metadata is correct
        assertTrue(result.fellBackToLegacy(), "Should report fallback to legacy");
        assertTrue(result.requestedEip1559(), "Should record that EIP-1559 was requested");
        assertFalse(result.actualEip1559(), "Should record that actual tx is legacy");
    }

    @Test
    void fallsBackToLegacyWhenLatestBlockIsNull() {
        final BraneProvider mockProvider = (method, params) -> {
            if ("eth_estimateGas".equals(method)) {
                return new JsonRpcResponse("2.0", "0x5208", null, "1");
            } else if ("eth_gasPrice".equals(method)) {
                return new JsonRpcResponse("2.0", "0x37e11d600", null, "1"); // 15 Gwei
            }
            return null;
        };

        // Null block simulates node not responding
        final PublicClient mockClient = createMockPublicClient(null);

        final SmartGasStrategy testStrategy = new SmartGasStrategy(mockClient, mockProvider, profile);

        final TransactionRequest request = new TransactionRequest(
                new Address("0x" + "f".repeat(40)),
                new Address("0x" + "e".repeat(40)),
                null, null, null, null, null, 1L, null, true, null);

        final SmartGasStrategy.GasFilledRequest result = testStrategy.applyDefaults(request, request.from());
        final TransactionRequest filled = result.request();

        // Should have fallen back to legacy
        assertFalse(filled.isEip1559());
        assertNotNull(filled.gasPrice());

        // Verify fallback metadata is correct
        assertTrue(result.fellBackToLegacy(), "Should report fallback to legacy");
    }

    @Test
    void preservesUserProvidedEip1559Fees() {
        final Wei userMaxFee = Wei.of(100_000_000_000L); // 100 Gwei
        final Wei userPriorityFee = Wei.of(5_000_000_000L); // 5 Gwei

        final BraneProvider mockProvider = (method, params) -> {
            if ("eth_estimateGas".equals(method)) {
                return new JsonRpcResponse("2.0", "0x5208", null, "1");
            }
            return null;
        };

        final PublicClient mockClient = createMockPublicClient(
                new io.brane.core.model.BlockHeader(
                        new Hash("0x" + "0".repeat(64)), 1L, new Hash("0x" + "1".repeat(64)),
                        System.currentTimeMillis() / 1000, Wei.of(30_000_000_000L)));

        final SmartGasStrategy testStrategy = new SmartGasStrategy(mockClient, mockProvider, profile);

        // User provides their own fees
        final TransactionRequest request = new TransactionRequest(
                new Address("0x" + "f".repeat(40)),
                new Address("0x" + "e".repeat(40)),
                null, null, null, userPriorityFee, userMaxFee, 1L, null, true, null);

        final SmartGasStrategy.GasFilledRequest result = testStrategy.applyDefaults(request, request.from());
        final TransactionRequest filled = result.request();

        // User fees should NOT be overridden
        assertEquals(userMaxFee.value(), filled.maxFeePerGas().value());
        assertEquals(userPriorityFee.value(), filled.maxPriorityFeePerGas().value());
        assertFalse(result.fellBackToLegacy()); // No fallback when user provides fees
    }

    @Test
    void preservesUserProvidedGasLimit() {
        final long userGasLimit = 100_000L;

        final BraneProvider mockProvider = (method, params) -> {
            // Should NOT call eth_estimateGas when user provides gas limit
            if ("eth_estimateGas".equals(method)) {
                throw new AssertionError("Should not call eth_estimateGas when gasLimit is provided");
            }
            return null;
        };

        final PublicClient mockClient = createMockPublicClient(
                new io.brane.core.model.BlockHeader(
                        new Hash("0x" + "0".repeat(64)), 1L, new Hash("0x" + "1".repeat(64)),
                        System.currentTimeMillis() / 1000, Wei.of(30_000_000_000L)));

        // Chain that doesn't support EIP-1559 to avoid additional RPC calls
        final ChainProfile legacyProfile = ChainProfile.of(1L, null, false, Wei.of(1_000_000_000L));
        final SmartGasStrategy testStrategy = new SmartGasStrategy(mockClient, mockProvider, legacyProfile);

        final TransactionRequest request = new TransactionRequest(
                new Address("0x" + "f".repeat(40)),
                new Address("0x" + "e".repeat(40)),
                null, userGasLimit, Wei.of(20_000_000_000L), null, null, 1L, null, false, null);

        final SmartGasStrategy.GasFilledRequest result = testStrategy.applyDefaults(request, request.from());
        final TransactionRequest filled = result.request();

        // Gas limit should NOT be overridden
        assertEquals(userGasLimit, filled.gasLimit().longValue());
    }

    @Test
    void legacyRequestDoesNotReportFallback() {
        final BraneProvider mockProvider = (method, params) -> {
            if ("eth_estimateGas".equals(method)) {
                return new JsonRpcResponse("2.0", "0x5208", null, "1");
            } else if ("eth_gasPrice".equals(method)) {
                return new JsonRpcResponse("2.0", "0x4a817c800", null, "1"); // 20 Gwei
            }
            return null;
        };

        // Create a profile that supports EIP-1559 but we'll request legacy
        final PublicClient mockClient = createMockPublicClient(
                new io.brane.core.model.BlockHeader(
                        new Hash("0x" + "0".repeat(64)), 1L, new Hash("0x" + "1".repeat(64)),
                        System.currentTimeMillis() / 1000, Wei.of(30_000_000_000L)));

        final SmartGasStrategy testStrategy = new SmartGasStrategy(mockClient, mockProvider, profile);

        // Request LEGACY transaction (isEip1559 = false)
        final TransactionRequest request = new TransactionRequest(
                new Address("0x" + "f".repeat(40)),
                new Address("0x" + "e".repeat(40)),
                null, null, null, null, null, 1L, null, false, null);

        final SmartGasStrategy.GasFilledRequest result = testStrategy.applyDefaults(request, request.from());
        final TransactionRequest filled = result.request();

        // Legacy request fulfilled as legacy = no fallback
        assertFalse(filled.isEip1559());
        assertFalse(result.fellBackToLegacy(), "Legacy request fulfilled as legacy is NOT a fallback");
        assertFalse(result.requestedEip1559(), "Should record that legacy was requested");
        assertFalse(result.actualEip1559(), "Should record that actual tx is legacy");
    }

    /** Creates a mock PublicClient that returns the given block for getLatestBlock() */
    private static PublicClient createMockPublicClient(io.brane.core.model.BlockHeader block) {
        return new MockPublicClient(block);
    }

    /** Non-final mock implementation for testing */
    private static class MockPublicClient implements PublicClient {
        private final io.brane.core.model.BlockHeader block;

        MockPublicClient(io.brane.core.model.BlockHeader block) {
            this.block = block;
        }

        @Override
        public io.brane.core.model.BlockHeader getLatestBlock() {
            return block;
        }

        @Override
        public io.brane.core.model.BlockHeader getBlockByNumber(long blockNumber) {
            return null;
        }

        @Override
        public io.brane.core.model.Transaction getTransactionByHash(Hash hash) {
            return null;
        }

        @Override
        public io.brane.core.types.HexData call(CallRequest request, BlockTag blockTag) {
            return io.brane.core.types.HexData.EMPTY;
        }

        @SuppressWarnings("deprecation")
        @Override
        public String call(Map<String, Object> callObject, String blockTag) {
            return null;
        }

        @Override
        public List<io.brane.core.model.LogEntry> getLogs(LogFilter filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.math.BigInteger getChainId() {
            return java.math.BigInteger.ONE;
        }

        @Override
        public java.math.BigInteger getBalance(Address address) {
            return java.math.BigInteger.ZERO;
        }

        @Override
        public Subscription subscribeToNewHeads(java.util.function.Consumer<io.brane.core.model.BlockHeader> callback) {
            return null;
        }

        @Override
        public Subscription subscribeToLogs(LogFilter filter, java.util.function.Consumer<io.brane.core.model.LogEntry> callback) {
            return null;
        }

        @Override
        public io.brane.core.model.AccessListWithGas createAccessList(io.brane.core.model.TransactionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MulticallBatch createBatch() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SimulateResult simulateCalls(SimulateRequest request) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void txObjectIncludesAccessListWhenPresent() {
        final TransactionRequest request = new TransactionRequest(
                new Address("0x" + "f".repeat(40)),
                new Address("0x" + "e".repeat(40)),
                null,
                null,
                null,
                null,
                null,
                1L,
                null,
                true,
                List.of(new AccessListEntry(new Address("0x" + "a".repeat(40)),
                        List.of(new Hash("0x" + "1".repeat(64))))));

        final Map<String, Object> tx = strategy.toTxObject(request);

        assertTrue(tx.containsKey("accessList"));
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> accessList = (List<Map<String, Object>>) tx.get("accessList");
        assertEquals("0x" + "a".repeat(40), accessList.getFirst().get("address"));
        assertEquals(List.of("0x" + "1".repeat(64)), accessList.getFirst().get("storageKeys"));
    }

    @Test
    void txObjectOmitsAccessListWhenNullOrEmpty() {
        final TransactionRequest nullAccessList = new TransactionRequest(new Address("0x" + "f".repeat(40)), null, null,
                null, null, null, null, null, null, true, null);
        final TransactionRequest emptyAccessList = new TransactionRequest(
                new Address("0x" + "f".repeat(40)), null, null, null, null, null, null, null, null, true, List.of());

        assertFalse(strategy.toTxObject(nullAccessList).containsKey("accessList"));
        assertFalse(strategy.toTxObject(emptyAccessList).containsKey("accessList"));
    }

    private static final class NoopPublicClient implements PublicClient {

        @Override
        public io.brane.core.model.BlockHeader getLatestBlock() {
            return null;
        }

        @Override
        public io.brane.core.model.BlockHeader getBlockByNumber(final long blockNumber) {
            return null;
        }

        @Override
        public io.brane.core.model.Transaction getTransactionByHash(final io.brane.core.types.Hash hash) {
            return null;
        }

        @Override
        public io.brane.core.types.HexData call(CallRequest request, BlockTag blockTag) {
            return io.brane.core.types.HexData.EMPTY;
        }

        @SuppressWarnings("deprecation")
        @Override
        public String call(final Map<String, Object> callObject, final String blockTag) {
            return null;
        }

        @Override
        public java.util.List<io.brane.core.model.LogEntry> getLogs(io.brane.rpc.LogFilter filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.math.BigInteger getChainId() {
            return java.math.BigInteger.ONE;
        }

        @Override
        public java.math.BigInteger getBalance(io.brane.core.types.Address address) {
            return java.math.BigInteger.ZERO;
        }

        @Override
        public io.brane.rpc.Subscription subscribeToNewHeads(
                java.util.function.Consumer<io.brane.core.model.BlockHeader> callback) {
            return null;
        }

        @Override
        public io.brane.rpc.Subscription subscribeToLogs(io.brane.rpc.LogFilter filter,
                java.util.function.Consumer<io.brane.core.model.LogEntry> callback) {
            return null;
        }

        @Override
        public io.brane.core.model.AccessListWithGas createAccessList(io.brane.core.model.TransactionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.brane.rpc.MulticallBatch createBatch() {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.brane.rpc.SimulateResult simulateCalls(io.brane.rpc.SimulateRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
