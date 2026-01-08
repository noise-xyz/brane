package io.brane.smoke;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.brane.contract.BraneContract;
import io.brane.core.BraneDebug;
import io.brane.core.RevertDecoder;
import io.brane.core.RevertDecoder.CustomErrorAbi;
import io.brane.core.RevertDecoder.Decoded;
import io.brane.core.RevertDecoder.RevertKind;
import io.brane.core.abi.Abi;
import io.brane.core.abi.TypeSchema.StringSchema;
import io.brane.core.abi.TypeSchema.UIntSchema;
import io.brane.core.builder.TxBuilder;
import io.brane.core.chain.ChainProfile;
import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.crypto.Signature;
import io.brane.core.crypto.Signer;
import io.brane.core.error.ChainMismatchException;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.model.AccessListEntry;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.LogEntry;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.tx.LegacyTransaction;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.core.util.Topics;
import io.brane.primitives.Hex;
import io.brane.rpc.AccountOverride;
import io.brane.rpc.Brane;
import io.brane.rpc.BraneProvider;
import io.brane.rpc.CallRequest;
import io.brane.rpc.CallResult;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.JsonRpcResponse;
import io.brane.rpc.LogFilter;
import io.brane.rpc.SimulateCall;
import io.brane.rpc.SimulateRequest;
import io.brane.rpc.SimulateResult;
import io.brane.rpc.Subscription;
import io.brane.rpc.WebSocketProvider;
import io.brane.rpc.exception.SimulateNotSupportedException;

public class SmokeApp {

    // Timeout constants for transaction waiting
    private static final long WAIT_TIMEOUT_MS = 30_000;
    private static final long POLL_INTERVAL_MS = 1_000;

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
    private static Brane client;
    private static Brane.Signer signerClient;
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
                testWebSocket(); // Scenario P
                testSimulateCalls(); // Scenario Q
                testEip712TypeSafeSigning(); // Scenario R
                testEip712DynamicSigning(); // Scenario S
                testEip712JsonParsing(); // Scenario T
                testTesterOperations(); // Scenario U
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

        // Keep provider reference for raw RPC calls (anvil_mine, etc.)
        provider = HttpBraneProvider.builder(rpcUrl).build();

        if (sepoliaMode) {
            // Read-only client for Sepolia using new Brane API
            client = Brane.connect(rpcUrl);
        } else {
            // Signing client for Anvil using new Brane API
            signerClient = Brane.connect(rpcUrl, new PrivateKeySigner(PRIVATE_KEY));
            client = signerClient;
        }

        abi = Abi.fromJson(ABI_JSON);

