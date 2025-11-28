package io.brane.core.tx;

import io.brane.core.crypto.Keccak256;
import io.brane.core.crypto.PrivateKey;
import io.brane.core.crypto.Signature;
import io.brane.core.model.AccessListEntry;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.primitives.Hex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Eip1559Transaction with EIP-2718 typed envelope.
 */
class Eip1559TransactionTest {

    private static final String TEST_PRIVATE_KEY = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";

    @Test
    void testCreateEip1559Transaction() {
        final Eip1559Transaction tx = new Eip1559Transaction(
                1L, // chainId
                0L, // nonce
                Wei.of(2000000000L), // 2 gwei priority fee
                Wei.of(100000000000L), // 100 gwei max fee
                21000L,
                Address.fromBytes(hexToBytes("70997970c51812dc3a010c7d01b50e0d17dc79c8")),
                Wei.of(1000000000000000000L), // 1 ether
                HexData.EMPTY,
                List.of());

        assertNotNull(tx);
        assertEquals(1L, tx.chainId());
        assertEquals(0L, tx.nonce());
        assertEquals(21000L, tx.gasLimit());
    }

    @Test
    void testEncodeForSigningStartsWithTypeExByte() {
        final Eip1559Transaction tx = new Eip1559Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(1000000000000000000L),
                HexData.EMPTY,
                List.of());

        final byte[] preimage = tx.encodeForSigning(1);

        assertNotNull(preimage);
        assertTrue(preimage.length > 0);
        assertEquals(0x02, preimage[0], "EIP-1559 transactions must start with type0x02");
    }

    @Test
    void testEncodeAsEnvelopeStartsWithType() {
        final Eip1559Transaction tx = new Eip1559Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(1000000000000000000L),
                HexData.EMPTY,
                List.of());

        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        r[0] = 0x01;
        s[0] = 0x02;
        final Signature signature = new Signature(r, s, 0); // yParity = 0

        final byte[] envelope = tx.encodeAsEnvelope(signature);

        assertNotNull(envelope);
        assertTrue(envelope.length > 0);
        assertEquals(0x02, envelope[0], "EIP-1559 envelope must start with type 0x02");
    }

    @Test
    void testSignAndEncode() {
        final PrivateKey privateKey = PrivateKey.fromHex(TEST_PRIVATE_KEY);

        final Eip1559Transaction tx = new Eip1559Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(1000000000000000000L),
                HexData.EMPTY,
                List.of());

        final byte[] preimage = tx.encodeForSigning(1);
        final byte[] messageHash = Keccak256.hash(preimage);
        final Signature signature = privateKey.sign(messageHash);

        // For EIP-1559, v is just yParity (0 or 1), not EIP-155 encoded
        assertTrue(signature.v() == 0 || signature.v() == 1);

        final byte[] envelope = tx.encodeAsEnvelope(signature);

        assertNotNull(envelope);
        assertTrue(envelope.length > 0);

        final String hex = Hex.encode(envelope);
        assertTrue(hex.startsWith("0x02"), "Hex should start with 0x02");
    }

    @Test
    void testWithAccessList() {
        final Address contractAddr = Address.fromBytes(hexToBytes("a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"));
        final Hash storageKey1 = new Hash("0x0000000000000000000000000000000000000000000000000000000000000001");
        final Hash storageKey2 = new Hash("0x0000000000000000000000000000000000000000000000000000000000000002");

        final AccessListEntry entry = new AccessListEntry(contractAddr, List.of(storageKey1, storageKey2));

        final Eip1559Transaction tx = new Eip1559Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                100000L, // More gas for complex interaction
                contractAddr,
                Wei.of(0),
                HexData.fromBytes(new byte[] { 0x01, 0x02, 0x03, 0x04 }),
                List.of(entry));

        assertNotNull(tx);
        assertEquals(1, tx.accessList().size());

        final byte[] preimage = tx.encodeForSigning(1);
        assertNotNull(preimage);
        // Access list should be encoded in the preimage
    }

    @Test
    void testContractCreation() {
        final Eip1559Transaction tx = new Eip1559Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                500000L,
                null, // null = contract creation
                Wei.of(0),
                HexData.fromBytes(new byte[] { 0x60, 0x60, 0x60, 0x40 }),
                List.of());

        assertNotNull(tx);
        assertNull(tx.to());

        final byte[] preimage = tx.encodeForSigning(1);
        assertNotNull(preimage);
    }

    @Test
    void testChainIdValidation() {
        assertThrows(IllegalArgumentException.class, () -> new Eip1559Transaction(
                0L, // Invalid chainId = 0
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(0),
                HexData.EMPTY,
                List.of()));
    }

    @Test
    void testNonceValidation() {
        assertThrows(IllegalArgumentException.class, () -> new Eip1559Transaction(
                1L,
                -1L, // Invalid negative nonce
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(0),
                HexData.EMPTY,
                List.of()));
    }

    @Test
    void testGasLimitValidation() {
        assertThrows(IllegalArgumentException.class, () -> new Eip1559Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                0L, // Invalid zero gas limit
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(0),
                HexData.EMPTY,
                List.of()));
    }

    @Test
    void testNullValidation() {
        assertThrows(NullPointerException.class, () -> new Eip1559Transaction(
                1L,
                0L,
                null, // Null maxPriorityFeePerGas
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(0),
                HexData.EMPTY,
                List.of()));
    }

    @Test
    void testDeterministicEncoding() {
        final Eip1559Transaction tx = new Eip1559Transaction(
                1L,
                5L,
                Wei.of(2500000000L),
                Wei.of(95000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(500000000000000000L),
                HexData.EMPTY,
                List.of());

        final byte[] preimage1 = tx.encodeForSigning(1);
        final byte[] preimage2 = tx.encodeForSigning(1);

        assertArrayEquals(preimage1, preimage2, "Encoding must be deterministic");
    }

    @Test
    void testAccessListIsImmutable() {
        final Address contractAddr = Address.fromBytes(hexToBytes("a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"));
        final Hash storageKey = new Hash("0x0000000000000000000000000000000000000000000000000000000000000001");
        final List<AccessListEntry> accessList = new java.util.ArrayList<>();
        accessList.add(new AccessListEntry(contractAddr, List.of(storageKey)));

        final Eip1559Transaction tx = new Eip1559Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                contractAddr,
                Wei.of(0),
                HexData.EMPTY,
                accessList);

        // Access list should be immutable - attempting to modify should fail
        assertThrows(UnsupportedOperationException.class, () -> tx.accessList().add(
                new AccessListEntry(contractAddr, List.of())));
    }

    // Helper method
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
