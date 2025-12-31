package io.brane.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.brane.core.error.ChainMismatchException;
import io.brane.core.abi.Abi;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.rpc.Subscription;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.PublicClient;
import io.brane.rpc.WalletClient;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ReadWriteContractTest {

    private static final String ERC20_ABI = """
            [
              {
                "inputs": [{ "internalType": "address", "name": "to", "type": "address" }, { "internalType": "uint256", "name": "amount", "type": "uint256" }],
                "name": "transfer",
                "outputs": [{ "internalType": "bool", "name": "", "type": "bool" }],
                "stateMutability": "nonpayable",
                "type": "function"
              }
            ]
            """;

    @Test
    void sendBuildsTransactionRequest() {
        CapturingWalletClient walletClient = new CapturingWalletClient();
        ReadWriteContract contract = ReadWriteContract.from(
                new Address("0x" + "1".repeat(40)),
                Abi.fromJson(ERC20_ABI),
                new NoopPublicClient(),
                walletClient);

        Address recipient = new Address("0x" + "2".repeat(40));
        contract.send("transfer", recipient, BigInteger.valueOf(10));

        TransactionRequest captured = walletClient.lastRequest;
        assertEquals("0x" + "1".repeat(40), captured.to().value());
        assertEquals(BigInteger.ZERO, captured.value().value());
        // Confirm selector present
        assertEquals("0xa9059cbb", captured.data().value().substring(0, 10));
    }

    @Test
    void sendAndWaitDelegates() {
        CapturingWalletClient walletClient = new CapturingWalletClient();
        ReadWriteContract contract = ReadWriteContract.from(
                new Address("0x" + "1".repeat(40)),
                Abi.fromJson(ERC20_ABI),
                new NoopPublicClient(),
                walletClient);

        Address recipient = new Address("0x" + "2".repeat(40));
        TransactionReceipt receipt = contract.sendAndWait("transfer", 1000, 10, recipient, BigInteger.ONE);
        assertEquals("0x" + "a".repeat(64), receipt.transactionHash().value());
    }

    @Test
    void sendPropagatesTxnExceptions() {
        WalletClient failingWallet = new WalletClient() {
            @Override
            public Hash sendTransaction(final TransactionRequest request) {
                throw new ChainMismatchException(1L, 5L);
            }

            @Override
            public TransactionReceipt sendTransactionAndWait(
                    final TransactionRequest request,
                    final long timeoutMillis,
                    final long pollIntervalMillis) {
                throw new ChainMismatchException(1L, 5L);
            }
        };

        ReadWriteContract contract = ReadWriteContract.from(
                new Address("0x" + "1".repeat(40)),
                Abi.fromJson(ERC20_ABI),
                new NoopPublicClient(),
                failingWallet);

        Address recipient = new Address("0x" + "2".repeat(40));
        assertThrows(ChainMismatchException.class, () -> contract.send("transfer", recipient, BigInteger.ONE));
        assertThrows(
                ChainMismatchException.class,
                () -> contract.sendAndWait("transfer", 1000, 10, recipient, BigInteger.ONE));
    }

    @Test
    void sendWithValueBuildsTransactionWithEthValue() {
        CapturingWalletClient walletClient = new CapturingWalletClient();
        ReadWriteContract contract = ReadWriteContract.from(
                new Address("0x" + "1".repeat(40)),
                Abi.fromJson(ERC20_ABI),
                new NoopPublicClient(),
                walletClient);

        Wei ethValue = Wei.fromEther(java.math.BigDecimal.ONE);
        Address recipient = new Address("0x" + "2".repeat(40));
        contract.send("transfer", ethValue, recipient, BigInteger.valueOf(10));

        TransactionRequest captured = walletClient.lastRequest;
        assertEquals("0x" + "1".repeat(40), captured.to().value());
        assertEquals(ethValue, captured.value());
        // Confirm selector present
        assertEquals("0xa9059cbb", captured.data().value().substring(0, 10));
    }

    @Test
    void sendAndWaitWithValueBuildsTransactionWithEthValue() {
        CapturingWalletClient walletClient = new CapturingWalletClient();
        ReadWriteContract contract = ReadWriteContract.from(
                new Address("0x" + "1".repeat(40)),
                Abi.fromJson(ERC20_ABI),
                new NoopPublicClient(),
                walletClient);

        Wei ethValue = Wei.gwei(500);
        Address recipient = new Address("0x" + "2".repeat(40));
        TransactionReceipt receipt = contract.sendAndWait("transfer", ethValue, 1000, 10, recipient, BigInteger.ONE);

        TransactionRequest captured = walletClient.lastRequest;
        assertEquals(ethValue, captured.value());
        assertEquals("0x" + "a".repeat(64), receipt.transactionHash().value());
    }

    @Test
    void sendWithNullValueThrows() {
        CapturingWalletClient walletClient = new CapturingWalletClient();
        ReadWriteContract contract = ReadWriteContract.from(
                new Address("0x" + "1".repeat(40)),
                Abi.fromJson(ERC20_ABI),
                new NoopPublicClient(),
                walletClient);

        Address recipient = new Address("0x" + "2".repeat(40));
        assertThrows(NullPointerException.class, () ->
                contract.send("transfer", (Wei) null, recipient, BigInteger.ONE));
    }

    @Test
    void sendAppliesContractOptionsGasLimit() {
        CapturingWalletClient walletClient = new CapturingWalletClient();
        ContractOptions options = ContractOptions.builder()
                .gasLimit(500_000L)
                .build();

        ReadWriteContract contract = ReadWriteContract.from(
                new Address("0x" + "1".repeat(40)),
                Abi.fromJson(ERC20_ABI),
                new NoopPublicClient(),
                walletClient,
                options);

        Address recipient = new Address("0x" + "2".repeat(40));
        contract.send("transfer", recipient, BigInteger.valueOf(10));

        TransactionRequest captured = walletClient.lastRequest;
        assertEquals(Long.valueOf(500_000L), captured.gasLimit());
    }

    @Test
    void sendAppliesContractOptionsMaxPriorityFee() {
        CapturingWalletClient walletClient = new CapturingWalletClient();
        Wei priorityFee = Wei.gwei(5);
        ContractOptions options = ContractOptions.builder()
                .maxPriorityFee(priorityFee)
                .build();

        ReadWriteContract contract = ReadWriteContract.from(
                new Address("0x" + "1".repeat(40)),
                Abi.fromJson(ERC20_ABI),
                new NoopPublicClient(),
                walletClient,
                options);

        Address recipient = new Address("0x" + "2".repeat(40));
        contract.send("transfer", recipient, BigInteger.valueOf(10));

        TransactionRequest captured = walletClient.lastRequest;
        assertEquals(priorityFee, captured.maxPriorityFeePerGas());
        assertEquals(true, captured.isEip1559());
    }

    @Test
    void sendAppliesLegacyTransactionType() {
        CapturingWalletClient walletClient = new CapturingWalletClient();
        ContractOptions options = ContractOptions.builder()
                .transactionType(ContractOptions.TransactionType.LEGACY)
                .gasLimit(100_000L)
                .build();

        ReadWriteContract contract = ReadWriteContract.from(
                new Address("0x" + "1".repeat(40)),
                Abi.fromJson(ERC20_ABI),
                new NoopPublicClient(),
                walletClient,
                options);

        Address recipient = new Address("0x" + "2".repeat(40));
        contract.send("transfer", recipient, BigInteger.valueOf(10));

        TransactionRequest captured = walletClient.lastRequest;
        assertEquals(false, captured.isEip1559());
        assertEquals(Long.valueOf(100_000L), captured.gasLimit());
    }

    @Test
    void sendAndWaitAppliesContractOptionsGasLimit() {
        CapturingWalletClient walletClient = new CapturingWalletClient();
        Wei priorityFee = Wei.gwei(3);
        ContractOptions options = ContractOptions.builder()
                .gasLimit(250_000L)
                .maxPriorityFee(priorityFee)
                .build();

        ReadWriteContract contract = ReadWriteContract.from(
                new Address("0x" + "1".repeat(40)),
                Abi.fromJson(ERC20_ABI),
                new NoopPublicClient(),
                walletClient,
                options);

        Address recipient = new Address("0x" + "2".repeat(40));
        contract.sendAndWait("transfer", 5000L, 200L, recipient, BigInteger.valueOf(10));

        TransactionRequest captured = walletClient.lastRequest;
        assertEquals(Long.valueOf(250_000L), captured.gasLimit());
        assertEquals(priorityFee, captured.maxPriorityFeePerGas());
        assertEquals(true, captured.isEip1559());
    }

    private static final class CapturingWalletClient implements WalletClient {
        TransactionRequest lastRequest;
        long lastTimeoutMillis;
        long lastPollIntervalMillis;

        @Override
        public Hash sendTransaction(final TransactionRequest request) {
            this.lastRequest = request;
            return new Hash("0x" + "a".repeat(64));
        }

        @Override
        public TransactionReceipt sendTransactionAndWait(
                final TransactionRequest request, final long timeoutMillis, final long pollIntervalMillis) {
            this.lastRequest = request;
            this.lastTimeoutMillis = timeoutMillis;
            this.lastPollIntervalMillis = pollIntervalMillis;
            return new TransactionReceipt(
                    new Hash("0x" + "a".repeat(64)),
                    new Hash("0x" + "0".repeat(64)),
                    0L,
                    new Address("0x" + "1".repeat(40)),  // from address
                    null,  // to can be null for contract creation
                    null,  // contractAddress null for non-deployment
                    java.util.List.of(),
                    true,
                    Wei.of(21000L));
        }
    }

    private static final class NoopPublicClient implements PublicClient {
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
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("deprecation")
        @Override
        public String call(java.util.Map<String, Object> callObject, String blockTag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<io.brane.core.model.LogEntry> getLogs(final io.brane.rpc.LogFilter filter) {
            return java.util.Collections.emptyList();
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
