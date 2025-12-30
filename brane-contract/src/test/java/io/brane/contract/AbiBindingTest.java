package io.brane.contract;

import static org.junit.jupiter.api.Assertions.*;

import io.brane.core.abi.Abi;
import io.brane.core.abi.AbiBinding;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.types.Address;
import io.brane.rpc.Subscription;
import io.brane.rpc.PublicClient;
import io.brane.rpc.WalletClient;
import java.math.BigInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AbiBindingTest {

    @Test
    void resolvesMethodsSuccessfully() {
        String json = """
                [
                    {
                        "type": "function",
                        "name": "balanceOf",
                        "stateMutability": "view",
                        "inputs": [{"name": "owner", "type": "address"}],
                        "outputs": [{"name": "", "type": "uint256"}]
                    }
                ]
                """;

        interface TestContract {
            BigInteger balanceOf(Address owner);
        }

        Abi abi = Abi.fromJson(json);
        AbiBinding binding = new AbiBinding(abi, TestContract.class);

        assertNotNull(binding);
    }

    @Test
    void throwsOnMissingFunction() {
        String json = """
                [
                    {
                        "type": "function",
                        "name": "foo",
                        "stateMutability": "view",
                        "inputs": [],
                        "outputs": []
                    }
                ]
                """;

        interface TestContract {
            void bar();
        }

        Abi abi = Abi.fromJson(json);
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> new AbiBinding(abi, TestContract.class));
        assertTrue(e.getMessage().contains("No ABI function named 'bar'"));
    }

    @Test
    void throwsOnParameterCountMismatch() {
        String json = """
                [
                    {
                        "type": "function",
                        "name": "foo",
                        "stateMutability": "view",
                        "inputs": [{"name": "a", "type": "uint256"}],
                        "outputs": []
                    }
                ]
                """;

        interface TestContract {
            void foo();
        }

        PublicClient fakePublic = new FakePublicClient() {};
        WalletClient fakeWallet = new FakeWalletClient() {};

        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> BraneContract.bind(
                        new Address("0x" + "1".repeat(40)),
                        json,
                        fakePublic,
                        fakeWallet,
                        TestContract.class));
        assertTrue(e.getMessage().contains("expects 1 parameters"));
    }

    @Test
    void cachesResolvedMethods() throws Exception {
        String json = """
                [
                    {
                        "type": "function",
                        "name": "getValue",
                        "stateMutability": "view",
                        "inputs": [],
                        "outputs": [{"name": "", "type": "uint256"}]
                    }
                ]
                """;

        interface TestContract {
            BigInteger getValue();
        }

        Abi abi = Abi.fromJson(json);
        AbiBinding binding = new AbiBinding(abi, TestContract.class);

        var method = TestContract.class.getMethod("getValue");
        var metadata1 = binding.resolve(method);
        var metadata2 = binding.resolve(method);

        assertSame(metadata1, metadata2);
    }

    @Test
    void validatesViewReturnTypes() {
        String json = """
                [
                    {
                        "type": "function",
                        "name": "badReturn",
                        "stateMutability": "view",
                        "inputs": [],
                        "outputs": [{"name": "", "type": "uint256"}]
                    }
                ]
                """;

        interface TestContract {
            String badReturn(); // Wrong return type for uint256
        }

        PublicClient fakePublic = new FakePublicClient() {
        };
        WalletClient fakeWallet = new FakeWalletClient() {
        };

        assertThrows(IllegalArgumentException.class, () -> BraneContract.bind(
                new Address("0x" + "1".repeat(40)),
                json,
                fakePublic,
                fakeWallet,
                TestContract.class));
    }

    @Test
    void validatesWriteReturnTypes() {
        String json = """
                [
                    {
                        "type": "function",
                        "name": "write",
                        "stateMutability": "nonpayable",
                        "inputs": [],
                        "outputs": []
                    }
                ]
                """;

        interface BadContract {
            String write(); // Write functions must return void or TransactionReceipt
        }

        PublicClient fakePublic = new FakePublicClient() {
        };
        WalletClient fakeWallet = new FakeWalletClient() {
        };

        assertThrows(IllegalArgumentException.class, () -> BraneContract.bind(
                new Address("0x" + "1".repeat(40)),
                json,
                fakePublic,
                fakeWallet,
                BadContract.class));
    }

    @Test
    void allowsStringReturnTypeForStringOutput() {
        String json = """
                [
                    {
                        "type": "function",
                        "name": "name",
                        "stateMutability": "view",
                        "inputs": [],
                        "outputs": [{"name": "", "type": "string"}]
                    }
                ]
                """;

        interface TestContract {
            String name();
        }

        Abi abi = Abi.fromJson(json);
        assertDoesNotThrow(() -> new AbiBinding(abi, TestContract.class));
    }

    @Test
    void rejectsStringReturnTypeForNonStringOutput() {
        String json = """
                [
                    {
                        "type": "function",
                        "name": "getValue",
                        "stateMutability": "view",
                        "inputs": [],
                        "outputs": [{"name": "", "type": "uint256"}]
                    }
                ]
                """;

        interface TestContract {
            String getValue();  // Wrong - should be BigInteger for uint256
        }

        PublicClient fakePublic = new FakePublicClient() {};
        WalletClient fakeWallet = new FakeWalletClient() {};

        assertThrows(IllegalArgumentException.class, () -> BraneContract.bind(
                new Address("0x" + "1".repeat(40)),
                json,
                fakePublic,
                fakeWallet,
                TestContract.class));
    }

    @Test
    void allowsBytesReturnTypeForBytesOutput() {
        String json = """
                [
                    {
                        "type": "function",
                        "name": "getData",
                        "stateMutability": "view",
                        "inputs": [],
                        "outputs": [{"name": "", "type": "bytes"}]
                    }
                ]
                """;

        interface TestContract {
            byte[] getData();
        }

        Abi abi = Abi.fromJson(json);
        assertDoesNotThrow(() -> new AbiBinding(abi, TestContract.class));
    }

    @Test
    void allowsHexDataReturnTypeForBytes32Output() {
        String json = """
                [
                    {
                        "type": "function",
                        "name": "getHash",
                        "stateMutability": "view",
                        "inputs": [],
                        "outputs": [{"name": "", "type": "bytes32"}]
                    }
                ]
                """;

        interface TestContract {
            io.brane.core.types.HexData getHash();
        }

        PublicClient fakePublic = new FakePublicClient() {};
        WalletClient fakeWallet = new FakeWalletClient() {};

        assertDoesNotThrow(() -> BraneContract.bind(
                new Address("0x" + "1".repeat(40)),
                json,
                fakePublic,
                fakeWallet,
                TestContract.class));
    }

    @Test
    void allowsListReturnTypeForDynamicArrayOutput() {
        String json = """
                [
                    {
                        "type": "function",
                        "name": "getOwners",
                        "stateMutability": "view",
                        "inputs": [],
                        "outputs": [{"name": "", "type": "address[]"}]
                    }
                ]
                """;

        interface TestContract {
            java.util.List<Address> getOwners();
        }

        PublicClient fakePublic = new FakePublicClient() {};
        WalletClient fakeWallet = new FakeWalletClient() {};

        assertDoesNotThrow(() -> BraneContract.bind(
                new Address("0x" + "1".repeat(40)),
                json,
                fakePublic,
                fakeWallet,
                TestContract.class));
    }

    @Test
    void rejectsPayableAnnotationOnNonPayableFunction() {
        String json = """
                [
                    {
                        "type": "function",
                        "name": "transfer",
                        "stateMutability": "nonpayable",
                        "inputs": [{"name": "to", "type": "address"}],
                        "outputs": []
                    }
                ]
                """;

        interface TestContract {
            @Payable
            void transfer(io.brane.core.types.Wei value, Address to);
        }

        PublicClient fakePublic = new FakePublicClient() {};
        WalletClient fakeWallet = new FakeWalletClient() {};

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                BraneContract.bind(
                        new Address("0x" + "1".repeat(40)),
                        json,
                        fakePublic,
                        fakeWallet,
                        TestContract.class));

        assertTrue(ex.getMessage().contains("does not map to a payable ABI function"));
    }

    @Test
    void allowsPayableAnnotationOnPayableFunction() {
        String json = """
                [
                    {
                        "type": "function",
                        "name": "deposit",
                        "stateMutability": "payable",
                        "inputs": [],
                        "outputs": []
                    }
                ]
                """;

        interface TestContract {
            @Payable
            void deposit(io.brane.core.types.Wei value);
        }

        PublicClient fakePublic = new FakePublicClient() {};
        WalletClient fakeWallet = new FakeWalletClient() {};

        assertDoesNotThrow(() -> BraneContract.bind(
                new Address("0x" + "1".repeat(40)),
                json,
                fakePublic,
                fakeWallet,
                TestContract.class));
    }

    @Test
    void allowsVoidAndTransactionReceiptForWrites() {
        String json = """
                [
                    {
                        "type": "function",
                        "name": "write1",
                        "stateMutability": "nonpayable",
                        "inputs": [],
                        "outputs": []
                    },
                    {
                        "type": "function",
                        "name": "write2",
                        "stateMutability": "nonpayable",
                        "inputs": [],
                        "outputs": []
                    }
                ]
                """;

        interface TestContract {
            void write1();

            TransactionReceipt write2();
        }

        Abi abi = Abi.fromJson(json);
        assertDoesNotThrow(() -> new AbiBinding(abi, TestContract.class));
    }

    @Test
    void cacheIsThreadSafeForConcurrentReads() throws Exception {
        String json = """
                [
                    {
                        "type": "function",
                        "name": "getValue",
                        "stateMutability": "view",
                        "inputs": [],
                        "outputs": [{"name": "", "type": "uint256"}]
                    }
                ]
                """;

        interface TestContract {
            BigInteger getValue();
        }

        Abi abi = Abi.fromJson(json);
        AbiBinding binding = new AbiBinding(abi, TestContract.class);
        var method = TestContract.class.getMethod("getValue");

        // Test concurrent reads from multiple threads
        int threadCount = 10;
        int iterationsPerThread = 1000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < iterationsPerThread; j++) {
                            var metadata = binding.resolve(method);
                            if (metadata != null && "getValue".equals(metadata.name())) {
                                successCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            doneLatch.await();
        }

        assertEquals(0, errorCount.get(), "No errors should occur during concurrent access");
        assertEquals(threadCount * iterationsPerThread, successCount.get(),
                "All reads should succeed");
    }

    // Minimal fake implementations
    private static abstract class FakePublicClient implements io.brane.rpc.PublicClient {
        @Override
        public io.brane.core.model.BlockHeader getLatestBlock() {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.brane.core.model.BlockHeader getBlockByNumber(long blockNumber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.brane.core.model.Transaction getTransactionByHash(io.brane.core.types.Hash hash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String call(java.util.Map<String, Object> callObject, String blockTag) {
            return "0x";
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
            return null;
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

    private static abstract class FakeWalletClient implements io.brane.rpc.WalletClient {
        @Override
        public io.brane.core.types.Hash sendTransaction(io.brane.core.model.TransactionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransactionReceipt sendTransactionAndWait(
                io.brane.core.model.TransactionRequest request,
                long timeoutMillis,
                long pollIntervalMillis) {
            throw new UnsupportedOperationException();
        }
    }
}
