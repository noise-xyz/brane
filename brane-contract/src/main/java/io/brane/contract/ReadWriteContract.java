package io.brane.contract;

import io.brane.core.builder.TxBuilder;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.PublicClient;
import io.brane.rpc.WalletClient;

public final class ReadWriteContract extends ReadOnlyContract {

    private final WalletClient walletClient;

    private ReadWriteContract(
            final Address address,
            final Abi abi,
            final PublicClient publicClient,
            final WalletClient walletClient) {
        super(address, abi, publicClient);
        this.walletClient = walletClient;
    }

    public static ReadWriteContract from(
            final Address address,
            final Abi abi,
            final PublicClient publicClient,
            final WalletClient walletClient) {
        return new ReadWriteContract(address, abi, publicClient, walletClient);
    }

    public Hash send(final String functionName, final Object... args) {
        final Abi.FunctionCall fnCall = abi().encodeFunction(functionName, args);
        final TransactionRequest request =
                TxBuilder.legacy()
                        .to(address())
                        .value(Wei.of(0))
                        .gasPrice(Wei.of(1))
                        .data(new HexData(fnCall.data()))
                        .build();
        return walletClient.sendTransaction(request);
    }

    public TransactionReceipt sendAndWait(
            final String functionName,
            final long timeoutMillis,
            final long pollIntervalMillis,
            final Object... args) {
        final Abi.FunctionCall fnCall = abi().encodeFunction(functionName, args);
        final TransactionRequest request =
                TxBuilder.legacy()
                        .to(address())
                        .value(Wei.of(0))
                        .gasPrice(Wei.of(1))
                        .data(new HexData(fnCall.data()))
                        .build();
        return walletClient.sendTransactionAndWait(request, timeoutMillis, pollIntervalMillis);
    }
}
