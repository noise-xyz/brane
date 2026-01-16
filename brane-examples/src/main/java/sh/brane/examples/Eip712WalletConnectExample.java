// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.examples;

import sh.brane.core.AnsiColors;
import sh.brane.core.crypto.PrivateKeySigner;
import sh.brane.core.crypto.Signature;
import sh.brane.core.crypto.eip712.TypedData;
import sh.brane.core.crypto.eip712.TypedDataJson;
import sh.brane.core.crypto.eip712.TypedDataPayload;
import sh.brane.core.types.Hash;
import sh.brane.primitives.Hex;

/**
 * Demonstrates EIP-712 typed data signing from JSON requests as received via WalletConnect.
 *
 * <p>When a dapp connects to a wallet via WalletConnect, it sends signing requests
 * in a standard JSON format. This example shows how a wallet would:
 * <ol>
 *   <li>Receive the raw JSON payload from a dapp</li>
 *   <li>Parse and validate the typed data structure</li>
 *   <li>Display signing details to the user for approval</li>
 *   <li>Sign the data and return the signature</li>
 * </ol>
 *
 * <p>The JSON format follows the {@code eth_signTypedData_v4} standard used by
 * MetaMask, WalletConnect, and other wallet providers.
 *
 * <p>Usage:
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *     -PmainClass=sh.brane.examples.Eip712WalletConnectExample
 * </pre>
 */
public final class Eip712WalletConnectExample {

    // Test private key (Anvil's default account #0)
    private static final String PRIVATE_KEY = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private Eip712WalletConnectExample() {
        // Prevent instantiation
    }

    public static void main(String[] args) {
        System.out.println("=== EIP-712 WalletConnect Signing Example ===\n");

        // Create signer (in a real wallet, this would be the user's key)
        var signer = new PrivateKeySigner(PRIVATE_KEY);
        System.out.println("Wallet address: " + signer.address().value());

        // =========================================================================
        // [1] Uniswap Permit2 - Token approval via signature
        // =========================================================================
        System.out.println("\n[1] Simulated WalletConnect Request: Uniswap Permit2");

        String permit2Request = """
                {
                    "domain": {
                        "name": "Permit2",
                        "chainId": 1,
                        "verifyingContract": "0x000000000022D473030F116dDEE9F6B43aC78BA3"
                    },
                    "primaryType": "PermitSingle",
                    "types": {
                        "EIP712Domain": [
                            {"name": "name", "type": "string"},
                            {"name": "chainId", "type": "uint256"},
                            {"name": "verifyingContract", "type": "address"}
                        ],
                        "PermitSingle": [
                            {"name": "details", "type": "PermitDetails"},
                            {"name": "spender", "type": "address"},
                            {"name": "sigDeadline", "type": "uint256"}
                        ],
                        "PermitDetails": [
                            {"name": "token", "type": "address"},
                            {"name": "amount", "type": "uint160"},
                            {"name": "expiration", "type": "uint48"},
                            {"name": "nonce", "type": "uint48"}
                        ]
                    },
                    "message": {
                        "details": {
                            "token": "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                            "amount": "1461501637330902918203684832716283019655932542975",
                            "expiration": "1735689600",
                            "nonce": "0"
                        },
                        "spender": "0x3fC91A3afd70395Cd496C647d5a6CC9D4B2b7FAD",
                        "sigDeadline": "1704067200"
                    }
                }
                """;

        handleWalletConnectRequest("Permit2", permit2Request, signer);

        // =========================================================================
        // [2] OpenSea Seaport - NFT marketplace order
        // =========================================================================
        System.out.println("\n[2] Simulated WalletConnect Request: OpenSea Seaport Order");

        String seaportRequest = """
                {
                    "domain": {
                        "name": "Seaport",
                        "version": "1.5",
                        "chainId": 1,
                        "verifyingContract": "0x00000000000000ADc04C56Bf30aC9d3c0aAF14dC"
                    },
                    "primaryType": "OrderComponents",
                    "types": {
                        "EIP712Domain": [
                            {"name": "name", "type": "string"},
                            {"name": "version", "type": "string"},
                            {"name": "chainId", "type": "uint256"},
                            {"name": "verifyingContract", "type": "address"}
                        ],
                        "OrderComponents": [
                            {"name": "offerer", "type": "address"},
                            {"name": "zone", "type": "address"},
                            {"name": "offer", "type": "OfferItem[]"},
                            {"name": "consideration", "type": "ConsiderationItem[]"},
                            {"name": "orderType", "type": "uint8"},
                            {"name": "startTime", "type": "uint256"},
                            {"name": "endTime", "type": "uint256"},
                            {"name": "zoneHash", "type": "bytes32"},
                            {"name": "salt", "type": "uint256"},
                            {"name": "conduitKey", "type": "bytes32"},
                            {"name": "counter", "type": "uint256"}
                        ],
                        "OfferItem": [
                            {"name": "itemType", "type": "uint8"},
                            {"name": "token", "type": "address"},
                            {"name": "identifierOrCriteria", "type": "uint256"},
                            {"name": "startAmount", "type": "uint256"},
                            {"name": "endAmount", "type": "uint256"}
                        ],
                        "ConsiderationItem": [
                            {"name": "itemType", "type": "uint8"},
                            {"name": "token", "type": "address"},
                            {"name": "identifierOrCriteria", "type": "uint256"},
                            {"name": "startAmount", "type": "uint256"},
                            {"name": "endAmount", "type": "uint256"},
                            {"name": "recipient", "type": "address"}
                        ]
                    },
                    "message": {
                        "offerer": "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
                        "zone": "0x0000000000000000000000000000000000000000",
                        "offer": [
                            {
                                "itemType": 2,
                                "token": "0xBC4CA0EdA7647A8aB7C2061c2E118A18a936f13D",
                                "identifierOrCriteria": "1234",
                                "startAmount": "1",
                                "endAmount": "1"
                            }
                        ],
                        "consideration": [
                            {
                                "itemType": 0,
                                "token": "0x0000000000000000000000000000000000000000",
                                "identifierOrCriteria": "0",
                                "startAmount": "950000000000000000",
                                "endAmount": "950000000000000000",
                                "recipient": "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
                            },
                            {
                                "itemType": 0,
                                "token": "0x0000000000000000000000000000000000000000",
                                "identifierOrCriteria": "0",
                                "startAmount": "25000000000000000",
                                "endAmount": "25000000000000000",
                                "recipient": "0x0000a26b00c1F0DF003000390027140000fAa719"
                            },
                            {
                                "itemType": 0,
                                "token": "0x0000000000000000000000000000000000000000",
                                "identifierOrCriteria": "0",
                                "startAmount": "25000000000000000",
                                "endAmount": "25000000000000000",
                                "recipient": "0xA858DDc0445d8131daC4d1DE01f834ffcbA52Ef1"
                            }
                        ],
                        "orderType": 0,
                        "startTime": "1700000000",
                        "endTime": "1800000000",
                        "zoneHash": "0x0000000000000000000000000000000000000000000000000000000000000000",
                        "salt": "12345678901234567890",
                        "conduitKey": "0x0000007b02230091a7ed01230072f7006a004d60a8d4e71d599b8104250f0000",
                        "counter": "0"
                    }
                }
                """;

        handleWalletConnectRequest("Seaport", seaportRequest, signer);

        // =========================================================================
        // [3] CoW Protocol - DEX swap order
        // =========================================================================
        System.out.println("\n[3] Simulated WalletConnect Request: CoW Protocol Swap");

        String cowSwapRequest = """
                {
                    "domain": {
                        "name": "Gnosis Protocol",
                        "version": "v2",
                        "chainId": 1,
                        "verifyingContract": "0x9008D19f58AAbD9eD0D60971565AA8510560ab41"
                    },
                    "primaryType": "Order",
                    "types": {
                        "EIP712Domain": [
                            {"name": "name", "type": "string"},
                            {"name": "version", "type": "string"},
                            {"name": "chainId", "type": "uint256"},
                            {"name": "verifyingContract", "type": "address"}
                        ],
                        "Order": [
                            {"name": "sellToken", "type": "address"},
                            {"name": "buyToken", "type": "address"},
                            {"name": "receiver", "type": "address"},
                            {"name": "sellAmount", "type": "uint256"},
                            {"name": "buyAmount", "type": "uint256"},
                            {"name": "validTo", "type": "uint32"},
                            {"name": "appData", "type": "bytes32"},
                            {"name": "feeAmount", "type": "uint256"},
                            {"name": "kind", "type": "string"},
                            {"name": "partiallyFillable", "type": "bool"},
                            {"name": "sellTokenBalance", "type": "string"},
                            {"name": "buyTokenBalance", "type": "string"}
                        ]
                    },
                    "message": {
                        "sellToken": "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2",
                        "buyToken": "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                        "receiver": "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
                        "sellAmount": "1000000000000000000",
                        "buyAmount": "2000000000",
                        "validTo": 1735689600,
                        "appData": "0x0000000000000000000000000000000000000000000000000000000000000000",
                        "feeAmount": "1000000000000000",
                        "kind": "sell",
                        "partiallyFillable": false,
                        "sellTokenBalance": "erc20",
                        "buyTokenBalance": "erc20"
                    }
                }
                """;

        handleWalletConnectRequest("CoW Protocol", cowSwapRequest, signer);

        System.out.println("\n" + AnsiColors.success("WalletConnect signing examples completed successfully!"));
    }

