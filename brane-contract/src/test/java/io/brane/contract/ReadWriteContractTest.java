package io.brane.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.brane.core.error.ChainMismatchException;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.rpc.PublicClient;
import io.brane.rpc.WalletClient;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ReadWriteContractTest {

    private static final String ERC20_ABI =
            """
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
        ReadWriteContract contract =
                ReadWriteContract.from(
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
        ReadWriteContract contract =
                ReadWriteContract.from(
                        new Address("0x" + "1".repeat(40)),
                        Abi.fromJson(ERC20_ABI),
                        new NoopPublicClient(),
                        walletClient);

        Address recipient = new Address("0x" + "2".repeat(40));
        TransactionReceipt receipt =
                contract.sendAndWait("transfer", 1000, 10, recipient, BigInteger.ONE);
        assertEquals("0x" + "a".repeat(64), receipt.transactionHash().value());
    }

    @Test
    void sendPropagatesTxnExceptions() {
        WalletClient failingWallet =
                new WalletClient() {
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

        ReadWriteContract contract =
                ReadWriteContract.from(
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

    private static final class CapturingWalletClient implements WalletClient {
        TransactionRequest lastRequest;

        @Override
        public Hash sendTransaction(final TransactionRequest request) {
            this.lastRequest = request;
            return new Hash("0x" + "a".repeat(64));
        }

        @Override
        public TransactionReceipt sendTransactionAndWait(
                final TransactionRequest request, final long timeoutMillis, final long pollIntervalMillis) {
            this.lastRequest = request;
            return new TransactionReceipt(
                    new Hash("0x" + "a".repeat(64)),
                    new Hash("0x" + "0".repeat(64)),
                    0L,
                    null,
                    null,
                    HexData.EMPTY,
                    java.util.List.of(),
                    true,
                    null);
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
        public String call(java.util.Map<String, Object> callObject, String blockTag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<io.brane.core.model.LogEntry> getLogs(final io.brane.rpc.LogFilter filter) {
            return java.util.Collections.emptyList();
        }
    }
}
