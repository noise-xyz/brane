// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.crypto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for the Signature record.
 */
class SignatureTest {

    @Test
    void testSignatureCreation() {
        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        r[0] = 0x01;
        s[0] = 0x02;

        final Signature sig = new Signature(r, s, 27);

        assertNotNull(sig);
        assertEquals(27, sig.v());
        assertEquals(32, sig.r().length);
        assertEquals(32, sig.s().length);
    }

    @Test
    void testImmutability() {
        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        r[0] = 0x01;
        s[0] = 0x02;

        final Signature sig = new Signature(r, s, 27);

        // Modify original arrays
        r[0] = (byte) 0xFF;
        s[0] = (byte) 0xFF;

        // Signature should be unaffected (defensive copy)
        assertEquals(0x01, sig.r()[0]);
        assertEquals(0x02, sig.s()[0]);
    }

    @Test
    void testInvalidRLength() {
        final byte[] invalidR = new byte[31]; // Wrong length
        final byte[] validS = new byte[32];

        assertThrows(IllegalArgumentException.class, () -> new Signature(invalidR, validS, 27));
    }

    @Test
    void testInvalidSLength() {
        final byte[] validR = new byte[32];
        final byte[] invalidS = new byte[33]; // Wrong length

        assertThrows(IllegalArgumentException.class, () -> new Signature(validR, invalidS, 27));
    }

    @Test
    void testNullR() {
        final byte[] validS = new byte[32];
        assertThrows(NullPointerException.class, () -> new Signature(null, validS, 27));
    }

    @Test
    void testNullS() {
        final byte[] validR = new byte[32];
        assertThrows(NullPointerException.class, () -> new Signature(validR, null, 27));
    }

    @Test
    void testGetRecoveryIdSimple() {
        final byte[] r = new byte[32];
        final byte[] s = new byte[32];

        // Simple v (0 or 1)
        final Signature sig0 = new Signature(r, s, 0);
        assertEquals(0, sig0.getRecoveryId(0));

        final Signature sig1 = new Signature(r, s, 1);
        assertEquals(1, sig1.getRecoveryId(0));
    }

    @Test
    void testGetRecoveryIdEip155() {
        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        final long chainId = 1; // Ethereum mainnet

        // EIP-155: v = chainId * 2 + 35 + yParity
        // For chainId=1, yParity=0: v = 1*2 + 35 + 0 = 37
        final Signature sig = new Signature(r, s, 37);
        assertEquals(0, sig.getRecoveryId(chainId));

        // For chainId=1, yParity=1: v = 1*2 + 35 + 1 = 38
        final Signature sig2 = new Signature(r, s, 38);
        assertEquals(1, sig2.getRecoveryId(chainId));
    }

    @Test
    void testIsEip155() {
        final byte[] r = new byte[32];
        final byte[] s = new byte[32];

        // Simple v values (not EIP-155)
        assertFalse(new Signature(r, s, 0).isEip155());
        assertFalse(new Signature(r, s, 1).isEip155());
        assertFalse(new Signature(r, s, 27).isEip155());
        assertFalse(new Signature(r, s, 28).isEip155());

        // EIP-155 v values
        assertTrue(new Signature(r, s, 36).isEip155());
        assertTrue(new Signature(r, s, 37).isEip155());
        assertTrue(new Signature(r, s, 100).isEip155());
    }

    @Test
    void testEquality() {
        final byte[] r1 = new byte[32];
        final byte[] s1 = new byte[32];
        r1[0] = 0x01;
        s1[0] = 0x02;

        final byte[] r2 = new byte[32];
        final byte[] s2 = new byte[32];
        r2[0] = 0x01;
        s2[0] = 0x02;

        final Signature sig1 = new Signature(r1, s1, 27);
        final Signature sig2 = new Signature(r2, s2, 27);

        assertEquals(sig1, sig2);
        assertEquals(sig1.hashCode(), sig2.hashCode());
    }

