package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.brane.core.chain.ChainProfile;
import io.brane.core.model.AccessListEntry;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.rpc.Subscription;
import io.brane.core.types.Hash;
import io.brane.core.types.Wei;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SmartGasStrategyTest {

    private final PublicClient unusedPublicClient = new NoopPublicClient();
    private final BraneProvider unusedProvider = (method, params) -> null;
    private final ChainProfile profile = ChainProfile.of(1L, null, true, Wei.of(1_000_000_000L));
    private final SmartGasStrategy strategy = new SmartGasStrategy(unusedPublicClient, unusedProvider, profile);

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
    }
}