    /**
     * Simulates how a wallet handles a WalletConnect signing request.
     *
     * <p>In a real wallet implementation, this would:
     * <ol>
     *   <li>Show a confirmation dialog with parsed details</li>
     *   <li>Wait for user approval</li>
     *   <li>Sign if approved, reject if denied</li>
     * </ol>
     */
    private static void handleWalletConnectRequest(String protocol, String json, PrivateKeySigner signer) {
        System.out.println("Received eth_signTypedData_v4 request from dapp...");

        // Step 1: Parse the raw JSON payload
        TypedDataPayload payload = TypedDataJson.parse(json);

        // Step 2: Display request details for user review (simulated)
        System.out.println("\n--- Signing Request Details ---");
        System.out.println("Protocol: " + protocol);
        System.out.println("Domain: " + payload.domain().name());
        if (payload.domain().version() != null) {
            System.out.println("Version: " + payload.domain().version());
        }
        System.out.println("Chain ID: " + payload.domain().chainId());
        System.out.println("Contract: " + payload.domain().verifyingContract().value());
        System.out.println("Primary Type: " + payload.primaryType());
        System.out.println("Message fields: " + payload.message().keySet());

        // Step 3: Validate and create signable TypedData
        TypedData<?> typedData = TypedDataJson.parseAndValidate(json);

        // Step 4: Compute hash for verification
        Hash hash = typedData.hash();
        System.out.println("\n--- Signature ---");
        System.out.println("Message hash: " + hash.value());

        // Step 5: Sign (in real wallet, only after user approval)
        Signature sig = typedData.sign(signer);

        // Step 6: Return signature components (for WalletConnect response)
        System.out.println("r: 0x" + Hex.encodeNoPrefix(sig.r()));
        System.out.println("s: 0x" + Hex.encodeNoPrefix(sig.s()));
        System.out.println("v: " + sig.v());
        System.out.println(AnsiColors.success("Full signature: " + sig));
    }
}