    @Test
    void testInequality() {
        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        r[0] = 0x01;
        s[0] = 0x02;

        final byte[] sDifferent = new byte[32];
        sDifferent[0] = 0x03;

        final Signature sig1 = new Signature(r, s, 27);
        final Signature sig2 = new Signature(r, sDifferent, 27);
        final Signature sig3 = new Signature(r, s, 28);

        assertNotEquals(sig1, sig2, "Different s values");
        assertNotEquals(sig1, sig3, "Different v values");
    }

    @Test
    void testToString() {
        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        r[0] = 0x01;
        s[0] = 0x02;

        final Signature sig = new Signature(r, s, 27);
        final String str = sig.toString();

        assertNotNull(str);
        assertTrue(str.contains("Signature"));
        assertTrue(str.contains("v=27"));
    }

    @Test
    void testGetRecoveryIdLargeChainId() {
        // CRIT-2: Test with large chain IDs to verify computation handles overflow correctly
        final byte[] r = new byte[32];
        final byte[] s = new byte[32];

        // Use a large but realistic chainId (e.g., some L2 chains have IDs in millions)
        // Maximum chainId where v fits in int: (Integer.MAX_VALUE - 36) / 2 = 1073741805
        long chainId = 1073741805L;
        int v0 = (int) (chainId * 2 + 35);      // 2147483645 (yParity=0)
        int v1 = (int) (chainId * 2 + 35 + 1);  // 2147483646 (yParity=1)

        final Signature sig0 = new Signature(r, s, v0);
        assertEquals(0, sig0.getRecoveryId(chainId));

        final Signature sig1 = new Signature(r, s, v1);
        assertEquals(1, sig1.getRecoveryId(chainId));

        // Test with a typical L2 chain ID (e.g., Arbitrum One = 42161)
        long arbitrumChainId = 42161L;
        int arbitrumV = (int) (arbitrumChainId * 2 + 35); // 84357

        final Signature arbSig = new Signature(r, s, arbitrumV);
        assertEquals(0, arbSig.getRecoveryId(arbitrumChainId));
    }

    @Test
    void testGetRecoveryIdInvalidRecoveryId() {
        // CRIT-2: Verify that invalid recovery IDs throw an exception
        final byte[] r = new byte[32];
        final byte[] s = new byte[32];

        // Create a signature with v that doesn't match the chainId
        // For chainId=1, valid v values are 37 (yParity=0) or 38 (yParity=1)
        final Signature sig = new Signature(r, s, 100);

        // Using chainId=1 should compute recoveryId = 100 - 1*2 - 35 = 63, which is invalid
        assertThrows(IllegalArgumentException.class, () -> sig.getRecoveryId(1L));
    }

    @Test
    void testGetRecoveryIdMismatchedChainId() {
        // Using wrong chainId should throw an exception
        final byte[] r = new byte[32];
        final byte[] s = new byte[32];

        // Signature for chainId=1, yParity=0: v = 1*2 + 35 = 37
        final Signature sig = new Signature(r, s, 37);

        // Using chainId=2 would compute: 37 - 2*2 - 35 = -2, which is invalid
        assertThrows(IllegalArgumentException.class, () -> sig.getRecoveryId(2L));
    }

    @Test
    void testAccessorDefensiveCopy() {
        // HIGH-3: Verify that modifying returned arrays doesn't affect the Signature
        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        r[0] = 0x01;
        s[0] = 0x02;

        final Signature sig = new Signature(r, s, 27);

        // Get the arrays and mutate them
        byte[] retrievedR = sig.r();
        byte[] retrievedS = sig.s();
        retrievedR[0] = (byte) 0xFF;
        retrievedS[0] = (byte) 0xFF;

        // Subsequent calls should return original values (new defensive copies)
        assertEquals(0x01, sig.r()[0], "r() should return defensive copy");
        assertEquals(0x02, sig.s()[0], "s() should return defensive copy");

        // Verify the retrieved arrays were actually modified (sanity check)
        assertEquals((byte) 0xFF, retrievedR[0]);
        assertEquals((byte) 0xFF, retrievedS[0]);
    }
}
