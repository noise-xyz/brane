package io.brane.contract;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.brane.core.abi.Abi;
import io.brane.core.abi.AbiBinding;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.types.Address;

/**
 * Tests for AbiBinding class which handles method-to-ABI function resolution.
 *
 * Note: BraneContract.bind() validation tests are now performed in integration tests
 * since Brane.Signer is a sealed interface.
 */
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
}
