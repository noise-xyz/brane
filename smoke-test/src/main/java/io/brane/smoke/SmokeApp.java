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
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.BraneProvider;
import io.brane.rpc.DefaultWalletClient;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.LogFilter;
import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.crypto.Signer;
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

    private static BraneProvider provider;
    private static PublicClient publicClient;
    private static WalletClient walletClient;
    private static Address tokenAddress;
    private static Abi abi;

    private static Address complexAddress;
    private static Abi complexAbi;

    private static boolean sepoliaMode = false;

    public static void main(String[] args) {
        System.out.println("=== 🚀 Brane SDK Smoke Test Suite ===");

        for (String arg : args) {
            if (arg.equals("--sepolia")) {
                sepoliaMode = true;
                System.out.println("⚠️ Running in SEPOLIA mode (Read-Only)");
            }
        }

        try {
            setup();

            if (sepoliaMode) {
                testPublicClientReads(); // Scenario I
                testWeiUtilities(); // Scenario J
                testDebugAndColorMode(); // Scenario M
                testSepoliaSpecifics();
            } else {
                testCoreTransfer(); // Scenario A
                testErrorHandling(); // Scenario B
                testEventLogs(); // Scenario C
                testAbiWrapper(); // Scenario D
                testEip1559(); // Scenario E
                testRawSigning(); // Scenario F
                testCustomRpc(); // Scenario G
                testChainIdMismatch(); // Scenario H
                testPublicClientReads(); // Scenario I
                testWeiUtilities(); // Scenario J
                testComplexAbi(); // Scenario K
                testGasStrategy(); // Scenario L
                testDebugAndColorMode(); // Scenario M
                testCustomErrorDecoding(); // Scenario N
                testComplexNestedStructs(); // Scenario O
            }

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

        String defaultRpc = sepoliaMode ? "https://rpc.sepolia.org" : "http://127.0.0.1:8545";
        String rpcUrl = System.getProperty("brane.smoke.rpc", defaultRpc);

        // If env var BRANE_SEPOLIA_RPC is set and we are in sepolia mode, use it
        if (sepoliaMode) {
            // Use a more reliable public RPC
            rpcUrl = System.getenv().getOrDefault("BRANE_SEPOLIA_RPC", "https://ethereum-sepolia-rpc.publicnode.com");
        }

        provider = HttpBraneProvider.builder(rpcUrl).build();
        publicClient = PublicClient.from(provider);

        if (!sepoliaMode) {
            walletClient = DefaultWalletClient.create(
                    provider,
                    publicClient,
                    new PrivateKeySigner(PRIVATE_KEY),
                    OWNER);
        }

        abi = Abi.fromJson(ABI_JSON);

        System.out.println("✓ Clients ready. RPC: " + rpcUrl);
    }

    private static void testSepoliaSpecifics() {
        System.out.println("\n[Sepolia Specifics]");
        runSepoliaTask(() -> {
            BigInteger chainId = publicClient.getChainId();
            if (!chainId.equals(new BigInteger("11155111"))) {
                throw new RuntimeException("Sepolia Chain ID mismatch. Expected 11155111, got " + chainId);
            }
            System.out.println("  ✓ Chain ID Verified: " + chainId);

            // Check balance of zero address (should be > 0 usually, or just check it
            // doesn't crash)
            BigInteger balance = publicClient.getBalance(new Address("0x0000000000000000000000000000000000000000"));
            System.out.println("  ✓ Zero Address Balance: " + balance);
            System.out.println("  ✓ Balance Check Successful");
        });
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

        TransactionRequest deployRequest = BraneContract.deployRequest(ABI_JSON, bytecodeHex, initialSupply);

        TransactionReceipt receipt = walletClient.sendTransactionAndWait(deployRequest, 30_000, 1_000);

        if (!receipt.status())
            throw new RuntimeException("Deployment failed!");
        tokenAddress = new Address(receipt.contractAddress().value()); // Convert HexData to Address
        System.out.println("  ✓ Deployed at: " + tokenAddress);

        // 3. Check Balance
        ReadOnlyContract contract = ReadOnlyContract.from(tokenAddress, abi, publicClient);
        BigInteger balance = contract.call("balanceOf", BigInteger.class, OWNER);
        if (!balance.equals(initialSupply))
            throw new RuntimeException("Balance mismatch! Expected " + initialSupply + ", got " + balance);
        System.out.println("  ✓ Initial Balance Verified: " + balance);

        // 4. Transfer
        System.out.println("  Transferring 100 tokens...");
        BigInteger amount = new BigInteger("100");
        ReadWriteContract writeContract = ReadWriteContract.from(tokenAddress, abi, publicClient, walletClient);

        TransactionReceipt transferReceipt = writeContract.sendAndWait("transfer", 30_000, 1_000, RECIPIENT, amount);

        if (!transferReceipt.status())
            throw new RuntimeException("Transfer failed!");
        System.out.println("  ✓ Transfer Mined: " + transferReceipt.transactionHash());

        // 5. Verify Recipient Balance
        BigInteger recipientBalance = contract.call("balanceOf", BigInteger.class, RECIPIENT);
        if (!recipientBalance.equals(amount))
            throw new RuntimeException("Recipient balance mismatch!");
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
                        Optional.empty()));

        System.out.println("  Found " + logs.size() + " logs.");
        if (logs.isEmpty())
            throw new RuntimeException("No logs found!");

        // Check for Transfer to Recipient
        // Topic 2 is 'to' (indexed)
        String recipientTopic = io.brane.core.utils.Topics.fromAddress(RECIPIENT).value();

        boolean foundTransfer = logs.stream().anyMatch(log -> log.topics().size() >= 3 &&
                log.topics().get(2).value().equalsIgnoreCase(recipientTopic));

        if (!foundTransfer)
            throw new RuntimeException("Did not find Transfer event to Recipient!");
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
        if (!balance.equals(new BigInteger("100")))
            throw new RuntimeException("Wrapper read failed!");

        // Write (Transfer back 10 tokens)
        System.out.println("  [Wrapper] Transferring 10 tokens back...");

        TransactionReceipt receipt = token.transfer(RECIPIENT, BigInteger.TEN);

        if (!receipt.status())
            throw new RuntimeException("Wrapper transfer failed!");
        System.out.println("  ✓ Wrapper Transfer Mined");

        BigInteger newBalance = token.balanceOf(RECIPIENT);
        if (!newBalance.equals(new BigInteger("110")))
            throw new RuntimeException("Wrapper write verification failed!");
        System.out.println("  ✓ Final Balance Verified: " + newBalance);
    }

    private static void testEip1559() {
        System.out.println("\n[Scenario E] EIP-1559 & Access Lists");

        io.brane.core.model.AccessListEntry entry = new io.brane.core.model.AccessListEntry(
                tokenAddress,
                List.of() // No storage keys, just warming the address
        );

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.of(1))
                .accessList(List.of(entry))
                .build();

        TransactionReceipt receipt = walletClient.sendTransactionAndWait(request, 30_000, 1_000);

        if (!receipt.status())
            throw new RuntimeException("EIP-1559 transfer failed!");
        System.out.println("  ✓ EIP-1559 Transaction Mined: " + receipt.transactionHash());
    }

    private static void testRawSigning() {
        System.out.println("\n[Scenario F] Raw Transaction Signing");

        Signer signer = new PrivateKeySigner(PRIVATE_KEY);

        // Create a dummy legacy transaction
        io.brane.core.tx.LegacyTransaction tx = new io.brane.core.tx.LegacyTransaction(
                0, // nonce
                Wei.of(1000000000), // gasPrice
                21000, // gasLimit
                RECIPIENT, // to
                Wei.of(1), // value
                HexData.EMPTY // data
        );

        io.brane.core.crypto.Signature signature = signer.signTransaction(tx, 31337);

        if (signature == null) {
            throw new RuntimeException("Invalid signature generated: null");
        }
        System.out.println("  ✓ Generated Valid Signature: r=" + io.brane.primitives.Hex.encode(signature.r()) + "...");
    }

    private static void testCustomRpc() {
        System.out.println("\n[Scenario G] Custom RPC (anvil_mine)");

        // Mine a block manually
        io.brane.rpc.JsonRpcResponse response = provider.send("anvil_mine", List.of());

        if (response.hasError()) {
            throw new RuntimeException("Custom RPC call failed: " + response.error().message());
        }
        System.out.println("  ✓ Successfully called 'anvil_mine'");
    }

    private static void testChainIdMismatch() {
        System.out.println("\n[Scenario H] Chain ID Validation");

        // Create a client expecting Mainnet (Chain ID 1) but connected to Anvil (31337)
        WalletClient badClient = DefaultWalletClient.from(
                provider,
                publicClient,
                new PrivateKeySigner(PRIVATE_KEY),
                OWNER,
                1L // Expected: Mainnet
        );

        try {
            TransactionRequest req = TxBuilder.legacy()
                    .to(RECIPIENT)
                    .value(Wei.of(1))
                    .build();

            badClient.sendTransaction(req);
            throw new RuntimeException("Should have thrown ChainMismatchException!");
        } catch (io.brane.core.error.ChainMismatchException e) {
            System.out.println("  ✓ Caught Expected ChainMismatchException: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Unexpected exception: " + e.getClass().getName(), e);
        }
    }

    private static void testPublicClientReads() {
        System.out.println("\n[Scenario I] Public Client Read Ops");

        runSepoliaTask(() -> {
            io.brane.core.model.BlockHeader block = publicClient.getLatestBlock();
            if (block == null || block.number() == null)
                throw new RuntimeException("Failed to get latest block");
            System.out.println("  ✓ Latest Block: " + block.number());

            if (block.number() > 0) {
                io.brane.core.model.BlockHeader parent = publicClient.getBlockByNumber(block.number() - 1);
                if (!parent.hash().equals(block.parentHash()))
                    throw new RuntimeException("Parent hash mismatch");
                System.out.println("  ✓ Block Parent Hash Verified");
            }
        });
    }

    private static void testWeiUtilities() {
        System.out.println("\n[Scenario J] Wei Utilities");

        Wei oneEther = Wei.fromEther(java.math.BigDecimal.ONE);
        if (!oneEther.value().equals(new BigInteger("1000000000000000000")))
            throw new RuntimeException("Wei.fromEther(1) failed");

        Wei gwei = Wei.gwei(1);
        if (!gwei.value().equals(new BigInteger("1000000000")))
            throw new RuntimeException("Wei.gwei(1) failed");

        System.out.println("  ✓ Wei conversions verified");
    }

    private static void testComplexAbi() throws Exception {
        System.out.println("\n[Scenario K] Complex ABI (Arrays/Tuples)");

        // Load artifacts
        String bytecode = new String(Objects.requireNonNull(
                SmokeApp.class.getResourceAsStream("/ComplexContract.bin")).readAllBytes(), StandardCharsets.UTF_8)
                .trim();
        String abiJson = new String(Objects.requireNonNull(
                SmokeApp.class.getResourceAsStream("/ComplexContract.json")).readAllBytes(), StandardCharsets.UTF_8)
                .trim();

        if (!bytecode.startsWith("0x"))
            bytecode = "0x" + bytecode;

        // Deploy
        TransactionRequest deployReq = TxBuilder.legacy().data(new HexData(bytecode)).build();
        TransactionReceipt receipt = walletClient.sendTransactionAndWait(deployReq, 30_000, 1_000);
        if (!receipt.status())
            throw new RuntimeException("ComplexContract deployment failed");
        complexAddress = new Address(receipt.contractAddress().value());
        System.out.println("  ✓ Deployed ComplexContract at " + complexAddress);

        complexAbi = Abi.fromJson(abiJson);
        ReadOnlyContract contract = ReadOnlyContract.from(complexAddress, complexAbi, publicClient);

        // Test Array: processArray([1, 2]) -> [2, 4]
        List<BigInteger> input = List.of(BigInteger.ONE, BigInteger.TWO);
        @SuppressWarnings("unchecked")
        List<BigInteger> output = (List<BigInteger>) contract.call("processArray", List.class, input);

        if (!output.get(0).equals(BigInteger.TWO) || !output.get(1).equals(BigInteger.valueOf(4)))
            throw new RuntimeException("Array processing failed: " + output);
        System.out.println("  ✓ Array processing verified");

        // Test Fixed Bytes: processFixedBytes(bytes32)
        byte[] bytes32 = new byte[32];
        bytes32[0] = 0x12;
        HexData inputBytes = new HexData(io.brane.primitives.Hex.encode(bytes32));
        HexData outputBytes = contract.call("processFixedBytes", HexData.class, inputBytes);
        if (!outputBytes.value().equals(inputBytes.value()))
            throw new RuntimeException("Fixed bytes processing failed");
        System.out.println("  ✓ Fixed bytes verified");
    }

    private static void testGasStrategy() {
        System.out.println("\n[Scenario L] Gas Strategy Configuration");

        // Create client with 2.0x gas buffer
        WalletClient bufferedClient = DefaultWalletClient.from(
                provider,
                publicClient,
                new PrivateKeySigner(PRIVATE_KEY),
                OWNER,
                31337L,
                io.brane.core.chain.ChainProfile.of(31337L, "http://127.0.0.1:8545", true, Wei.of(1_000_000_000L)),
                BigInteger.valueOf(2), // Numerator
                BigInteger.ONE // Denominator
        );

        TransactionRequest req = TxBuilder.legacy()
                .to(RECIPIENT)
                .value(Wei.of(1))
                .build(); // No gas limit set, so it will estimate and apply buffer

        TransactionReceipt receipt = bufferedClient.sendTransactionAndWait(req, 30_000, 1_000);
        if (!receipt.status())
            throw new RuntimeException("Buffered transaction failed");
        System.out.println("  ✓ Buffered Gas Transaction Mined");
    }

    private static void testDebugAndColorMode() {
        System.out.println("\n[Scenario M] Debug & Color Mode");

        // Enable debug and color
        io.brane.core.BraneDebug.setEnabled(true);
        // Force color (simulated) - usually controlled by env var, but we can check if
        // logs appear
        // We can't easily capture stdout here without redirecting, but we can ensure it
        // doesn't crash

        System.out.println("  (Debug logging enabled - check stdout for cyan/teal logs)");

        runSepoliaTask(() -> {
            // Make a simple call to trigger logs
            publicClient.getChainId();
        });

        // Disable debug to keep subsequent output clean
        io.brane.core.BraneDebug.setEnabled(false);
        System.out.println("  ✓ Debug mode toggled and executed without error");
    }

    private static void testCustomErrorDecoding() {
        System.out.println("\n[Scenario N] Custom Error Decoding");

        // Use ReadOnlyContract to call the pure function via eth_call
        ReadOnlyContract contract = ReadOnlyContract.from(complexAddress, complexAbi, publicClient);

        try {
            System.out.println("  Attempting to trigger custom error (expecting revert)...");
            // We expect this to fail
            contract.call("revertWithCustomError", String.class, BigInteger.valueOf(404), "Not Found");

            throw new RuntimeException("Should have reverted with CustomError!");
        } catch (RevertException e) {
            // Decode the custom error from the raw data
            String data = e.rawDataHex();
            if (data == null) {
                throw new RuntimeException("RevertException caught but no data present: " + e.getMessage());
            }

            // Define the custom error ABI
            io.brane.core.RevertDecoder.CustomErrorAbi customError = new io.brane.core.RevertDecoder.CustomErrorAbi(
                    "CustomError",
                    List.of(new io.brane.core.abi.TypeSchema.UIntSchema(256),
                            new io.brane.core.abi.TypeSchema.StringSchema()));

            // "CustomError(uint256,string)" selector is needed.
            // keccak256("CustomError(uint256,string)") = 0x97ea5a2f
            String selector = Abi.getSelector("CustomError(uint256,string)");

            io.brane.core.RevertDecoder.Decoded decoded = io.brane.core.RevertDecoder.decode(
                    data,
                    java.util.Map.of(selector, customError));

            if (decoded.kind() != io.brane.core.RevertDecoder.RevertKind.CUSTOM) {
                throw new RuntimeException("Failed to decode custom error. Got: " + decoded.kind());
            }

            if (!decoded.reason().equals("CustomError(404, Not Found)")) {
                throw new RuntimeException("Unexpected decoded reason: " + decoded.reason());
            }

            System.out.println("  ✓ Decoded Custom Error: " + decoded.reason());
        } catch (RpcException e) {
            throw new RuntimeException(
                    "Unexpected RpcException (should be wrapped in RevertException): " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected exception: " + e.getClass().getName(), e);
        }
    }

    private static void testComplexNestedStructs() {
        System.out.println("\n[Scenario O] Complex Nested Structs");

        ReadOnlyContract contract = ReadOnlyContract.from(complexAddress, complexAbi, publicClient);

        // Struct Inner { uint256 a; string b; }
        // Struct Outer { Inner[] inners; bytes32 id; }

        // Construct Inner objects
        List<Object> inner1 = List.of(BigInteger.ONE, "inner1");
        List<Object> inner2 = List.of(BigInteger.TWO, "inner2");

        // Construct Outer object
        List<List<Object>> inners = List.of(inner1, inner2);

        byte[] idBytes = new byte[32];
        idBytes[0] = (byte) 0xAB;
        idBytes[31] = (byte) 0xCD;
        HexData id = new HexData(io.brane.primitives.Hex.encode(idBytes));

        List<Object> outer = List.of(inners, id);

        // Call processNested(Outer) -> Outer
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) contract.call("processNested", List.class, outer);

        // Verify result
        // Result structure: [List<List<Object>> (inners), HexData (id)]

        @SuppressWarnings("unchecked")
        List<List<Object>> resultInners = (List<List<Object>>) result.get(0);
        HexData resultId = (HexData) result.get(1);

        if (resultInners.size() != 2)
            throw new RuntimeException("Result inner array size mismatch");

        List<Object> resInner1 = resultInners.get(0);
        if (!resInner1.get(0).equals(BigInteger.ONE))
            throw new RuntimeException("Inner1.a mismatch");
        if (!resInner1.get(1).equals("inner1"))
            throw new RuntimeException("Inner1.b mismatch");

        if (!resultId.value().equals(id.value()))
            throw new RuntimeException("Outer.id mismatch");

        System.out.println("  ✓ Nested Struct Encoding/Decoding Verified");
    }

    private static void runSepoliaTask(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            if (sepoliaMode
                    && (e instanceof RpcException || e.getCause() instanceof java.net.http.HttpTimeoutException)) {
                System.out.println("  ⚠️ Sepolia Network Error (Expected on public RPC): " + e.getMessage());
            } else {
                if (e instanceof RuntimeException)
                    throw (RuntimeException) e;
                throw new RuntimeException(e);
            }
        }
    }
}