        System.out.println("✓ Clients ready. RPC: " + rpcUrl);
    }

    private static void testSepoliaSpecifics() {
        System.out.println("\n[Sepolia Specifics]");
        runSepoliaTask(() -> {
            BigInteger chainId = client.chainId();
            if (!chainId.equals(new BigInteger("11155111"))) {
                throw new RuntimeException("Sepolia Chain ID mismatch. Expected 11155111, got " + chainId);
            }
            System.out.println("  ✓ Chain ID Verified: " + chainId);

            // Check balance of zero address (should be > 0 usually, or just check it
            // doesn't crash)
            BigInteger balance = client.getBalance(new Address("0x0000000000000000000000000000000000000000"));
            System.out.println("  ✓ Zero Address Balance: " + balance);
            System.out.println("  ✓ Balance Check Successful");
        });
    }

    private static void testCoreTransfer() throws Exception {
        System.out.println("\n[Scenario A] End-to-End Token Transfer");

        // 1. Load Bytecode
        String bytecodeHex;
        try (var resourceStream = Objects.requireNonNull(
                SmokeApp.class.getResourceAsStream("/BraneToken.bin"))) {
            bytecodeHex = new String(resourceStream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        // Ensure bytecode starts with 0x
        if (!bytecodeHex.startsWith("0x")) {
            bytecodeHex = "0x" + bytecodeHex;
        }

        // 2. Deploy
        System.out.println("  Deploying BraneToken...");
        BigInteger initialSupply = new BigInteger("1000000000000000000000"); // 1000 tokens

        TransactionRequest deployRequest = BraneContract.deployRequest(ABI_JSON, bytecodeHex, initialSupply);

        TransactionReceipt receipt = signerClient.sendTransactionAndWait(deployRequest, WAIT_TIMEOUT_MS, POLL_INTERVAL_MS);

        if (!receipt.status()) {
            throw new RuntimeException("Deployment failed!");
        }
        tokenAddress = new Address(receipt.contractAddress().value()); // Convert HexData to Address
        System.out.println("  ✓ Deployed at: " + tokenAddress);

        // 3. Check Balance using new Brane API
        Erc20 token = BraneContract.bind(tokenAddress, ABI_JSON, signerClient, Erc20.class);
        BigInteger balance = token.balanceOf(OWNER);
        if (!balance.equals(initialSupply)) {
            throw new RuntimeException("Balance mismatch! Expected " + initialSupply + ", got " + balance);
        }
        System.out.println("  ✓ Initial Balance Verified: " + balance);

        // 4. Transfer using new Brane API
        System.out.println("  Transferring 100 tokens...");
        BigInteger amount = new BigInteger("100");

        TransactionReceipt transferReceipt = token.transfer(RECIPIENT, amount);

        if (!transferReceipt.status()) {
            throw new RuntimeException("Transfer failed!");
        }
        System.out.println("  ✓ Transfer Mined: " + transferReceipt.transactionHash());

        // 5. Verify Recipient Balance
        BigInteger recipientBalance = token.balanceOf(RECIPIENT);
        if (!recipientBalance.equals(amount)) {
            throw new RuntimeException("Recipient balance mismatch!");
        }
        System.out.println("  ✓ Recipient Balance Verified: " + recipientBalance);
    }

    private static void testErrorHandling() {
        System.out.println("\n[Scenario B] Error Handling (Resilience)");

        // Use new Brane API for contract binding
        Erc20 token = BraneContract.bind(tokenAddress, ABI_JSON, signerClient, Erc20.class);
        BigInteger excessiveAmount = new BigInteger("9999999999999999999999999999"); // More than supply

        try {
            System.out.println("  Attempting to transfer excessive amount (expecting revert)...");
            token.transfer(RECIPIENT, excessiveAmount);
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

        // Specify fromBlock=0 to query all blocks, not just "latest"
        // Use new Brane API
        List<LogEntry> logs = client.getLogs(
                new LogFilter(
                        Optional.of(0L),
                        Optional.empty(),
                        Optional.of(List.of(tokenAddress)),
                        Optional.empty()));

        System.out.println("  Found " + logs.size() + " logs.");
        if (logs.isEmpty()) {
            throw new RuntimeException("No logs found!");
        }

        // Check for Transfer to Recipient
        // Topic 2 is 'to' (indexed)
        String recipientTopic = Topics.fromAddress(RECIPIENT).value();

        boolean foundTransfer = logs.stream().anyMatch(log -> log.topics().size() >= 3 &&
                log.topics().get(2).value().equalsIgnoreCase(recipientTopic));

        if (!foundTransfer) {
            throw new RuntimeException("Did not find Transfer event to Recipient!");
        }
        System.out.println("  ✓ Verified Transfer Event in logs.");
    }

    public interface Erc20 {
        BigInteger balanceOf(Address account);

        TransactionReceipt transfer(Address to, BigInteger amount);
    }

    private static void testAbiWrapper() {
        System.out.println("\n[Scenario D] ABI Wrapper (Usability)");

        // Bind the interface using new Brane API
        Erc20 token = BraneContract.bind(tokenAddress, ABI_JSON, signerClient, Erc20.class);

        // Read
        BigInteger balance = token.balanceOf(RECIPIENT);
        System.out.println("  [Wrapper] Recipient Balance: " + balance);
        if (!balance.equals(new BigInteger("100"))) {
            throw new RuntimeException("Wrapper read failed!");
        }

        // Write (Transfer back 10 tokens)
        System.out.println("  [Wrapper] Transferring 10 tokens back...");

        TransactionReceipt receipt = token.transfer(RECIPIENT, BigInteger.TEN);

        if (!receipt.status()) {
            throw new RuntimeException("Wrapper transfer failed!");
        }
        System.out.println("  ✓ Wrapper Transfer Mined");

        BigInteger newBalance = token.balanceOf(RECIPIENT);
        if (!newBalance.equals(new BigInteger("110"))) {
            throw new RuntimeException("Wrapper write verification failed!");
        }
        System.out.println("  ✓ Final Balance Verified: " + newBalance);
    }

    private static void testEip1559() {
        System.out.println("\n[Scenario E] EIP-1559 & Access Lists");

        AccessListEntry entry = new AccessListEntry(
                tokenAddress,
                List.of() // No storage keys, just warming the address
        );

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.of(1))
                .accessList(List.of(entry))
                .build();

        // Use new Brane API
        TransactionReceipt receipt = signerClient.sendTransactionAndWait(request, WAIT_TIMEOUT_MS, POLL_INTERVAL_MS);

        if (!receipt.status()) {
            throw new RuntimeException("EIP-1559 transfer failed!");
        }
        System.out.println("  ✓ EIP-1559 Transaction Mined: " + receipt.transactionHash());
    }

    private static void testRawSigning() {
        System.out.println("\n[Scenario F] Raw Transaction Signing");

        Signer signer = new PrivateKeySigner(PRIVATE_KEY);

        // Create a dummy legacy transaction
        LegacyTransaction tx = new LegacyTransaction(
                0, // nonce
                Wei.of(1000000000), // gasPrice
                21000, // gasLimit
                RECIPIENT, // to
                Wei.of(1), // value
                HexData.EMPTY // data
        );

        Signature signature = signer.signTransaction(tx, 31337);

        if (signature == null) {
            throw new RuntimeException("Invalid signature generated: null");
        }
        System.out.println("  ✓ Generated Valid Signature: r=" + Hex.encode(signature.r()) + "...");
    }

    private static void testCustomRpc() {
        System.out.println("\n[Scenario G] Custom RPC (anvil_mine)");

        // Mine a block manually
        JsonRpcResponse response = provider.send("anvil_mine", List.of());

        if (response.hasError()) {
            throw new RuntimeException("Custom RPC call failed: " + response.error().message());
        }
        System.out.println("  ✓ Successfully called 'anvil_mine'");
    }

    private static void testChainIdMismatch() {
        System.out.println("\n[Scenario H] Chain ID Validation");

        // Create a client expecting Mainnet (Chain ID 1) but connected to Anvil (31337)
        Brane.Signer badClient = Brane.builder()
                .rpcUrl("http://127.0.0.1:8545")
                .signer(new PrivateKeySigner(PRIVATE_KEY))
                .chain(ChainProfile.of(1L, "http://127.0.0.1:8545", false, Wei.gwei(1))) // Expected: Mainnet (chain id 1)
                .buildSigner();

        try {
            TransactionRequest req = TxBuilder.legacy()
                    .to(RECIPIENT)
                    .value(Wei.of(1))
                    .build();

            badClient.sendTransaction(req);
            throw new RuntimeException("Should have thrown ChainMismatchException!");
        } catch (ChainMismatchException e) {
            System.out.println("  ✓ Caught Expected ChainMismatchException: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Unexpected exception: " + e.getClass().getName(), e);
        } finally {
            try {
                badClient.close();
            } catch (Exception ignored) {
                // Ignore close exceptions
            }
        }
    }

    private static void testPublicClientReads() {
        System.out.println("\n[Scenario I] Brane Client Read Ops");

        runSepoliaTask(() -> {
            // Use new Brane API
            BlockHeader block = client.getLatestBlock();
            if (block == null) {
                throw new RuntimeException("Failed to get latest block");
            }
            System.out.println("  ✓ Latest Block: " + block.number());

            if (block.number() > 0) {
                BlockHeader parent = client.getBlockByNumber(block.number() - 1);
                if (!parent.hash().equals(block.parentHash())) {
                    throw new RuntimeException("Parent hash mismatch");
                }
                System.out.println("  ✓ Block Parent Hash Verified");
            }
        });
    }

    private static void testWeiUtilities() {
        System.out.println("\n[Scenario J] Wei Utilities");

        Wei oneEther = Wei.fromEther(BigDecimal.ONE);
        if (!oneEther.value().equals(new BigInteger("1000000000000000000"))) {
            throw new RuntimeException("Wei.fromEther(1) failed");
        }

        Wei gwei = Wei.gwei(1);
        if (!gwei.value().equals(new BigInteger("1000000000"))) {
            throw new RuntimeException("Wei.gwei(1) failed");
        }

        System.out.println("  ✓ Wei conversions verified");
    }

    private static void testComplexAbi() throws Exception {
        System.out.println("\n[Scenario K] Complex ABI (Arrays/Tuples)");

        // Load artifacts
        String bytecode;
        try (var binStream = Objects.requireNonNull(
                SmokeApp.class.getResourceAsStream("/ComplexContract.bin"))) {
            bytecode = new String(binStream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        String abiJson;
        try (var jsonStream = Objects.requireNonNull(
                SmokeApp.class.getResourceAsStream("/ComplexContract.json"))) {
            abiJson = new String(jsonStream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        if (!bytecode.startsWith("0x")) {
            bytecode = "0x" + bytecode;
        }

        // Deploy using new Brane API
        TransactionRequest deployReq = TxBuilder.legacy().data(new HexData(bytecode)).build();
        TransactionReceipt receipt = signerClient.sendTransactionAndWait(deployReq, WAIT_TIMEOUT_MS, POLL_INTERVAL_MS);
        if (!receipt.status()) {
            throw new RuntimeException("ComplexContract deployment failed");
        }
        complexAddress = new Address(receipt.contractAddress().value());
        System.out.println("  ✓ Deployed ComplexContract at " + complexAddress);

        complexAbi = Abi.fromJson(abiJson);

        // Test Array: processArray([1, 2]) -> [2, 4]
        // Using manual ABI encoding/decoding via Brane.call()
        List<BigInteger> input = List.of(BigInteger.ONE, BigInteger.TWO);
        Abi.FunctionCall processArrayCall = complexAbi.encodeFunction("processArray", input);
        HexData arrayResult = client.call(CallRequest.builder()
                .to(complexAddress)
                .data(new HexData(processArrayCall.data()))
                .build());

        @SuppressWarnings("unchecked")
        List<BigInteger> output = processArrayCall.decode(arrayResult.value(), List.class);

        if (!output.get(0).equals(BigInteger.TWO) || !output.get(1).equals(BigInteger.valueOf(4))) {
            throw new RuntimeException("Array processing failed: " + output);
        }
        System.out.println("  ✓ Array processing verified");

        // Test Fixed Bytes: processFixedBytes(bytes32)
        byte[] bytes32 = new byte[32];
        bytes32[0] = 0x12;
        HexData inputBytes = new HexData(Hex.encode(bytes32));
        Abi.FunctionCall processBytesCall = complexAbi.encodeFunction("processFixedBytes", inputBytes);
        HexData bytesResult = client.call(CallRequest.builder()
                .to(complexAddress)
                .data(new HexData(processBytesCall.data()))
                .build());

        HexData outputBytes = processBytesCall.decode(bytesResult.value(), HexData.class);
        if (!outputBytes.value().equals(inputBytes.value())) {
            throw new RuntimeException("Fixed bytes processing failed");
        }
        System.out.println("  ✓ Fixed bytes verified");
    }

    private static void testGasStrategy() {
        System.out.println("\n[Scenario L] Gas Strategy Configuration");

        // Test that the default gas strategy correctly estimates gas
        // The signerClient uses SmartGasStrategy which applies a buffer
        TransactionRequest req = TxBuilder.legacy()
                .to(RECIPIENT)
                .value(Wei.of(1))
                .build(); // No gas limit set, so it will estimate and apply buffer

        TransactionReceipt receipt = signerClient.sendTransactionAndWait(req, WAIT_TIMEOUT_MS, POLL_INTERVAL_MS);
        if (!receipt.status()) {
            throw new RuntimeException("Gas strategy transaction failed");
        }
        System.out.println("  ✓ Gas Strategy Transaction Mined");
    }

    private static void testDebugAndColorMode() {
        System.out.println("\n[Scenario M] Debug & Color Mode");

        // Enable debug and color
        BraneDebug.setEnabled(true);
        // Force color (simulated) - usually controlled by env var, but we can check if
        // logs appear
        // We can't easily capture stdout here without redirecting, but we can ensure it
        // doesn't crash

        System.out.println("  (Debug logging enabled - check stdout for cyan/teal logs)");

        runSepoliaTask(() -> {
            // Make a simple call to trigger logs using new Brane API
            client.chainId();
        });

        // Disable debug to keep subsequent output clean
        BraneDebug.setEnabled(false);
        System.out.println("  ✓ Debug mode toggled and executed without error");
    }

    private static void testCustomErrorDecoding() {
        System.out.println("\n[Scenario N] Custom Error Decoding");

        try {
            System.out.println("  Attempting to trigger custom error (expecting revert)...");
            // Encode and call function that reverts with CustomError
            Abi.FunctionCall revertCall = complexAbi.encodeFunction("revertWithCustomError",
                    BigInteger.valueOf(404), "Not Found");
            client.call(CallRequest.builder()
                    .to(complexAddress)
                    .data(new HexData(revertCall.data()))
                    .build());

            throw new RuntimeException("Should have reverted with CustomError!");
        } catch (RevertException e) {
            // Decode the custom error from the raw data
            String data = e.rawDataHex();
            if (data == null) {
                throw new RuntimeException("RevertException caught but no data present: " + e.getMessage());
            }

            // Define the custom error ABI
            CustomErrorAbi customError = new CustomErrorAbi(
                    "CustomError",
                    List.of(new UIntSchema(256),
                            new StringSchema()));

            // "CustomError(uint256,string)" selector is needed.
            // keccak256("CustomError(uint256,string)") = 0x97ea5a2f
            String selector = Abi.getSelector("CustomError(uint256,string)");

            Decoded decoded = RevertDecoder.decode(
                    data,
                    Map.of(selector, customError));

            if (decoded.kind() != RevertKind.CUSTOM) {
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
        HexData id = new HexData(Hex.encode(idBytes));

        List<Object> outer = List.of(inners, id);

        // Encode and call processNested(Outer) -> Outer
        Abi.FunctionCall processNestedCall = complexAbi.encodeFunction("processNested", outer);
        HexData nestedResult = client.call(CallRequest.builder()
                .to(complexAddress)
                .data(new HexData(processNestedCall.data()))
                .build());

        @SuppressWarnings("unchecked")
        List<Object> result = processNestedCall.decode(nestedResult.value(), List.class);

        // Verify result
        // Result structure: [List<List<Object>> (inners), HexData (id)]

        @SuppressWarnings("unchecked")
        List<List<Object>> resultInners = (List<List<Object>>) result.get(0);
        HexData resultId = (HexData) result.get(1);

        if (resultInners.size() != 2) {
            throw new RuntimeException("Result inner array size mismatch");
        }

        List<Object> resInner1 = resultInners.get(0);
        if (!resInner1.get(0).equals(BigInteger.ONE)) {
            throw new RuntimeException("Inner1.a mismatch");
        }
        if (!resInner1.get(1).equals("inner1")) {
            throw new RuntimeException("Inner1.b mismatch");
        }

        if (!resultId.value().equals(id.value())) {
            throw new RuntimeException("Outer.id mismatch");
        }

        System.out.println("  ✓ Nested Struct Encoding/Decoding Verified");
    }

    private static void testWebSocket() throws Exception {
        System.out.println("\n[Scenario P] WebSocket Transport (Real-time)");

        String wsUrl = "ws://127.0.0.1:8545";
        // Use WebSocketProvider for high performance and better subscription support
        try (WebSocketProvider wsProvider = WebSocketProvider.create(wsUrl)) {
            // 0. Verify Standard RPC calls work over WebSocket using new Brane API
            System.out.println("  Checking standard RPC over WebSocket...");
            Brane wsClient = Brane.builder().provider(wsProvider).build();
            BigInteger chainId = wsClient.chainId();
            if (!chainId.equals(new BigInteger("31337"))) {
                throw new RuntimeException("WebSocket RPC chainId failed. Expected 31337, got " + chainId);
            }
            System.out.println("  ✓ [WS] chainId success: " + chainId);

            // 1. Subscribe to New Heads using new Brane API
            CompletableFuture<BlockHeader> headFuture = new CompletableFuture<>();
            Subscription headSub = wsClient.onNewHeads(head -> {
                System.out.println("  ✓ [WS] New Head: " + head.number());
                headFuture.complete(head);
            });

            // Trigger a block
            provider.send("evm_mine", List.of());

            headFuture.get(5, TimeUnit.SECONDS);
            headSub.unsubscribe();

            // 2. Subscribe to Logs (Transfer event) using new Brane API
            CompletableFuture<LogEntry> logFuture = new CompletableFuture<>();
            Subscription logSub = wsClient.onLogs(
                    new LogFilter(Optional.empty(), Optional.empty(), Optional.of(List.of(tokenAddress)), Optional.empty()),
                    log -> {
                        System.out.println("  ✓ [WS] Log Received: " + log.transactionHash());
                        logFuture.complete(log);
                    });

            // Trigger a transfer using new Brane API
            Erc20 token = BraneContract.bind(tokenAddress, ABI_JSON, signerClient, Erc20.class);
            token.transfer(RECIPIENT, BigInteger.ONE);

            logFuture.get(10, TimeUnit.SECONDS);
            logSub.unsubscribe();

            System.out.println("  ✓ WebSocket subscriptions verified");
        } catch (Exception e) {
            System.err
                    .println("  ⚠️ WebSocket test failed: " + e.getMessage());
            // Log error but fail the test suite because WebSocket functionality is critical
            throw e;
        }
    }

    private static void runSepoliaTask(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            if (sepoliaMode
                    && (e instanceof RpcException || e.getCause() instanceof HttpTimeoutException)) {
                System.out.println("  ⚠️ Sepolia Network Error (Expected on public RPC): " + e.getMessage());
            } else if (e instanceof RuntimeException re) {
                throw re;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Scenario Q: Transaction Simulation (eth_simulateV1).
     * <p>
     * Tests the simulateCalls feature for dry-run transaction validation,
     * gas estimation, and multi-call sequence simulation.
     */
    private static void testSimulateCalls() {
        System.out.println("\n[Scenario Q] Testing Transaction Simulation (eth_simulateV1)");

        try {
            // Test 1: Simple value transfer simulation
            System.out.println("  Testing simple value transfer simulation");
            SimulateRequest request1 = SimulateRequest.builder()
                    .account(OWNER)
                    .call(SimulateCall.builder()
                            .to(RECIPIENT)
                            .value(Wei.fromEther(new BigDecimal("1.0")))
                            .build())
                    .build();

            // Use new Brane API
            SimulateResult result1 = client.simulate(request1);
            if (result1.results().size() != 1) {
                throw new RuntimeException("Expected 1 result, got " + result1.results().size());
            }
            if (!(result1.results().get(0) instanceof CallResult.Success)) {
                throw new RuntimeException("Simple transfer should succeed");
            }
            System.out.println("    ✓ Simple value transfer simulation works");

            // Test 2: Contract call simulation (balanceOf)
            System.out.println("  Testing contract call simulation");
            HexData balanceOfData = new HexData(abi.encodeFunction("balanceOf", OWNER).data());
            SimulateRequest request2 = SimulateRequest.builder()
                    .call(SimulateCall.builder()
                            .to(tokenAddress)
                            .data(balanceOfData)
                            .build())
                    .build();

            SimulateResult result2 = client.simulate(request2);
            if (!(result2.results().get(0) instanceof CallResult.Success)) {
                throw new RuntimeException("balanceOf call should succeed");
            }
            CallResult.Success success = (CallResult.Success) result2.results().get(0);
            if (success.returnData() == null) {
                throw new RuntimeException("balanceOf should return data");
            }
            System.out.println("    ✓ Contract call simulation works");

            // Test 3: Multiple calls in sequence
            System.out.println("  Testing multiple calls simulation");
            HexData transferData = new HexData(abi.encodeFunction("transfer", RECIPIENT, BigInteger.valueOf(100)).data());
            SimulateRequest request3 = SimulateRequest.builder()
                    .account(OWNER)
                    .call(SimulateCall.builder()
                            .to(tokenAddress)
                            .data(transferData)
                            .build())
                    .call(SimulateCall.builder()
                            .to(tokenAddress)
                            .data(balanceOfData)
                            .build())
                    .build();

            SimulateResult result3 = client.simulate(request3);
            if (result3.results().size() != 2) {
                throw new RuntimeException("Expected 2 results");
            }
            System.out.println("    ✓ Multiple calls simulation works");

            // Test 4: State override
            System.out.println("  Testing state override");
            AccountOverride override = AccountOverride.builder()
                    .balance(Wei.fromEther(new BigDecimal("10000")))
                    .build();

            SimulateRequest request4 = SimulateRequest.builder()
                    .call(SimulateCall.builder()
                            .to(tokenAddress)
                            .data(balanceOfData)
                            .build())
                    .stateOverride(OWNER, override)
                    .build();

            SimulateResult result4 = client.simulate(request4);
            if (!(result4.results().get(0) instanceof CallResult.Success)) {
                throw new RuntimeException("Call with state override should succeed");
            }
            System.out.println("    ✓ State override works");

            // Test 5: Gas estimation via simulation
            System.out.println("  Testing gas estimation");
            CallResult.Success transferResult = (CallResult.Success) result3.results().get(0);
            BigInteger gasUsed = transferResult.gasUsed();
            if (gasUsed == null || gasUsed.compareTo(BigInteger.ZERO) <= 0) {
                throw new RuntimeException("Gas used should be > 0");
            }
            System.out.println("    ✓ Gas estimation: " + gasUsed + " gas");

            // Test 6: Failed call simulation
            System.out.println("  Testing failed call simulation");
            BigInteger tooMuch = new BigInteger("999999999999999999999999");
            HexData failTransfer = new HexData(abi.encodeFunction("transfer", RECIPIENT, tooMuch).data());
            SimulateRequest request6 = SimulateRequest.builder()
                    .account(OWNER)
                    .call(SimulateCall.builder()
                            .to(tokenAddress)
                            .data(failTransfer)
                            .build())
                    .build();

            SimulateResult result6 = client.simulate(request6);
            if (!(result6.results().get(0) instanceof CallResult.Failure)) {
                throw new RuntimeException("Transfer with insufficient balance should fail");
            }
            CallResult.Failure failure = (CallResult.Failure) result6.results().get(0);
            if (failure.errorMessage() == null) {
                throw new RuntimeException("Failure should have error message");
            }
            System.out.println("    ✓ Failed call simulation works: " + failure.errorMessage());

            System.out.println("  ✅ Scenario Q: Transaction Simulation PASSED");

        } catch (SimulateNotSupportedException e) {
            System.out.println("  ⚠️ eth_simulateV1 not supported by this node (skipping test)");
            System.out.println("    This is expected on older Anvil versions or some RPC providers");
        } catch (Exception e) {
            throw new RuntimeException("Simulation test failed", e);
        }
    }

    // EIP-712 Permit record for type-safe signing test
    public record Eip712Permit(
            Address owner,
            Address spender,
            BigInteger value,
            BigInteger nonce,
            BigInteger deadline) {

        public static final io.brane.core.crypto.eip712.TypeDefinition<Eip712Permit> DEFINITION =
                io.brane.core.crypto.eip712.TypeDefinition.forRecord(
                        Eip712Permit.class,
                        "Permit",
                        Map.of("Permit", List.of(
                                io.brane.core.crypto.eip712.TypedDataField.of("owner", "address"),
                                io.brane.core.crypto.eip712.TypedDataField.of("spender", "address"),
                                io.brane.core.crypto.eip712.TypedDataField.of("value", "uint256"),
                                io.brane.core.crypto.eip712.TypedDataField.of("nonce", "uint256"),
                                io.brane.core.crypto.eip712.TypedDataField.of("deadline", "uint256"))));
    }

    /**
     * Scenario R: EIP-712 Type-Safe Signing.
     * <p>
     * Tests the TypedData.create() API with sign() and verifies that
     * signature recovery matches the signer's address.
     */
    private static void testEip712TypeSafeSigning() {
        System.out.println("\n[Scenario R] EIP-712 Type-Safe Signing");

        // Create a signer from the test private key
        PrivateKeySigner signer = new PrivateKeySigner(PRIVATE_KEY);
        Address signerAddress = signer.address();
        System.out.println("  Signer Address: " + signerAddress.value());

        // Define the EIP-712 domain (simulating a token contract)
        io.brane.core.crypto.eip712.Eip712Domain domain = io.brane.core.crypto.eip712.Eip712Domain.builder()
                .name("TestToken")
                .version("1")
                .chainId(31337L)
                .verifyingContract(RECIPIENT) // Using RECIPIENT as a placeholder contract address
                .build();

        // Create a permit message
        Eip712Permit permit = new Eip712Permit(
                signerAddress,
                RECIPIENT,
                BigInteger.valueOf(1_000_000_000_000_000_000L), // 1 token (18 decimals)
                BigInteger.ZERO,
                BigInteger.valueOf(1893456000L) // Far future deadline
        );

        // Create TypedData and sign
        io.brane.core.crypto.eip712.TypedData<Eip712Permit> typedData =
                io.brane.core.crypto.eip712.TypedData.create(domain, Eip712Permit.DEFINITION, permit);

        io.brane.core.types.Hash hash = typedData.hash();
        System.out.println("  EIP-712 Hash: " + hash.value());

        Signature signature = typedData.sign(signer);
        System.out.println("  Signature v: " + signature.v());

        // Verify v is 27 or 28 (EIP-712 standard)
        if (signature.v() != 27 && signature.v() != 28) {
            throw new RuntimeException("Signature v should be 27 or 28, got: " + signature.v());
        }

        // Recover the signer address from the signature
        Address recovered = io.brane.core.crypto.PrivateKey.recoverAddress(hash.toBytes(), signature);
        System.out.println("  Recovered Address: " + recovered.value());

        // Verify recovered address matches original signer
        if (!recovered.value().equalsIgnoreCase(signerAddress.value())) {
            throw new RuntimeException("Recovered address does not match signer: expected "
                    + signerAddress.value() + ", got " + recovered.value());
        }

        System.out.println("  ✓ EIP-712 sign/recover verified");
        System.out.println("  ✅ Scenario R: EIP-712 Type-Safe Signing PASSED");
    }

    /**
     * Scenario S: EIP-712 Dynamic Signing (Map-based API).
     * <p>
     * Tests TypedDataSigner.signTypedData() with Map-based message construction
     * for dynamic use cases where type structure is determined at runtime
     * (JSON from dapps, scripting, runtime-generated types).
     */
    private static void testEip712DynamicSigning() {
        System.out.println("\n[Scenario S] EIP-712 Dynamic Signing (Map-based API)");

        // Create a signer from the test private key
        PrivateKeySigner signer = new PrivateKeySigner(PRIVATE_KEY);
        Address signerAddress = signer.address();
        System.out.println("  Signer Address: " + signerAddress.value());

        // Define the EIP-712 domain (simulating a token contract)
        io.brane.core.crypto.eip712.Eip712Domain domain = io.brane.core.crypto.eip712.Eip712Domain.builder()
                .name("TestToken")
                .version("1")
                .chainId(31337L)
                .verifyingContract(RECIPIENT) // Using RECIPIENT as a placeholder contract address
                .build();

        // Define types as Map (runtime/dynamic approach)
        var types = new java.util.LinkedHashMap<String, List<io.brane.core.crypto.eip712.TypedDataField>>();
        types.put("Permit", List.of(
                io.brane.core.crypto.eip712.TypedDataField.of("owner", "address"),
                io.brane.core.crypto.eip712.TypedDataField.of("spender", "address"),
                io.brane.core.crypto.eip712.TypedDataField.of("value", "uint256"),
                io.brane.core.crypto.eip712.TypedDataField.of("nonce", "uint256"),
                io.brane.core.crypto.eip712.TypedDataField.of("deadline", "uint256")
        ));

        // Construct message as Map (how data arrives from JSON/dapps)
        var message = new java.util.LinkedHashMap<String, Object>();
        message.put("owner", signerAddress);
        message.put("spender", RECIPIENT);
        message.put("value", BigInteger.valueOf(1_000_000_000_000_000_000L)); // 1 token
        message.put("nonce", BigInteger.ZERO);
        message.put("deadline", BigInteger.valueOf(1893456000L)); // Far future

        // Sign using TypedDataSigner (static utility API)
        io.brane.core.types.Hash hash = io.brane.core.crypto.eip712.TypedDataSigner.hashTypedData(
                domain, "Permit", types, message);
        System.out.println("  EIP-712 Hash: " + hash.value());

        Signature signature = io.brane.core.crypto.eip712.TypedDataSigner.signTypedData(
                domain, "Permit", types, message, signer);
        System.out.println("  Signature v: " + signature.v());

        // Verify v is 27 or 28 (EIP-712 standard)
        if (signature.v() != 27 && signature.v() != 28) {
            throw new RuntimeException("Signature v should be 27 or 28, got: " + signature.v());
        }

        // Recover the signer address from the signature
        Address recovered = io.brane.core.crypto.PrivateKey.recoverAddress(hash.toBytes(), signature);
        System.out.println("  Recovered Address: " + recovered.value());

        // Verify recovered address matches original signer
        if (!recovered.value().equalsIgnoreCase(signerAddress.value())) {
            throw new RuntimeException("Recovered address does not match signer: expected "
                    + signerAddress.value() + ", got " + recovered.value());
        }

        System.out.println("  ✓ EIP-712 dynamic sign/recover verified");
        System.out.println("  ✅ Scenario S: EIP-712 Dynamic Signing PASSED");
    }

    /**
     * Scenario T: EIP-712 JSON Parsing.
     * <p>
     * Tests TypedDataJson.parse() for parsing EIP-712 JSON payloads
     * (WalletConnect/dapp style), then sign() and verify signature recovery.
     */
    private static void testEip712JsonParsing() {
        System.out.println("\n[Scenario T] EIP-712 JSON Parsing");

        // Create a signer from the test private key
        PrivateKeySigner signer = new PrivateKeySigner(PRIVATE_KEY);
        Address signerAddress = signer.address();
        System.out.println("  Signer Address: " + signerAddress.value());

        // EIP-712 JSON payload (eth_signTypedData_v4 format from dapps/WalletConnect)
        String permitJson = """
                {
                    "domain": {
                        "name": "TestToken",
                        "version": "1",
                        "chainId": 31337,
                        "verifyingContract": "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"
                    },
                    "primaryType": "Permit",
                    "types": {
                        "EIP712Domain": [
                            {"name": "name", "type": "string"},
                            {"name": "version", "type": "string"},
                            {"name": "chainId", "type": "uint256"},
                            {"name": "verifyingContract", "type": "address"}
                        ],
                        "Permit": [
                            {"name": "owner", "type": "address"},
                            {"name": "spender", "type": "address"},
                            {"name": "value", "type": "uint256"},
                            {"name": "nonce", "type": "uint256"},
                            {"name": "deadline", "type": "uint256"}
                        ]
                    },
                    "message": {
                        "owner": "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
                        "spender": "0x70997970C51812dc3A010C7d01b50e0d17dc79C8",
                        "value": "1000000000000000000",
                        "nonce": "0",
                        "deadline": "1893456000"
                    }
                }
                """;

        // Parse the JSON using TypedDataJson.parse()
        io.brane.core.crypto.eip712.TypedDataPayload payload =
                io.brane.core.crypto.eip712.TypedDataJson.parse(permitJson);

        // Verify the parsed payload
        if (!"Permit".equals(payload.primaryType())) {
            throw new RuntimeException("Primary type mismatch: expected Permit, got " + payload.primaryType());
        }
        if (!"TestToken".equals(payload.domain().name())) {
            throw new RuntimeException("Domain name mismatch: expected TestToken, got " + payload.domain().name());
        }
        if (payload.domain().chainId() != 31337L) {
            throw new RuntimeException("Chain ID mismatch: expected 31337, got " + payload.domain().chainId());
        }
        System.out.println("  ✓ JSON parsed successfully");
        System.out.println("    Domain: " + payload.domain().name());
        System.out.println("    Primary Type: " + payload.primaryType());

        // Create TypedData from parsed payload and sign
        io.brane.core.crypto.eip712.TypedData<?> typedData =
                io.brane.core.crypto.eip712.TypedData.fromPayload(payload);

        io.brane.core.types.Hash hash = typedData.hash();
        System.out.println("  EIP-712 Hash: " + hash.value());

        Signature signature = typedData.sign(signer);
        System.out.println("  Signature v: " + signature.v());

        // Verify v is 27 or 28 (EIP-712 standard)
        if (signature.v() != 27 && signature.v() != 28) {
            throw new RuntimeException("Signature v should be 27 or 28, got: " + signature.v());
        }

        // Recover the signer address from the signature
        Address recovered = io.brane.core.crypto.PrivateKey.recoverAddress(hash.toBytes(), signature);
        System.out.println("  Recovered Address: " + recovered.value());

        // Verify recovered address matches original signer
        if (!recovered.value().equalsIgnoreCase(signerAddress.value())) {
            throw new RuntimeException("Recovered address does not match signer: expected "
                    + signerAddress.value() + ", got " + recovered.value());
        }

        System.out.println("  ✓ EIP-712 JSON parse/sign/recover verified");

        // Also test parseAndValidate() shorthand
        io.brane.core.crypto.eip712.TypedData<?> typedData2 =
                io.brane.core.crypto.eip712.TypedDataJson.parseAndValidate(permitJson);
        io.brane.core.types.Hash hash2 = typedData2.hash();

        // Verify both methods produce the same hash
        if (!hash.value().equals(hash2.value())) {
            throw new RuntimeException("Hash mismatch between parse() and parseAndValidate()");
        }
        System.out.println("  ✓ parseAndValidate() produces same hash");

        System.out.println("  ✅ Scenario T: EIP-712 JSON Parsing PASSED");
    }

    /**
     * Scenario U: Tester Client Operations.
     * <p>
     * Tests the Brane.Tester client for local development and testing:
     * - connectTest() factory method
     * - setBalance() for account manipulation
     * - snapshot()/revert() for state management
     * - impersonate() for testing without private keys
     * - mine() for block production control
     */
    private static void testTesterOperations() {
        System.out.println("\n[Scenario U] Tester Client Operations");

        String rpcUrl = "http://127.0.0.1:8545";

        try (Brane.Tester tester = Brane.connectTest(rpcUrl)) {
            // 1. Test connectTest() and basic connectivity
            System.out.println("  Testing connectTest()...");
            BigInteger chainId = tester.chainId();
            if (!chainId.equals(BigInteger.valueOf(31337))) {
                throw new RuntimeException("Unexpected chain ID: " + chainId + " (expected 31337)");
            }
            System.out.println("    ✓ Connected to Anvil (Chain ID: " + chainId + ")");

            // 2. Test setBalance()
            System.out.println("  Testing setBalance()...");
            Address testAccount = new Address("0x1111111111111111111111111111111111111111");
            Wei targetBalance = Wei.fromEther(new BigDecimal("100"));

            tester.setBalance(testAccount, targetBalance);
            BigInteger actualBalance = tester.getBalance(testAccount);

            if (!actualBalance.equals(targetBalance.value())) {
                throw new RuntimeException("Balance mismatch: expected " + targetBalance.value() + ", got " + actualBalance);
            }
            System.out.println("    ✓ setBalance() works (set 100 ETH)");

            // 3. Test snapshot()/revert()
            System.out.println("  Testing snapshot()/revert()...");
            io.brane.rpc.SnapshotId snapshot = tester.snapshot();
            System.out.println("    Snapshot taken: " + snapshot.value());

            // Modify state after snapshot
            Wei modifiedBalance = Wei.fromEther(new BigDecimal("999"));
            tester.setBalance(testAccount, modifiedBalance);
            BigInteger balanceAfterModify = tester.getBalance(testAccount);
            if (!balanceAfterModify.equals(modifiedBalance.value())) {
                throw new RuntimeException("Balance not modified correctly");
            }

            // Revert to snapshot
            boolean reverted = tester.revert(snapshot);
            if (!reverted) {
                throw new RuntimeException("Revert returned false");
            }

            BigInteger balanceAfterRevert = tester.getBalance(testAccount);
            if (!balanceAfterRevert.equals(targetBalance.value())) {
                throw new RuntimeException("Balance not restored after revert: expected " + targetBalance.value() + ", got " + balanceAfterRevert);
            }
            System.out.println("    ✓ snapshot()/revert() works");

            // 4. Test impersonate()
            System.out.println("  Testing impersonate()...");
            Address whaleAddress = new Address("0x2222222222222222222222222222222222222222");
            Address recipient = io.brane.rpc.AnvilSigners.keyAt(1).address();

            // Fund the whale
            tester.setBalance(whaleAddress, Wei.fromEther(new BigDecimal("50")));
            BigInteger recipientBalanceBefore = tester.getBalance(recipient);

            Wei transferAmount = Wei.fromEther(new BigDecimal("1"));

            try (io.brane.rpc.ImpersonationSession session = tester.impersonate(whaleAddress)) {
                // Verify session address matches
                if (!session.address().equals(whaleAddress)) {
                    throw new RuntimeException("Session address mismatch");
                }

                // Send transaction as impersonated account
                TransactionRequest request = new TransactionRequest(
                        null, recipient, transferAmount,
                        21_000L, null, null, null, null, null, false, null);

                TransactionReceipt receipt = session.sendTransactionAndWait(request);
                if (!receipt.status()) {
                    throw new RuntimeException("Impersonated transaction failed");
                }
            }

            BigInteger recipientBalanceAfter = tester.getBalance(recipient);
            if (!recipientBalanceAfter.equals(recipientBalanceBefore.add(transferAmount.value()))) {
                throw new RuntimeException("Recipient did not receive funds from impersonated tx");
            }
            System.out.println("    ✓ impersonate() works");

            // 5. Test mine()
            System.out.println("  Testing mine()...");
            BlockHeader blockBefore = tester.getLatestBlock();
            long blockNumberBefore = blockBefore.number();

            tester.mine();
            BlockHeader blockAfterOne = tester.getLatestBlock();
            if (blockAfterOne.number() != blockNumberBefore + 1) {
                throw new RuntimeException("mine() did not advance block by 1");
            }

            tester.mine(5);
            BlockHeader blockAfterFive = tester.getLatestBlock();
            if (blockAfterFive.number() != blockNumberBefore + 6) {
                throw new RuntimeException("mine(5) did not advance blocks correctly");
            }
            System.out.println("    ✓ mine() works (mined 6 blocks total)");

            System.out.println("  ✅ Scenario U: Tester Client Operations PASSED");

        } catch (Exception e) {
            throw new RuntimeException("Tester operations test failed", e);
        }
    }
}
