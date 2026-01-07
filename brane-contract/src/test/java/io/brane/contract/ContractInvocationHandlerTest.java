package io.brane.contract;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.brane.core.error.AbiDecodingException;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.Wei;
import io.brane.rpc.PublicClient;
import io.brane.rpc.WalletClient;

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
            public io.brane.core.types.HexData call(io.brane.rpc.CallRequest request, io.brane.rpc.BlockTag blockTag) {
                publicClientCalled.set(true);
                // Return encoded uint256(100)
                return new io.brane.core.types.HexData("0x0000000000000000000000000000000000000000000000000000000000000064");
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
            public io.brane.core.types.HexData call(io.brane.rpc.CallRequest request, io.brane.rpc.BlockTag blockTag) {
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
                        null,
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

    @Test
    void viewMethodReturnsStringCorrectly() {
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

        // Encoded "TestToken" string:
        // offset (32) + length (9) + "TestToken" padded
        String encodedString = "0x"
                + "0000000000000000000000000000000000000000000000000000000000000020"  // offset
                + "0000000000000000000000000000000000000000000000000000000000000009"  // length=9
                + "54657374546f6b656e0000000000000000000000000000000000000000000000"; // "TestToken"

        PublicClient publicClient = new FakePublicClient() {
            @Override
            public io.brane.core.types.HexData call(io.brane.rpc.CallRequest request, io.brane.rpc.BlockTag blockTag) {
                return new io.brane.core.types.HexData(encodedString);
            }
        };

        WalletClient walletClient = new FakeWalletClient() {};

        TestContract contract = BraneContract.bind(
                new Address("0x" + "1".repeat(40)),
                json,
                publicClient,
                walletClient,
                TestContract.class);

        String result = contract.name();
        assertEquals("TestToken", result);
    }

    @Test
    void viewMethodDecodesRevertReason() {
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

        // Error(string) selector + "Insufficient balance" encoded
        String revertData = "0x08c379a0"
                + "0000000000000000000000000000000000000000000000000000000000000020"
                + "0000000000000000000000000000000000000000000000000000000000000014"
                + "496e73756666696369656e742062616c616e6365000000000000000000000000";

        PublicClient publicClient = new FakePublicClient() {
            @Override
            public io.brane.core.types.HexData call(io.brane.rpc.CallRequest request, io.brane.rpc.BlockTag blockTag) {
                throw new RpcException(-32000, "execution reverted", revertData);
            }
        };

        WalletClient walletClient = new FakeWalletClient() {};

        TestContract contract = BraneContract.bind(
                new Address("0x" + "1".repeat(40)),
                json,
                publicClient,
                walletClient,
                TestContract.class);

        RevertException ex = assertThrows(RevertException.class, () ->
                contract.balanceOf(new Address("0x" + "2".repeat(40))));

        assertEquals("Insufficient balance", ex.revertReason());
    }

    @Test
    void viewMethodThrowsOnEmptyResponse() {
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

        PublicClient publicClient = new FakePublicClient() {
            @Override
            public io.brane.core.types.HexData call(io.brane.rpc.CallRequest request, io.brane.rpc.BlockTag blockTag) {
                return new io.brane.core.types.HexData("0x");  // Empty response
            }
        };

        WalletClient walletClient = new FakeWalletClient() {};

        TestContract contract = BraneContract.bind(
                new Address("0x" + "1".repeat(40)),
                json,
                publicClient,
                walletClient,
                TestContract.class);

        AbiDecodingException ex = assertThrows(AbiDecodingException.class, () ->
                contract.balanceOf(new Address("0x" + "2".repeat(40))));

        assertTrue(ex.getMessage().contains("empty result"));
    }

    @Test
    void viewMethodThrowsOnNullResponse() {
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

        PublicClient publicClient = new FakePublicClient() {
            @Override
            public io.brane.core.types.HexData call(io.brane.rpc.CallRequest request, io.brane.rpc.BlockTag blockTag) {
                return null;  // Null response
            }
        };

        WalletClient walletClient = new FakeWalletClient() {};

        TestContract contract = BraneContract.bind(
                new Address("0x" + "1".repeat(40)),
                json,
                publicClient,
                walletClient,
                TestContract.class);

        AbiDecodingException ex = assertThrows(AbiDecodingException.class, () ->
                contract.balanceOf(new Address("0x" + "2".repeat(40))));

        assertTrue(ex.getMessage().contains("empty result"));
    }

    @Test
    void writeMethodAppliesGasLimitFromOptions() {
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

        AtomicReference<TransactionRequest> capturedRequest = new AtomicReference<>();

        PublicClient publicClient = new FakePublicClient() {};

        WalletClient walletClient = new FakeWalletClient() {
            @Override
            public TransactionReceipt sendTransactionAndWait(
                    TransactionRequest request,
                    long timeoutMillis,
                    long pollIntervalMillis) {
                capturedRequest.set(request);
                return new TransactionReceipt(
                        new Hash("0x" + "a".repeat(64)),
                        new Hash("0x" + "b".repeat(64)),
                        1L,
                        new Address("0x" + "1".repeat(40)),
                        new Address("0x" + "2".repeat(40)),
                        null,
                        java.util.List.of(),
                        true,
                        Wei.of(21000));
            }
        };

        ContractOptions customOptions = ContractOptions.builder()
                .gasLimit(500_000L)
                .build();

        TestContract contract = BraneContract.bind(
                new Address("0x" + "1".repeat(40)),
                json,
                publicClient,
                walletClient,
                TestContract.class,
                customOptions);

        contract.transfer(new Address("0x" + "2".repeat(40)), BigInteger.TEN);

        assertNotNull(capturedRequest.get());
        assertEquals(500_000L, capturedRequest.get().gasLimit());
    }

    @Test
    void writeMethodAppliesMaxPriorityFeeForEip1559() {
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

        AtomicReference<TransactionRequest> capturedRequest = new AtomicReference<>();

        PublicClient publicClient = new FakePublicClient() {};

        WalletClient walletClient = new FakeWalletClient() {
            @Override
            public TransactionReceipt sendTransactionAndWait(
                    TransactionRequest request,
                    long timeoutMillis,
                    long pollIntervalMillis) {
                capturedRequest.set(request);
                return new TransactionReceipt(
                        new Hash("0x" + "a".repeat(64)),
                        new Hash("0x" + "b".repeat(64)),
                        1L,
                        new Address("0x" + "1".repeat(40)),
                        new Address("0x" + "2".repeat(40)),
                        null,
                        java.util.List.of(),
                        true,
                        Wei.of(21000));
            }
        };

        Wei customPriorityFee = Wei.gwei(5);
        ContractOptions customOptions = ContractOptions.builder()
                .maxPriorityFee(customPriorityFee)
                .build();

        TestContract contract = BraneContract.bind(
                new Address("0x" + "1".repeat(40)),
                json,
                publicClient,
                walletClient,
                TestContract.class,
                customOptions);

        contract.transfer(new Address("0x" + "2".repeat(40)), BigInteger.TEN);

        assertNotNull(capturedRequest.get());
        assertEquals(customPriorityFee, capturedRequest.get().maxPriorityFeePerGas());
        assertTrue(capturedRequest.get().isEip1559(), "Should be EIP-1559 transaction");
    }

    @Test
    void writeMethodUsesLegacyTransactionWhenConfigured() {
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

        AtomicReference<TransactionRequest> capturedRequest = new AtomicReference<>();

        PublicClient publicClient = new FakePublicClient() {};

        WalletClient walletClient = new FakeWalletClient() {
            @Override
            public TransactionReceipt sendTransactionAndWait(
                    TransactionRequest request,
                    long timeoutMillis,
                    long pollIntervalMillis) {
                capturedRequest.set(request);
                return new TransactionReceipt(
                        new Hash("0x" + "a".repeat(64)),
                        new Hash("0x" + "b".repeat(64)),
                        1L,
                        new Address("0x" + "1".repeat(40)),
                        new Address("0x" + "2".repeat(40)),
                        null,
                        java.util.List.of(),
                        true,
                        Wei.of(21000));
            }
        };

        ContractOptions legacyOptions = ContractOptions.builder()
                .transactionType(ContractOptions.TransactionType.LEGACY)
                .gasLimit(100_000L)
                .build();

        TestContract contract = BraneContract.bind(
                new Address("0x" + "1".repeat(40)),
                json,
                publicClient,
                walletClient,
                TestContract.class,
                legacyOptions);

        contract.transfer(new Address("0x" + "2".repeat(40)), BigInteger.TEN);

        assertNotNull(capturedRequest.get());
        assertFalse(capturedRequest.get().isEip1559(), "Should be legacy transaction");
        assertEquals(100_000L, capturedRequest.get().gasLimit());
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
        public io.brane.core.types.HexData call(io.brane.rpc.CallRequest request, io.brane.rpc.BlockTag blockTag) {
            return io.brane.core.types.HexData.EMPTY;
        }

        @SuppressWarnings("deprecation")
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
