package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.brane.core.builder.TxBuilder;
import io.brane.core.chain.ChainProfile;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Wei;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SmartGasStrategyTest {

    @Test
    void appliesDefaultsForEip1559() {
        final FakePublicClient publicClient = new FakePublicClient();
        publicClient.latestBlock = new BlockHeader(null, 1L, null, 0L, Wei.of(20_000_000_000L));

        final FakeBraneProvider provider = new FakeBraneProvider();
        provider.responses.put("eth_estimateGas", "0x186a0"); // 100_000

        final ChainProfile profile = ChainProfile.of(1L, "http://localhost", true, Wei.of(2_000_000_000L));
        final SmartGasStrategy strategy = new SmartGasStrategy(publicClient, provider, profile);

        final TransactionRequest tx = TxBuilder.eip1559()
                .to(new Address("0x" + "0".repeat(40)))
                .value(Wei.of(0))
                .build();

        final TransactionRequest result = strategy.applyDefaults(tx, new Address("0x" + "1".repeat(40)));

        assertEquals(120_000L, result.gasLimit());
        assertEquals(BigInteger.valueOf(2_000_000_000L), result.maxPriorityFeePerGas().value());
        assertEquals(BigInteger.valueOf(42_000_000_000L), result.maxFeePerGas().value());
    }

    @Test
    void respectsUserPriorityFee() {
        final FakePublicClient publicClient = new FakePublicClient();
        publicClient.latestBlock = new BlockHeader(null, 1L, null, 0L, Wei.of(20_000_000_000L));

        final FakeBraneProvider provider = new FakeBraneProvider();
        provider.responses.put("eth_estimateGas", "0x186a0");

        final ChainProfile profile = ChainProfile.of(1L, "http://localhost", true, Wei.of(2_000_000_000L));
        final SmartGasStrategy strategy = new SmartGasStrategy(publicClient, provider, profile);

        final TransactionRequest tx = TxBuilder.eip1559()
                .to(new Address("0x" + "0".repeat(40)))
                .maxPriorityFeePerGas(Wei.of(5_000_000_000L))
                .build();

        final TransactionRequest result = strategy.applyDefaults(tx, new Address("0x" + "1".repeat(40)));

        assertEquals(BigInteger.valueOf(5_000_000_000L), result.maxPriorityFeePerGas().value());
        assertEquals(BigInteger.valueOf(45_000_000_000L), result.maxFeePerGas().value());
    }

    @Test
    void respectsUserMaxFee() {
        final FakePublicClient publicClient = new FakePublicClient();
        // Should not be called
        
        final FakeBraneProvider provider = new FakeBraneProvider();
        provider.responses.put("eth_estimateGas", "0x186a0");

        final ChainProfile profile = ChainProfile.of(1L, "http://localhost", true, Wei.of(2_000_000_000L));
        final SmartGasStrategy strategy = new SmartGasStrategy(publicClient, provider, profile);

        final TransactionRequest tx = TxBuilder.eip1559()
                .to(new Address("0x" + "0".repeat(40)))
                .maxPriorityFeePerGas(Wei.of(3_000_000_000L))
                .maxFeePerGas(Wei.of(100_000_000_000L))
                .build();

        final TransactionRequest result = strategy.applyDefaults(tx, new Address("0x" + "1".repeat(40)));

        assertEquals(BigInteger.valueOf(3_000_000_000L), result.maxPriorityFeePerGas().value());
        assertEquals(BigInteger.valueOf(100_000_000_000L), result.maxFeePerGas().value());
    }

    @Test
    void fallsBackToLegacyIfBaseFeeMissing() {
        final FakePublicClient publicClient = new FakePublicClient();
        publicClient.latestBlock = new BlockHeader(null, 1L, null, 0L, null); // No baseFee

        final FakeBraneProvider provider = new FakeBraneProvider();
        provider.responses.put("eth_estimateGas", "0x186a0");
        provider.responses.put("eth_gasPrice", "0x6fc23ac00"); // 30 gwei

        final ChainProfile profile = ChainProfile.of(1L, "http://localhost", true, Wei.of(2_000_000_000L));
        final SmartGasStrategy strategy = new SmartGasStrategy(publicClient, provider, profile);

        final TransactionRequest tx = TxBuilder.eip1559()
                .to(new Address("0x" + "0".repeat(40)))
                .build();

        final TransactionRequest result = strategy.applyDefaults(tx, new Address("0x" + "1".repeat(40)));

        assertNotNull(result.gasPrice());
        assertEquals(BigInteger.valueOf(30_000_000_000L), result.gasPrice().value());
        assertNull(result.maxFeePerGas());
        assertNull(result.maxPriorityFeePerGas());
    }

    @Test
    void handlesLegacyChain() {
        final FakePublicClient publicClient = new FakePublicClient();
        
        final FakeBraneProvider provider = new FakeBraneProvider();
        provider.responses.put("eth_estimateGas", "0x186a0");
        provider.responses.put("eth_gasPrice", "0x6fc23ac00");

        final ChainProfile profile = ChainProfile.of(1L, "http://localhost", false, null);
        final SmartGasStrategy strategy = new SmartGasStrategy(publicClient, provider, profile);

        final TransactionRequest tx = TxBuilder.legacy()
                .to(new Address("0x" + "0".repeat(40)))
                .build();

        final TransactionRequest result = strategy.applyDefaults(tx, new Address("0x" + "1".repeat(40)));

        assertEquals(BigInteger.valueOf(30_000_000_000L), result.gasPrice().value());
    }

    @Test
    void respectsUserGasLimit() {
        final FakePublicClient publicClient = new FakePublicClient();
        final FakeBraneProvider provider = new FakeBraneProvider();
        provider.responses.put("eth_gasPrice", "0x6fc23ac00");

        final ChainProfile profile = ChainProfile.of(1L, "http://localhost", false, null);
        final SmartGasStrategy strategy = new SmartGasStrategy(publicClient, provider, profile);

        final TransactionRequest tx = TxBuilder.legacy()
                .to(new Address("0x" + "0".repeat(40)))
                .gasLimit(500_000L)
                .build();

        final TransactionRequest result = strategy.applyDefaults(tx, new Address("0x" + "1".repeat(40)));

        assertEquals(500_000L, result.gasLimit());
    }

    // Fakes
    static class FakePublicClient implements PublicClient {
        BlockHeader latestBlock;

        @Override
        public BlockHeader getLatestBlock() {
            return latestBlock;
        }

        @Override
        public BlockHeader getBlockByNumber(long blockNumber) {
            return latestBlock;
        }

        @Override
        public io.brane.core.model.Transaction getTransactionByHash(io.brane.core.types.Hash hash) {
            return null;
        }

        @Override
        public String call(Map<String, Object> callObject, String blockTag) {
            return null;
        }

        @Override
        public List<io.brane.core.model.LogEntry> getLogs(LogFilter filter) {
            return List.of();
        }
    }

    static class FakeBraneProvider implements BraneProvider {
        final Map<String, String> responses = new HashMap<>();

        @Override
        public JsonRpcResponse send(String method, List<?> params) {
            String result = responses.get(method);
            if (result == null) {
                return new JsonRpcResponse("2.0", null, null, "1");
            }
            return new JsonRpcResponse("2.0", result, null, "1");
        }
    }
}
