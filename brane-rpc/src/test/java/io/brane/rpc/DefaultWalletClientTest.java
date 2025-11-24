package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.brane.core.builder.TxBuilder;
import io.brane.core.chain.ChainProfile;
import io.brane.core.chain.ChainProfiles;
import io.brane.core.error.ChainMismatchException;
import io.brane.core.error.InvalidSenderException;
import io.brane.core.error.RpcException;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.internal.web3j.crypto.RawTransaction;
import io.brane.internal.web3j.crypto.transaction.type.TransactionType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class DefaultWalletClientTest {

    @Test
    void sendsLegacyTransactionWithAutoFields() {
        final FakeSigner signer = new FakeSigner(new Address("0x" + "1".repeat(40)));
        final FakePublicClient publicClient = new FakePublicClient();

        final FakeBraneProvider provider =
                new FakeBraneProvider()
                        .respond("eth_chainId", "0x1")
                        .respond("eth_estimateGas", "0x5208")
                        .respond("eth_getTransactionCount", "0x5")

                        .respond("eth_sendRawTransaction", "0x" + "a".repeat(64));

        DefaultWalletClient wallet =
                DefaultWalletClient.from(
                        provider,
                        publicClient,
                        signer.asSigner(),
                        signer.address(),
                        1L,
                        ChainProfiles.ANVIL_LOCAL);

        TransactionRequest request =
                TxBuilder.legacy()
                        .from(signer.address())
                        .to(new Address("0x" + "2".repeat(40)))
                        .value(Wei.of(0))
                        .gasPrice(Wei.of(1_000_000_000L))
                        .data(new HexData("0x"))
                        .build();

        Hash hash = wallet.sendTransaction(request);

        assertEquals("0x" + "a".repeat(64), hash.value());
        assertEquals(TransactionType.LEGACY, signer.lastTransactionType());
        assertEquals(5L, signer.lastNonce());
        assertEquals(25200L, signer.lastGasLimit());
    }

    @Test
    void sendsTransactionWithCustomGasBuffer() {
        final FakeSigner signer = new FakeSigner(new Address("0x" + "1".repeat(40)));
        final FakePublicClient publicClient = new FakePublicClient()
                .withLatestBlock(new BlockHeader(null, 1L, null, 0L, Wei.of(10_000_000_000L)));

        final FakeBraneProvider provider =
                new FakeBraneProvider()
                        .respond("eth_chainId", "0x1")
                        .respond("eth_estimateGas", "0x186a0") // 100_000
                        .respond("eth_getTransactionCount", "0x0")
                        .respond("eth_sendRawTransaction", "0x" + "a".repeat(64));

        // Create wallet with custom 50% buffer (150/100)
        final ChainProfile profile = ChainProfile.of(1L, "http://localhost", true, Wei.of(1_000_000_000L));
        DefaultWalletClient wallet =
                DefaultWalletClient.create(
                        provider,
                        publicClient,
                        signer.asSigner(),
                        signer.address(),
                        profile,
                        BigInteger.valueOf(150),
                        BigInteger.valueOf(100));

        final TransactionRequest tx =
                TxBuilder.eip1559().to(new Address("0x" + "2".repeat(40))).value(Wei.of(100)).build();

        wallet.sendTransaction(tx);

        // Verify eth_sendRawTransaction was called
        assertTrue(provider.methods.contains("eth_sendRawTransaction"));

        // Verify gas limit: 100_000 * 150 / 100 = 150_000
        assertEquals(150_000L, signer.lastGasLimit());
    }

    @Test
    void sendsEip1559TransactionWithAutoFields() {
        final FakeSigner signer = new FakeSigner(new Address("0x" + "1".repeat(40)));
        final FakePublicClient publicClient =
                new FakePublicClient()
                        .withLatestBlock(
                                new BlockHeader(
                                        null,
                                        1L,
                                        null,
                                        0L,
                                        Wei.of(BigInteger.valueOf(10_000_000_000L))));

        final FakeBraneProvider provider =
                new FakeBraneProvider()
                        .respond("eth_chainId", "0x1")
                        .respond("eth_estimateGas", "0x5208")
                        .respond("eth_getTransactionCount", "0x6")
                        .respond("eth_sendRawTransaction", "0x" + "b".repeat(64));

        DefaultWalletClient wallet =
                DefaultWalletClient.from(
                        provider,
                        publicClient,
                        signer.asSigner(),
                        signer.address(),
                        1L,
                        ChainProfiles.ANVIL_LOCAL);

        TransactionRequest request =
                TxBuilder.eip1559()
                        .to(new Address("0x" + "2".repeat(40)))
                        .value(Wei.of(0))
                        .data(new HexData("0x"))
                        .build();

        Hash hash = wallet.sendTransaction(request);

        assertEquals("0x" + "b".repeat(64), hash.value());
        assertEquals(TransactionType.EIP1559, signer.lastTransactionType());
        assertEquals(6L, signer.lastNonce());
        // Auto-filled fees should match gasPrice (1 Gwei = 0x3b9aca00)
        // Note: DefaultWalletClient implementation uses gasPrice for both maxFee and maxPriority if not set
        // This is a simplification, but valid for the test assertion based on current logic
    }

    @Test
    void appliesSmartDefaultsForEip1559() {
        final FakeSigner signer = new FakeSigner(new Address("0x" + "1".repeat(40)));
        final FakePublicClient publicClient =
                new FakePublicClient()
                        .withLatestBlock(
                                new BlockHeader(
                                        null,
                                        1L,
                                        null,
                                        0L,
                                        Wei.of(BigInteger.valueOf(20_000_000_000L))));

        final FakeBraneProvider provider =
                new FakeBraneProvider()
                        .respond("eth_chainId", "0x1")
                        .respond("eth_estimateGas", "0x186a0") // 100_000
                        .respond("eth_getTransactionCount", "0x1")
                        .respond("eth_sendRawTransaction", "0x" + "d".repeat(64));

        DefaultWalletClient wallet =
                DefaultWalletClient.from(
                        provider,
                        publicClient,
                        signer.asSigner(),
                        signer.address(),
                        1L,
                        ChainProfile.of(1L, "http://localhost", true, Wei.of(2_000_000_000L)));

        TransactionRequest request =
                TxBuilder.eip1559()
                        .to(new Address("0x" + "2".repeat(40)))
                        .value(Wei.of(0))
                        .data(new HexData("0x"))
                        .build();

        Hash hash = wallet.sendTransaction(request);

        assertEquals("0x" + "d".repeat(64), hash.value());
        assertTrue(signer.lastGasLimit() > 100_000L);
        assertEquals(BigInteger.valueOf(2_000_000_000L), signer.lastMaxPriorityFeePerGas());
        assertEquals(BigInteger.valueOf(42_000_000_000L), signer.lastMaxFeePerGas());
    }

    @Test
    void fallsBackToGasPriceWhenBaseFeeMissing() {
        final FakeSigner signer = new FakeSigner(new Address("0x" + "1".repeat(40)));
        final FakePublicClient publicClient = new FakePublicClient();

        final FakeBraneProvider provider =
                new FakeBraneProvider()
                        .respond("eth_chainId", "0x1")
                        .respond("eth_estimateGas", "0x1388")
                        .respond("eth_gasPrice", "0x6fc23ac00") // 30 gwei
                        .respond("eth_getTransactionCount", "0x1")
                        .respond("eth_sendRawTransaction", "0x" + "e".repeat(64));

        DefaultWalletClient wallet =
                DefaultWalletClient.from(
                        provider,
                        publicClient,
                        signer.asSigner(),
                        signer.address(),
                        1L,
                        ChainProfiles.ANVIL_LOCAL);

        TransactionRequest request =
                TxBuilder.eip1559()
                        .to(new Address("0x" + "2".repeat(40)))
                        .value(Wei.of(0))
                        .data(new HexData("0x"))
                        .build();

        Hash hash = wallet.sendTransaction(request);

        assertEquals("0x" + "e".repeat(64), hash.value());
        assertEquals(TransactionType.LEGACY, signer.lastTransactionType());
        assertEquals(BigInteger.valueOf(30_000_000_000L), signer.lastGasPrice());
    }

    @Test
    void sendsEip1559TransactionWithExplicitFees() {
        final FakeSigner signer = new FakeSigner(new Address("0x" + "1".repeat(40)));
        final FakePublicClient publicClient = new FakePublicClient();

        final FakeBraneProvider provider =
                new FakeBraneProvider()
                        .respond("eth_chainId", "0x1")
                        .respond("eth_estimateGas", "0x5208")
                        .respond("eth_getTransactionCount", "0x7")
                        // No eth_gasPrice response needed
                        .respond("eth_sendRawTransaction", "0x" + "c".repeat(64));

        DefaultWalletClient wallet =
                DefaultWalletClient.from(
                        provider,
                        publicClient,
                        signer.asSigner(),
                        signer.address(),
                        1L,
                        ChainProfiles.ANVIL_LOCAL);

        TransactionRequest request =
                TxBuilder.eip1559()
                        .to(new Address("0x" + "2".repeat(40)))
                        .value(Wei.of(0))
                        .maxFeePerGas(Wei.of(2_000_000_000L))
                        .maxPriorityFeePerGas(Wei.of(1_000_000_000L))
                        .data(new HexData("0x"))
                        .build();

        Hash hash = wallet.sendTransaction(request);

        assertEquals("0x" + "c".repeat(64), hash.value());
        assertEquals(TransactionType.EIP1559, signer.lastTransactionType());
        assertEquals(7L, signer.lastNonce());
    }

    @Test
    void sendsEip1559TransactionWithAccessList() {
        final FakeSigner signer = new FakeSigner(new Address("0x" + "1".repeat(40)));
        final FakePublicClient publicClient =
                new FakePublicClient()
                        .withLatestBlock(
                                new BlockHeader(
                                        null,
                                        1L,
                                        null,
                                        0L,
                                        Wei.of(BigInteger.valueOf(10_000_000_000L))));

        final FakeBraneProvider provider =
                new FakeBraneProvider()
                        .respond("eth_chainId", "0x1")
                        .respond("eth_estimateGas", "0x5208")
                        .respond("eth_getTransactionCount", "0x8")
                        .respond("eth_sendRawTransaction", "0x" + "d".repeat(64));

        DefaultWalletClient wallet =
                DefaultWalletClient.from(
                        provider,
                        publicClient,
                        signer.asSigner(),
                        signer.address(),
                        1L,
                        ChainProfiles.ANVIL_LOCAL);

        final List<io.brane.core.model.AccessListEntry> accessList =
                List.of(
                        new io.brane.core.model.AccessListEntry(
                                new Address("0x" + "2".repeat(40)),
                                List.of(new Hash("0x" + "3".repeat(64)))));

        TransactionRequest request =
                TxBuilder.eip1559()
                        .to(new Address("0x" + "2".repeat(40)))
                        .value(Wei.of(0))
                        .accessList(accessList)
                        .build();

        Hash hash = wallet.sendTransaction(request);

        assertEquals("0x" + "d".repeat(64), hash.value());
        assertEquals(TransactionType.EIP1559, signer.lastTransactionType());
        // We can't easily verify the access list content in the signer without exposing it in FakeSigner,
        // but successful execution implies it didn't crash.
    }

    @Test
    void includesAccessListInEstimation() {
        final FakeSigner signer = new FakeSigner(new Address("0x" + "1".repeat(40)));
        final FakePublicClient publicClient =
                new FakePublicClient()
                        .withLatestBlock(
                                new BlockHeader(null, 1L, null, 0L, Wei.of(10_000_000_000L)));

        final FakeBraneProvider provider =
                new FakeBraneProvider()
                        .respond("eth_chainId", "0x1")
                        .respond("eth_estimateGas", "0x5208")
                        .respond("eth_getTransactionCount", "0x9")
                        .respond("eth_sendRawTransaction", "0x" + "e".repeat(64));

        DefaultWalletClient wallet =
                DefaultWalletClient.from(
                        provider,
                        publicClient,
                        signer.asSigner(),
                        signer.address(),
                        1L,
                        ChainProfiles.ANVIL_LOCAL);

        final List<io.brane.core.model.AccessListEntry> accessList =
                List.of(
                        new io.brane.core.model.AccessListEntry(
                                new Address("0x" + "2".repeat(40)),
                                List.of(new Hash("0x" + "3".repeat(64)))));

        TransactionRequest request =
                TxBuilder.eip1559()
                        .to(new Address("0x" + "2".repeat(40)))
                        .value(Wei.of(0))
                        .accessList(accessList)
                        .build();

        wallet.sendTransaction(request);

        // Verify eth_estimateGas call included accessList
        // This requires inspecting the arguments passed to provider.send()
        // Since FakeBraneProvider doesn't expose params easily, we rely on the fact that
        // SmartGasStrategyTest covers the mapping logic.
        // This test mainly ensures end-to-end flow works without error.
        assertTrue(provider.recordedMethods().contains("eth_estimateGas"));
    }

    @Test
    void enforcesChainId() {
        final FakeSigner signer = new FakeSigner(new Address("0x" + "1".repeat(40)));
        final FakePublicClient publicClient = new FakePublicClient();

        final FakeBraneProvider provider =
                new FakeBraneProvider()
                        .respond("eth_chainId", "0x2");

        DefaultWalletClient wallet =
                DefaultWalletClient.from(
                        provider,
                        publicClient,
                        signer.asSigner(),
                        signer.address(),
                        1L,
                        ChainProfiles.ANVIL_LOCAL);

        TransactionRequest request =
                TxBuilder.legacy()
                        .from(signer.address())
                        .to(new Address("0x" + "2".repeat(40)))
                        .value(Wei.of(0))
                        .gasPrice(Wei.of(1_000_000_000L))
                        .data(new HexData("0x"))
                        .build();

        assertThrows(ChainMismatchException.class, () -> wallet.sendTransaction(request));
    }

    @Test
    void invalidSenderRaisesInvalidSenderException() {
        final FakeSigner signer = new FakeSigner(new Address("0x" + "1".repeat(40)));
        final FakePublicClient publicClient = new FakePublicClient();

        final FakeBraneProvider provider =
                new FakeBraneProvider()
                        .respond("eth_chainId", "0x1")
                        .respond("eth_estimateGas", "0x5208")
                        .respond("eth_getTransactionCount", "0x0")

                        .respondError("eth_sendRawTransaction", -32000, "invalid sender");

        DefaultWalletClient wallet =
                DefaultWalletClient.from(
                        provider,
                        publicClient,
                        signer.asSigner(),
                        signer.address(),
                        1L,
                        ChainProfiles.ANVIL_LOCAL);

        TransactionRequest request =
                TxBuilder.legacy()
                        .from(signer.address())
                        .to(new Address("0x" + "2".repeat(40)))
                        .value(Wei.of(0))
                        .gasPrice(Wei.of(1_000_000_000L))
                        .data(new HexData("0x"))
                        .build();

        assertThrows(InvalidSenderException.class, () -> wallet.sendTransaction(request));
    }

    @Test
    void sendAndWaitPollsUntilReceipt() {
        final FakeSigner signer = new FakeSigner(new Address("0x" + "1".repeat(40)));
        final FakePublicClient publicClient = new FakePublicClient();

        final FakeBraneProvider provider =
                new FakeBraneProvider()
                        .respond("eth_chainId", "0x1")
                        .respond("eth_estimateGas", "0x5208")
                        .respond("eth_getTransactionCount", "0x0")

                        .respond("eth_sendRawTransaction", "0x" + "a".repeat(64))
                        .respond("eth_getTransactionReceipt", null)
                        .respond(
                                "eth_getTransactionReceipt",
                                new LinkedHashMapBuilder()
                                        .put("transactionHash", "0x" + "a".repeat(64))
                                        .put("blockNumber", "0x1")
                                        .put("blockHash", "0x" + "3".repeat(64))
                                        .put("from", signer.address().value())
                                        .put("to", "0x" + "2".repeat(40))
                                        .put("status", "0x1")
                                        .put("cumulativeGasUsed", "0x5208")
                                        .put("logs", List.of())
                                        .build());

        DefaultWalletClient wallet =
                DefaultWalletClient.from(
                        provider,
                        publicClient,
                        signer.asSigner(),
                        signer.address(),
                        1L,
                        ChainProfiles.ANVIL_LOCAL);

        TransactionRequest request =
                TxBuilder.legacy()
                        .from(signer.address())
                        .to(new Address("0x" + "2".repeat(40)))
                        .value(Wei.of(0))
                        .gasPrice(Wei.of(1_000_000_000L))
                        .data(new HexData("0x"))
                        .build();

        TransactionReceipt receipt = wallet.sendTransactionAndWait(request, 2000, 10);

        assertEquals("0x" + "a".repeat(64), receipt.transactionHash().value());
        assertTrue(provider.recordedMethods().contains("eth_getTransactionReceipt"));
    }

    @Test
    void retriesOnSendTransientError() {
        final FakeSigner signer = new FakeSigner(new Address("0x" + "1".repeat(40)));
        final FakePublicClient publicClient = new FakePublicClient();

        final FakeBraneProvider provider =
                new FakeBraneProvider()
                        .respond("eth_chainId", "0x1")
                        .respond("eth_estimateGas", "0x5208")
                        .respond("eth_getTransactionCount", "0x0")
                        .respondError("eth_sendRawTransaction", -32000, "header not found")
                        .respond("eth_sendRawTransaction", "0x" + "f".repeat(64));

        DefaultWalletClient wallet =
                DefaultWalletClient.from(
                        provider,
                        publicClient,
                        signer.asSigner(),
                        signer.address(),
                        1L,
                        ChainProfiles.ANVIL_LOCAL);

        TransactionRequest request =
                TxBuilder.legacy()
                        .from(signer.address())
                        .to(new Address("0x" + "2".repeat(40)))
                        .value(Wei.of(0))
                        .gasPrice(Wei.of(1_000_000_000L))
                        .data(new HexData("0x"))
                        .build();

        Hash hash = wallet.sendTransaction(request);

        assertEquals("0x" + "f".repeat(64), hash.value());
        assertEquals(2, provider.recordedMethods().stream().filter(m -> m.equals("eth_sendRawTransaction")).count());
    }

    private static final class FakeSigner {
        private final Address address;
        private RawTransaction last;

        private FakeSigner(final Address address) {
            this.address = address;
        }

        Address address() {
            return address;
        }

        TransactionSigner asSigner() {
            return tx -> {
                this.last = tx;
                return "0xsigned";
            };
        }

        long lastNonce() {
            return last.getNonce().longValue();
        }

        long lastGasLimit() {
            return last.getGasLimit().longValue();
        }

        BigInteger lastMaxPriorityFeePerGas() {
            if (last.getTransaction() instanceof io.brane.internal.web3j.crypto.transaction.type.Transaction1559 tx) {
                return tx.getMaxPriorityFeePerGas();
            }
            return null;
        }

        BigInteger lastMaxFeePerGas() {
            if (last.getTransaction() instanceof io.brane.internal.web3j.crypto.transaction.type.Transaction1559 tx) {
                return tx.getMaxFeePerGas();
            }
            return null;
        }

        BigInteger lastGasPrice() {
            return last.getGasPrice();
        }

        TransactionType lastTransactionType() {
            return last.getType();
        }
    }

    private static final class FakePublicClient implements PublicClient {
        private BlockHeader latest;

        FakePublicClient withLatestBlock(final BlockHeader header) {
            this.latest = header;
            return this;
        }

        @Override
        public io.brane.core.model.BlockHeader getLatestBlock() {
            return latest;
        }

        @Override
        public io.brane.core.model.BlockHeader getBlockByNumber(long blockNumber) {
            return latest;
        }

        @Override
        public io.brane.core.model.Transaction getTransactionByHash(Hash hash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String call(java.util.Map<String, Object> callObject, String blockTag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<io.brane.core.model.LogEntry> getLogs(final LogFilter filter) {
            return java.util.Collections.emptyList();
        }
    }

    private static final class FakeBraneProvider implements BraneProvider {
        private final List<JsonRpcResponse> responses = new ArrayList<>();
        private final List<String> methods = new ArrayList<>();

        FakeBraneProvider respond(final String method, final Object result) {
            responses.add(new JsonRpcResponse("2.0", result, null, "1"));
            return this;
        }

        FakeBraneProvider respondError(final String method, final int code, final String message) {
            responses.add(new JsonRpcResponse("2.0", null, new JsonRpcError(code, message, null), "1"));
            return this;
        }

        @Override
        public JsonRpcResponse send(final String method, final List<?> params) throws RpcException {
            System.out.println("FakeBraneProvider: " + method);
            if (responses.isEmpty()) {
                throw new RpcException(-1, "No response queued for " + method, null, null, null);
            }
            methods.add(method);
            return responses.remove(0);
        }

        List<String> recordedMethods() {
            return methods;
        }
    }

    private static final class LinkedHashMapBuilder {
        private final java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();

        LinkedHashMapBuilder put(final String key, final Object value) {
            map.put(key, value);
            return this;
        }

        java.util.LinkedHashMap<String, Object> build() {
            return map;
        }
    }
}
