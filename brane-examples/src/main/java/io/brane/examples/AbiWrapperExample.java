package io.brane.examples;

import io.brane.contract.Abi;
import io.brane.contract.BraneContract;
import io.brane.core.builder.TxBuilder;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.BraneProvider;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.PublicClient;
import io.brane.rpc.WalletClient;
import java.math.BigInteger;
import java.net.URI;

/**
 * Demo of Runtime ABI Wrapper Binding with ERC20.
 *
 * Usage:
 *   ./gradlew :brane-examples:run --no-daemon \
 *     -PmainClass=io.brane.examples.AbiWrapperExample \
 *     -Dbrane.examples.rpc=http://127.0.0.1:8545 \
 *     -Dbrane.examples.pk=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80 \
 *     -Dbrane.examples.contract=0x...
 */
public final class AbiWrapperExample {

    // BraneToken ABI (simpler than full ERC20)
    private static final String ERC20_ABI = """
            [
              {
                "name": "balanceOf",
                "type": "function",
                "stateMutability": "view",
                "inputs": [{"name": "account", "type": "address"}],
                "outputs": [{"name": "", "type": "uint256"}]
              },
              {
                "name": "transfer",
                "type": "function",
                "stateMutability": "nonpayable",
                "inputs": [
                  {"name": "to", "type": "address"},
                  {"name": "value", "type": "uint256"}
                ],
                "outputs": [{"name": "", "type": "bool"}]
              }
            ]
            """;

    private AbiWrapperExample() {}

    // Define the interface for the ERC20 contract
    interface Erc20Contract {
        BigInteger balanceOf(Address owner);
        TransactionReceipt transfer(Address to, BigInteger amount);
    }

    public static void main(final String[] args) {
        final String rpcUrl = System.getProperty("brane.examples.rpc");
        final String privateKey = System.getProperty("brane.examples.pk");
        final String contractAddr = System.getProperty("brane.examples.contract");

        if (isBlank(rpcUrl) || isBlank(privateKey) || isBlank(contractAddr)) {
            System.out.println("""
                    Please set the following system properties:
                      -Dbrane.examples.rpc=<RPC URL>
                      -Dbrane.examples.pk=<private key>
                      -Dbrane.examples.contract=<ERC20 contract address>
                    """);
            return;
        }

        try {
            final BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
            final PublicClient publicClient = PublicClient.from(provider);
            final var signer = new io.brane.rpc.PrivateKeyTransactionSigner(privateKey);
            final WalletClient walletClient = io.brane.rpc.DefaultWalletClient.create(
                    provider, publicClient, signer::sign, signer.address());

            System.out.println("=== Runtime ABI Wrapper Demo ===\n");

            // Bind the contract using BraneContract
            System.out.println("1. Binding contract via BraneContract.bind()...");
            final Address tokenAddress = new Address(contractAddr);
            final Erc20Contract token = BraneContract.bind(
                    tokenAddress,
                    ERC20_ABI,
                    publicClient,
                    walletClient,
                    Erc20Contract.class);
            System.out.println("   Bound to contract: " + tokenAddress.value());
            System.out.println("   Interface: " + Erc20Contract.class.getSimpleName() + "\n");

            // Call a view function (balanceOf)
            System.out.println("2. Calling view function: balanceOf()");
            final Address owner = signer.address();
            final BigInteger initialBalance = token.balanceOf(owner);
            System.out.println("   Owner address: " + owner.value());
            System.out.println("   Initial balance: " + initialBalance + "\n");

            // Call a write function (transfer)
            System.out.println("3. Calling write function: transfer()");
            final Address recipient = new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");
            final BigInteger transferAmount = BigInteger.valueOf(100);
            System.out.println("   Transferring " + transferAmount + " tokens to " + recipient.value());
            final TransactionReceipt receipt = token.transfer(recipient, transferAmount);
            System.out.println("   ✓ Transaction hash: " + receipt.transactionHash().value());
            System.out.println("   ✓ Block number: " + receipt.blockNumber());
            System.out.println("   ✓ Status: " + (receipt.status() ? "SUCCESS" : "FAILED") + "\n");

            // Verify the transfer
            System.out.println("4. Verifying balances after transfer:");
            final BigInteger ownerBalanceAfter = token.balanceOf(owner);
            final BigInteger recipientBalance = token.balanceOf(recipient);
            System.out.println("   Owner balance: " + ownerBalanceAfter + " (changed: " + initialBalance.subtract(ownerBalanceAfter) + ")");
            System.out.println("   Recipient balance: " + recipientBalance + "\n");

            System.out.println("=== Demo Complete ===");
            System.out.println("✓ Successfully bound Java interface to smart contract");
            System.out.println("✓ Called view method (balanceOf) via PublicClient");
            System.out.println("✓ Called write method (transfer) via WalletClient");
            System.out.println("✓ Verified state changes");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}

