package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.brane.core.chain.ChainProfile;
import io.brane.core.model.AccessListEntry;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
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
        final TransactionRequest request =
                new TransactionRequest(
                        new Address("0xfrom"),
                        new Address("0xto"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        1L,
                        null,
                        true,
                        List.of(new AccessListEntry(new Address("0xabc"), List.of(new Hash("0x01")))));

        final Map<String, Object> tx = strategy.toTxObject(request);

        assertTrue(tx.containsKey("accessList"));
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> accessList = (List<Map<String, Object>>) tx.get("accessList");
        assertEquals("0xabc", accessList.getFirst().get("address"));
        assertEquals(List.of("0x01"), accessList.getFirst().get("storageKeys"));
    }

    @Test
    void txObjectOmitsAccessListWhenNullOrEmpty() {
        final TransactionRequest nullAccessList =
                new TransactionRequest(new Address("0xfrom"), null, null, null, null, null, null, null, null, true, null);
        final TransactionRequest emptyAccessList =
                new TransactionRequest(
                        new Address("0xfrom"), null, null, null, null, null, null, null, null, true, List.of());

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
        public String call(final Map<String, Object> callObject, final String blockTag) {
            return null;
        }

        @Override
        public List<io.brane.core.model.LogEntry> getLogs(final LogFilter filter) {
            return List.of();
        }
    }
}
