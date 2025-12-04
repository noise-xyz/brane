package io.brane.examples;

import io.brane.contract.Abi;
import io.brane.contract.ReadWriteContract;
import io.brane.core.error.RpcException;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.rpc.BraneProvider;
import io.brane.core.crypto.PrivateKeySigner;
import io.brane.rpc.DefaultWalletClient;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.PublicClient;
import io.brane.core.crypto.Signer;
import io.brane.rpc.WalletClient;
import java.math.BigInteger;

/**
 * Example usage:
 * ./gradlew :brane-examples:run --no-daemon \
 *   -PmainClass=io.brane.examples.Erc20TransferExample \
 *   -Dbrane.examples.erc20.rpc=http://127.0.0.1:8545 \
 *   -Dbrane.examples.erc20.contract=0x... \
 *   -Dbrane.examples.erc20.recipient=0x... \
 *   -Dbrane.examples.erc20.pk=0x...
 */
public final class Erc20TransferExample {

    private static final String ERC20_ABI =
            """
            [
              {
                "inputs": [],
                "name": "decimals",
                "outputs": [{ "internalType": "uint8", "name": "", "type": "uint8" }],
                "stateMutability": "view",
                "type": "function"
              },
              {
                "inputs": [
                  { "internalType": "address", "name": "to", "type": "address" },
                  { "internalType": "uint256", "name": "amount", "type": "uint256" }
                ],
                "name": "transfer",
                "outputs": [{ "internalType": "bool", "name": "", "type": "bool" }],
                "stateMutability": "nonpayable",
                "type": "function"
              }
            ]
            """;

    private Erc20TransferExample() {}

    public static void main(final String[] args) throws Exception {
        final String rpcUrl = System.getProperty("brane.examples.erc20.rpc");
        final String tokenAddress = System.getProperty("brane.examples.erc20.contract");
        final String recipientAddress = System.getProperty("brane.examples.erc20.recipient");
        final String privateKey = System.getProperty("brane.examples.erc20.pk");
        final String amountStr = System.getProperty("brane.examples.erc20.amount", "1");

        if (isBlank(rpcUrl)
                || isBlank(tokenAddress)
                || isBlank(recipientAddress)
                || isBlank(privateKey)) {
            System.out.println(
                    """
                    Please set the following system properties:
                      -Dbrane.examples.erc20.rpc=<RPC URL>
                      -Dbrane.examples.erc20.contract=<ERC-20 contract address>
                      -Dbrane.examples.erc20.recipient=<recipient address>
                      -Dbrane.examples.erc20.pk=<private key hex>
                    Optional: -Dbrane.examples.erc20.amount=<amount in token units>
                    """);
            return;
        }

        final Address token = new Address(tokenAddress);
        final Address recipient = new Address(recipientAddress);
        final BigInteger amount = new BigInteger(amountStr);

        final BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
        final PublicClient publicClient = PublicClient.from(provider);
        final PrivateKeySigner signer = new PrivateKeySigner(privateKey);
        final WalletClient walletClient =
                DefaultWalletClient.create(provider, publicClient, signer, signer.address());

        final Abi abi = Abi.fromJson(ERC20_ABI);
        final ReadWriteContract contract =
                ReadWriteContract.from(token, abi, publicClient, walletClient);

        try {
            final Hash txHash = contract.send("transfer", recipient, amount);
            System.out.println("Sent transfer tx: " + txHash.value());

            final TransactionReceipt receipt =
                    contract.sendAndWait("transfer", 30_000L, 1_000L, recipient, amount);
            System.out.println("Mined tx in block: " + receipt.blockNumber());
        } catch (RpcException e) {
            System.err.println("RPC error (" + e.code() + "): " + e.getMessage());
        }
    }

    private static boolean isBlank(final String value) {
        return value == null || value.trim().isEmpty();
    }
}
