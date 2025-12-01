package io.brane.contract;

import static org.junit.jupiter.api.Assertions.*;

import io.brane.core.model.TransactionReceipt;
import io.brane.core.types.Address;
import io.brane.rpc.PublicClient;
import io.brane.rpc.WalletClient;
import java.math.BigInteger;
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

        Abi abi = Abi.fromJson(json);
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> new AbiBinding(abi, TestContract.class));
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
        public long getChainId() {
            return 1;
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
