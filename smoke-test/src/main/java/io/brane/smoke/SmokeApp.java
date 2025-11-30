package io.brane.smoke;

import io.brane.contract.Abi;
import io.brane.contract.BraneContract;
import io.brane.contract.ReadOnlyContract;
import io.brane.contract.ReadWriteContract;
import io.brane.core.builder.TxBuilder;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.model.LogEntry;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.BraneProvider;
import io.brane.rpc.DefaultWalletClient;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.LogFilter;
import io.brane.rpc.PrivateKeyTransactionSigner;
import io.brane.rpc.PublicClient;
import io.brane.rpc.WalletClient;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SmokeApp {

    // Minimal ERC-20 ABI for testing
    private static final String ABI_JSON = """
        [
          {
            "inputs": [{"internalType": "uint256", "name": "initialSupply", "type": "uint256"}],
            "stateMutability": "nonpayable",
            "type": "constructor"
          },
          {
            "inputs": [{"internalType": "address", "name": "account", "type": "address"}],
            "name": "balanceOf",
            "outputs": [{"internalType": "uint256", "name": "", "type": "uint256"}],
            "stateMutability": "view",
            "type": "function"
          },
          {
            "inputs": [{"internalType": "address", "name": "to", "type": "address"}, {"internalType": "uint256", "name": "amount", "type": "uint256"}],
            "name": "transfer",
            "outputs": [{"internalType": "bool", "name": "", "type": "bool"}],
            "stateMutability": "nonpayable",
            "type": "function"
          },
          {
            "anonymous": false,
            "inputs": [
              {"indexed": true, "internalType": "address", "name": "from", "type": "address"},
              {"indexed": true, "internalType": "address", "name": "to", "type": "address"},
              {"indexed": false, "internalType": "uint256", "name": "value", "type": "uint256"}
            ],
            "name": "Transfer",
            "type": "event"
          }
        ]
        """;

    // Anvil Default Account 0
    private static final String PRIVATE_KEY = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final Address OWNER = new Address("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266");
    private static final Address RECIPIENT = new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");

    private static PublicClient publicClient;
    private static WalletClient walletClient;
    private static Address tokenAddress;
    private static Abi abi;

    public static void main(String[] args) {
        System.out.println("=== 🚀 Brane SDK Smoke Test Suite ===");
        
        try {
            setup();
            
            testCoreTransfer(); // Scenario A
            testErrorHandling(); // Scenario B
            testEventLogs(); // Scenario C
            testAbiWrapper(); // Scenario D
            
            System.out.println("\n✅ ALL SMOKE TESTS PASSED!");
            System.exit(0);
        } catch (Throwable t) {
            System.err.println("\n❌ SMOKE TEST FAILED!");
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static void setup() throws Exception {
        System.out.println("\n[Setup] Initializing clients...");
        
        String rpcUrl = System.getProperty("brane.smoke.rpc", "http://127.0.0.1:8545");
        BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
        
        publicClient = PublicClient.from(provider);
        walletClient = DefaultWalletClient.create(
            provider, 
            publicClient, 
            new PrivateKeyTransactionSigner(PRIVATE_KEY), 
            OWNER
        );
        abi = Abi.fromJson(ABI_JSON);
        
        System.out.println("✓ Clients ready. RPC: " + rpcUrl);
    }

    private static void testCoreTransfer() throws Exception {
        System.out.println("\n[Scenario A] End-to-End Token Transfer");

        // 1. Load Bytecode
        String bytecodeHex = new String(Objects.requireNonNull(
            SmokeApp.class.getResourceAsStream("/BraneToken.bin")).readAllBytes(), StandardCharsets.UTF_8).trim();
        
        // Ensure bytecode starts with 0x
        if (!bytecodeHex.startsWith("0x")) {
            bytecodeHex = "0x" + bytecodeHex;
        }

        // 2. Deploy
        System.out.println("  Deploying BraneToken...");
        BigInteger initialSupply = new BigInteger("1000000000000000000000"); // 1000 tokens
        HexData encodedArgs = abi.encodeConstructor(initialSupply);
        
        // Concatenate bytecode and args (strip 0x from args)
        String deployData = bytecodeHex + encodedArgs.value().substring(2);
        
        TransactionRequest deployRequest = TxBuilder.legacy()
                .data(new HexData(deployData))
                .build();

        TransactionReceipt receipt = walletClient.sendTransactionAndWait(deployRequest, 30_000, 1_000);
        
        if (!receipt.status()) throw new RuntimeException("Deployment failed!");
        tokenAddress = new Address(receipt.contractAddress().value()); // Convert HexData to Address
        System.out.println("  ✓ Deployed at: " + tokenAddress);

        // 3. Check Balance
        ReadOnlyContract contract = ReadOnlyContract.from(tokenAddress, abi, publicClient);
        BigInteger balance = contract.call("balanceOf", BigInteger.class, OWNER);
        if (!balance.equals(initialSupply)) throw new RuntimeException("Balance mismatch! Expected " + initialSupply + ", got " + balance);
        System.out.println("  ✓ Initial Balance Verified: " + balance);

        // 4. Transfer
        System.out.println("  Transferring 100 tokens...");
        BigInteger amount = new BigInteger("100");
        ReadWriteContract writeContract = ReadWriteContract.from(tokenAddress, abi, publicClient, walletClient);
        
        TransactionReceipt transferReceipt = writeContract.sendAndWait("transfer", 30_000, 1_000, RECIPIENT, amount);
        
        if (!transferReceipt.status()) throw new RuntimeException("Transfer failed!");
        System.out.println("  ✓ Transfer Mined: " + transferReceipt.transactionHash());

        // 5. Verify Recipient Balance
        BigInteger recipientBalance = contract.call("balanceOf", BigInteger.class, RECIPIENT);
        if (!recipientBalance.equals(amount)) throw new RuntimeException("Recipient balance mismatch!");
        System.out.println("  ✓ Recipient Balance Verified: " + recipientBalance);
    }

    private static void testErrorHandling() {
        System.out.println("\n[Scenario B] Error Handling (Resilience)");
        
        ReadWriteContract contract = ReadWriteContract.from(tokenAddress, abi, publicClient, walletClient);
        BigInteger excessiveAmount = new BigInteger("9999999999999999999999999999"); // More than supply
        
        try {
            System.out.println("  Attempting to transfer excessive amount (expecting revert)...");
            contract.sendAndWait("transfer", 30_000, 1_000, RECIPIENT, excessiveAmount);
            throw new RuntimeException("Should have reverted!");
        } catch (RevertException e) {
            System.out.println("  ✓ Caught Expected Revert: " + e.revertReason());
        } catch (RpcException e) {
             System.out.println("  ✓ Caught Expected RPC Error: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Unexpected exception type: " + e.getClass().getName(), e);
        }
    }

    private static void testEventLogs() throws Exception {
        System.out.println("\n[Scenario C] Event Logs (Observability)");
        
        List<LogEntry> logs = publicClient.getLogs(
            new LogFilter(
                Optional.empty(),
                Optional.empty(),
                Optional.of(tokenAddress),
                Optional.empty()
            )
        );
        
        System.out.println("  Found " + logs.size() + " logs.");
        if (logs.isEmpty()) throw new RuntimeException("No logs found!");
        
        // Check for Transfer to Recipient
        // Topic 2 is 'to' (indexed)
        // We need to pad the recipient address to 32 bytes to match the topic
        String recipientTopic = "0x000000000000000000000000" + RECIPIENT.value().substring(2);
        
        boolean foundTransfer = logs.stream().anyMatch(log -> 
            log.topics().size() >= 3 && 
            log.topics().get(2).value().equalsIgnoreCase(recipientTopic)
        );
        
        if (!foundTransfer) throw new RuntimeException("Did not find Transfer event to Recipient!");
        System.out.println("  ✓ Verified Transfer Event in logs.");
    }

    public interface Erc20 {
        BigInteger balanceOf(Address account);
        TransactionReceipt transfer(Address to, BigInteger amount);
    }

    private static void testAbiWrapper() {
        System.out.println("\n[Scenario D] ABI Wrapper (Usability)");
        
        // Bind the interface
        Erc20 token = BraneContract.bind(tokenAddress, ABI_JSON, publicClient, walletClient, Erc20.class);
        
        // Read
        BigInteger balance = token.balanceOf(RECIPIENT);
        System.out.println("  [Wrapper] Recipient Balance: " + balance);
        if (!balance.equals(new BigInteger("100"))) throw new RuntimeException("Wrapper read failed!");
        
        // Write (Transfer back 10 tokens)
        System.out.println("  [Wrapper] Transferring 10 tokens back...");
        
        TransactionReceipt receipt = token.transfer(RECIPIENT, BigInteger.TEN);
        
        if (!receipt.status()) throw new RuntimeException("Wrapper transfer failed!");
        System.out.println("  ✓ Wrapper Transfer Mined");
        
        BigInteger newBalance = token.balanceOf(RECIPIENT);
        if (!newBalance.equals(new BigInteger("110"))) throw new RuntimeException("Wrapper write verification failed!");
        System.out.println("  ✓ Final Balance Verified: " + newBalance);
    }
}
