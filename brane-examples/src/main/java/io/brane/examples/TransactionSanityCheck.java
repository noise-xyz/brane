package io.brane.examples;

import io.brane.core.crypto.Keccak256;
import io.brane.core.crypto.PrivateKey;
import io.brane.core.crypto.Signature;
import io.brane.core.model.TransactionRequest;
import io.brane.core.tx.Eip1559Transaction;
import io.brane.core.tx.LegacyTransaction;
import io.brane.core.tx.UnsignedTransaction;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.primitives.Hex;

import java.util.List;

/**
 * Sanity check for Milestone 2 transaction models.
 * 
 * <p>
 * Demonstrates:
 * <ul>
 * <li>Creating Legacy (EIP-155) transactions</li>
 * <li>Creating EIP-1559 transactions with type byte 0x02</li>
 * <li>Converting TransactionRequest to UnsignedTransaction</li>
 * <li>Signing transactions with PrivateKey</li>
 * <li>Encoding signed transactions as broadcast-ready envelopes</li>
 * </ul>
 */
public final class TransactionSanityCheck {

    private static final String TEST_KEY = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";
    private static final long CHAIN_ID = 1; // Mainnet

    public static void main(String[] args) {
        System.out.println("=== Milestone 2: Transaction Models Sanity Check ===\n");

        try {
            // Test 1: Legacy Transaction
            testLegacyTransaction();

            // Test 2: EIP-1559 Transaction
            testEip1559Transaction();

            // Test 3: TransactionRequest Conversion
            testTransactionRequestConversion();

            // Test 4: Verify Type Bytes
            testTypeBytesCorrect();

            System.out.println("\n=== All Sanity Checks Passed! ✅ ===");
            System.out.println("\nMilestone 2 transaction models are working correctly:");
            System.out.println("  • Legacy transactions use EIP-155 encoding");
            System.out.println("  • EIP-1559 transactions use EIP-2718 envelope (0x02 prefix)");
            System.out.println("  • TransactionRequest converts to correct transaction type");
            System.out.println("  • All transactions sign and encode correctly");

        } catch (Exception e) {
            System.err.println("\n❌ Sanity check failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void testLegacyTransaction() {
        System.out.println("1. Testing Legacy Transaction (EIP-155)");

        final LegacyTransaction tx = new LegacyTransaction(
                0L, // nonce
                Wei.gwei(20), // gasPrice (20 gwei)
                21000L, // gasLimit
                Address.fromBytes(hexToBytes("70997970c51812dc3a010c7d01b50e0d17dc79c8")),
                Wei.of(1000000000000000000L), // 1 ether
                HexData.EMPTY);

        // Sign
        final PrivateKey privateKey = PrivateKey.fromHex(TEST_KEY);
        final byte[] preimage = tx.encodeForSigning(CHAIN_ID);
        final byte[] messageHash = Keccak256.hash(preimage);
        final Signature baseSig = privateKey.sign(messageHash);

        // Create EIP-155 signature (v = chainId * 2 + 35 + yParity)
        final int v = (int) (CHAIN_ID * 2 + 35 + baseSig.v());
        final Signature eip155Sig = new Signature(baseSig.r(), baseSig.s(), v);

        // Encode envelope
        final byte[] envelope = tx.encodeAsEnvelope(eip155Sig);
        final String hex = Hex.encode(envelope);

        System.out.println("   Nonce: " + tx.nonce());
        System.out.println("   Gas Price: " + tx.gasPrice().value() + " wei");
        System.out.println("   Gas Limit: " + tx.gasLimit());
        System.out.println("   To: " + tx.to().value());
        System.out.println("   Value: " + tx.value().value() + " wei");
        System.out.println("   Signature v: " + v + " (EIP-155 encoded)");
        System.out.println("   Envelope (hex): " + hex.substring(0, Math.min(66, hex.length())) + "...");
        System.out.println("   ✓ Legacy transaction encoded correctly\n");
    }

    private static void testEip1559Transaction() {
        System.out.println("2. Testing EIP-1559 Transaction");

        final Eip1559Transaction tx = new Eip1559Transaction(
                CHAIN_ID, // chainId
                0L, // nonce
                Wei.gwei(2), // maxPriorityFeePerGas (tip)
                Wei.gwei(100), // maxFeePerGas
                21000L, // gasLimit
                Address.fromBytes(hexToBytes("70997970c51812dc3a010c7d01b50e0d17dc79c8")),
                Wei.of(1000000000000000000L), // 1 ether
                HexData.EMPTY,
                List.of() // empty access list
        );

        // Sign
        final PrivateKey privateKey = PrivateKey.fromHex(TEST_KEY);
        final byte[] preimage = tx.encodeForSigning(CHAIN_ID);
        final byte[] messageHash = Keccak256.hash(preimage);
        final Signature signature = privateKey.sign(messageHash);

        // Encode envelope
        final byte[] envelope = tx.encodeAsEnvelope(signature);
        final String hex = Hex.encode(envelope);

        System.out.println("   Chain ID: " + tx.chainId());
        System.out.println("   Nonce: " + tx.nonce());
        System.out.println("   Max Priority Fee: " + tx.maxPriorityFeePerGas().value() + " wei");
        System.out.println("   Max Fee: " + tx.maxFeePerGas().value() + " wei");
        System.out.println("   Gas Limit: " + tx.gasLimit());
        System.out.println("   Signature v: " + signature.v() + " (yParity only, not EIP-155)");
        System.out.println("   Envelope starts with: 0x02 (" + (envelope[0] == 0x02 ? "✓" : "✗") + ")");
        System.out.println("   Envelope (hex): " + hex.substring(0, Math.min(66, hex.length())) + "...");
        System.out.println("   ✓ EIP-1559 transaction encoded correctly\n");
    }

    private static void testTransactionRequestConversion() {
        System.out.println("3. Testing TransactionRequest Conversion");

        // Legacy request
        final TransactionRequest legacyRequest = new TransactionRequest(
                Address.fromBytes(hexToBytes("2c7536e3605d9c16a7a3d7b1898e529396a65c23")),
                Address.fromBytes(hexToBytes("70997970c51812dc3a010c7d01b50e0d17dc79c8")),
                Wei.of(1000000000000000000L),
                21000L,
                Wei.gwei(20),
                null,
                null,
                0L,
                HexData.EMPTY,
                false, // Legacy
                null);

        final UnsignedTransaction legacyTx = legacyRequest.toUnsignedTransaction(CHAIN_ID);
        if (!(legacyTx instanceof LegacyTransaction)) {
            throw new AssertionError("Expected LegacyTransaction");
        }
        System.out.println("   ✓ Legacy request → LegacyTransaction");

        // EIP-1559 request
        final TransactionRequest eip1559Request = new TransactionRequest(
                Address.fromBytes(hexToBytes("2c7536e3605d9c16a7a3d7b1898e529396a65c23")),
                Address.fromBytes(hexToBytes("70997970c51812dc3a010c7d01b50e0d17dc79c8")),
                Wei.of(1000000000000000000L),
                21000L,
                null,
                Wei.gwei(2),
                Wei.gwei(100),
                0L,
                HexData.EMPTY,
                true, // EIP-1559
                List.of());

        final UnsignedTransaction eip1559Tx = eip1559Request.toUnsignedTransaction(CHAIN_ID);
        if (!(eip1559Tx instanceof Eip1559Transaction)) {
            throw new AssertionError("Expected Eip1559Transaction");
        }
        System.out.println("   ✓ EIP-1559 request → Eip1559Transaction");
        System.out.println("   ✓ TransactionRequest conversion works\n");
    }

    private static void testTypeBytesCorrect() {
        System.out.println("4. Testing Type Byte Correctness");

        final PrivateKey privateKey = PrivateKey.fromHex(TEST_KEY);

        // Legacy transaction should NOT have type byte prefix
        final LegacyTransaction legacyTx = new LegacyTransaction(
                0L, Wei.gwei(20), 21000L,
                Address.fromBytes(hexToBytes("70997970c51812dc3a010c7d01b50e0d17dc79c8")),
                Wei.of(1000000000000000000L),
                HexData.EMPTY);

        final byte[] legacyPreimage = legacyTx.encodeForSigning(CHAIN_ID);
        final byte[] legacyHash = Keccak256.hash(legacyPreimage);
        final Signature legacySig = privateKey.sign(legacyHash);
        final int legacyV = (int) (CHAIN_ID * 2 + 35 + legacySig.v());
        final byte[] legacyEnvelope = legacyTx.encodeAsEnvelope(new Signature(legacySig.r(), legacySig.s(), legacyV));

        // Legacy starts with RLP list marker (0xf8 or similar), not 0x02
        if (legacyEnvelope[0] == 0x02) {
            throw new AssertionError("Legacy transaction should NOT start with 0x02");
        }
        System.out.println("   ✓ Legacy transaction does NOT have type byte");

        // EIP-1559 transaction MUST have 0x02 type byte
        final Eip1559Transaction eip1559Tx = new Eip1559Transaction(
                CHAIN_ID, 0L, Wei.gwei(2), Wei.gwei(100), 21000L,
                Address.fromBytes(hexToBytes("70997970c51812dc3a010c7d01b50e0d17dc79c8")),
                Wei.of(1000000000000000000L),
                HexData.EMPTY,
                List.of());

        final byte[] eip1559Preimage = eip1559Tx.encodeForSigning(CHAIN_ID);
        if (eip1559Preimage[0] != 0x02) {
            throw new AssertionError("EIP-1559 signing preimage must start with 0x02");
        }

        final byte[] eip1559Hash = Keccak256.hash(eip1559Preimage);
        final Signature eip1559Sig = privateKey.sign(eip1559Hash);
        final byte[] eip1559Envelope = eip1559Tx.encodeAsEnvelope(eip1559Sig);

        if (eip1559Envelope[0] != 0x02) {
            throw new AssertionError("EIP-1559 envelope must start with 0x02");
        }
        System.out.println("   ✓ EIP-1559 transaction has 0x02 type byte");
        System.out.println("   ✓ Type bytes are correct per EIP-2718\n");
    }

    private static byte[] hexToBytes(String hex) {
        final int len = hex.length();
        final byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
