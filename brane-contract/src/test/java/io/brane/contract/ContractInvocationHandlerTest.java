package io.brane.contract;

import static org.junit.jupiter.api.Assertions.*;

import io.brane.core.model.TransactionReceipt;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.rpc.PublicClient;
import io.brane.rpc.WalletClient;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ContractInvocationHandlerTest {

    @Test
    void viewMethodUsesPublicClientOnly() {
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

        AtomicBoolean publicClientCalled = new AtomicBoolean(false);
        AtomicBoolean walletClientCalled = new AtomicBoolean(false);

        PublicClient publicClient = new FakePublicClient() {
            @Override
            public String call(Map<String, Object> callObject, String blockTag) {
                publicClientCalled.set(true);
                // Return encoded uint256(100)
                return "0x0000000000000000000000000000000000000000000000000000000000000064";
            }
        };

        WalletClient walletClient = new FakeWalletClient() {
            @Override
            public Hash sendTransaction(io.brane.core.model.TransactionRequest request) {
                walletClientCalled.set(true);
                throw new AssertionError("WalletClient should not be called for view functions");
            }
        };

        TestContract contract = BraneContract.bind(
                new Address("0x" + "1".repeat(40)),
                json,
                publicClient,
                walletClient,
                TestContract.class);

        BigInteger result = contract.balanceOf(new Address("0x" + "2".repeat(40)));

        assertTrue(publicClientCalled.get(), "PublicClient should be called");
        assertFalse(walletClientCalled.get(), "WalletClient should NOT be called");
        assertEquals(BigInteger.valueOf(100), result);
    }

    @Test
    void writeMethodUsesWalletClientOnly() {
        String json = """
                [
                    {
                        "type": "function",
                        "name": "transfer",
                        "stateMutability": "nonpayable",
                        "inputs": [
                            {"name": "to", "type": "address"},
                            {"name": "amount", "type": "uint256"}
                        ],
                        "outputs": []
                    }
                ]
                """;

        interface TestContract {
            TransactionReceipt transfer(Address to, BigInteger amount);
        }

        AtomicBoolean publicClientCalled = new AtomicBoolean(false);
        AtomicBoolean walletClientCalled = new AtomicBoolean(false);

        PublicClient publicClient = new FakePublicClient() {
            @Override
            public String call(Map<String, Object> callObject, String blockTag) {
                publicClientCalled.set(true);
                throw new AssertionError("PublicClient should not be called for write functions");
            }
        };

        WalletClient walletClient = new FakeWalletClient() {
            @Override
            public TransactionReceipt sendTransactionAndWait(
                    io.brane.core.model.TransactionRequest request,
                    long timeoutMillis,
                    long pollIntervalMillis) {
                walletClientCalled.set(true);
                return new TransactionReceipt(
                        new Hash("0x" + "a".repeat(64)),
                        new Hash("0x" + "b".repeat(64)),
                        1L,
                        new Address("0x" + "1".repeat(40)),
                        new Address("0x" + "2".repeat(40)),
                        new io.brane.core.types.HexData("0x"),
                        java.util.List.of(),
                        true,
                        io.brane.core.types.Wei.of(21000));
            }
        };

        TestContract contract = BraneContract.bind(
                new Address("0x" + "1".repeat(40)),
                json,
                publicClient,
                walletClient,
                TestContract.class);

        TransactionReceipt receipt = contract.transfer(
                new Address("0x" + "2".repeat(40)),
                BigInteger.TEN);

        assertFalse(publicClientCalled.get(), "PublicClient should NOT be called");
        assertTrue(walletClientCalled.get(), "WalletClient should be called");
        assertNotNull(receipt);
    }

    @Test
    void handlesObjectMethods() {
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
            void foo();
        }

        PublicClient fakePublic = new FakePublicClient() {
        };
        WalletClient fakeWallet = new FakeWalletClient() {
        };

        TestContract contract = BraneContract.bind(
                new Address("0x" + "1".repeat(40)),
                json,
                fakePublic,
                fakeWallet,
                TestContract.class);

        assertNotNull(contract.toString());
        assertTrue(contract.toString().contains("BraneContractProxy"));
        assertEquals(contract, contract);
        assertNotEquals(contract, new Object());
    }

    // Minimal fake implementations
    private static abstract class FakePublicClient implements PublicClient {
        @Override
        public io.brane.core.model.BlockHeader getLatestBlock() {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.brane.core.model.BlockHeader getBlockByNumber(long blockNumber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.brane.core.model.Transaction getTransactionByHash(Hash hash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String call(Map<String, Object> callObject, String blockTag) {
            return "0x";
        }

        @Override
        public java.util.List<io.brane.core.model.LogEntry> getLogs(io.brane.rpc.LogFilter filter) {
            throw new UnsupportedOperationException();
        }
    }

    private static abstract class FakeWalletClient implements WalletClient {
        @Override
        public Hash sendTransaction(io.brane.core.model.TransactionRequest request) {
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
