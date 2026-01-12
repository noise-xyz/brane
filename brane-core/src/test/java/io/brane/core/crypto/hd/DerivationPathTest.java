// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.crypto.hd;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for BIP-44 derivation path record.
 */
class DerivationPathTest {

    @Test
    void testConstructorWithValidValues() {
        var path = new DerivationPath(0, 0);
        assertEquals(0, path.account());
        assertEquals(0, path.addressIndex());

        var path2 = new DerivationPath(5, 10);
        assertEquals(5, path2.account());
        assertEquals(10, path2.addressIndex());
    }

    @Test
    void testConstructorWithMaxIndex() {
        var path = new DerivationPath(DerivationPath.MAX_INDEX, DerivationPath.MAX_INDEX);
        assertEquals(DerivationPath.MAX_INDEX, path.account());
        assertEquals(DerivationPath.MAX_INDEX, path.addressIndex());
    }

    /**
     * Verifies that the constructor Javadoc accurately reflects its validation behavior.
     * The constructor only rejects negative values; MAX_INDEX is accepted as a valid upper bound.
     * This matches the Javadoc which states: "@throws IllegalArgumentException if account or addressIndex is negative"
     */
    @Test
    void testConstructorJavadocMatchesBehavior() {
        // Constructor accepts MAX_INDEX (documented upper bound)
        assertDoesNotThrow(() -> new DerivationPath(DerivationPath.MAX_INDEX, DerivationPath.MAX_INDEX));

        // Constructor rejects negative values (as documented)
        assertThrows(IllegalArgumentException.class, () -> new DerivationPath(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new DerivationPath(0, -1));
    }

    @Test
    void testConstructorRejectsNegativeAccount() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new DerivationPath(-1, 0));
        assertTrue(ex.getMessage().contains("Account index cannot be negative"));
    }

    @Test
    void testConstructorRejectsNegativeAddressIndex() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new DerivationPath(0, -1));
        assertTrue(ex.getMessage().contains("Address index cannot be negative"));
    }

    @Test
    void testConstructorRejectsOverflowAccount() {
        // Since MAX_INDEX == Integer.MAX_VALUE, we can't exceed it with int
        // But we can test the edge case where MAX_INDEX + 1 would be negative due to overflow
        // The validation catches negative values, so Integer.MIN_VALUE should fail
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new DerivationPath(Integer.MIN_VALUE, 0));
        assertTrue(ex.getMessage().contains("Account index cannot be negative"));
    }

    @Test
    void testConstructorRejectsOverflowAddressIndex() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new DerivationPath(0, Integer.MIN_VALUE));
        assertTrue(ex.getMessage().contains("Address index cannot be negative"));
    }

    @Test
    void testParseRejectsOverflowIndex() {
        // Test that parsing a value larger than MAX_INDEX throws
        String overflowAccount = "m/44'/60'/2147483648'/0/0"; // MAX_INDEX + 1
        var ex1 = assertThrows(IllegalArgumentException.class,
                () -> DerivationPath.parse(overflowAccount));
        assertTrue(ex1.getMessage().contains("exceeds maximum"));

        String overflowAddress = "m/44'/60'/0'/0/2147483648"; // MAX_INDEX + 1
        var ex2 = assertThrows(IllegalArgumentException.class,
                () -> DerivationPath.parse(overflowAddress));
        assertTrue(ex2.getMessage().contains("exceeds maximum"));
    }

    @Test
    void testParseRejectsNegativeIndex() {
        String negativeAccount = "m/44'/60'/-1'/0/0";
        var ex1 = assertThrows(IllegalArgumentException.class,
                () -> DerivationPath.parse(negativeAccount));
        assertTrue(ex1.getMessage().contains("negative"));

        String negativeAddress = "m/44'/60'/0'/0/-1";
        var ex2 = assertThrows(IllegalArgumentException.class,
                () -> DerivationPath.parse(negativeAddress));
        assertTrue(ex2.getMessage().contains("negative"));
    }

    @Test
    void testMaxIndexConstant() {
        assertEquals(0x7FFFFFFF, DerivationPath.MAX_INDEX);
        assertEquals(Integer.MAX_VALUE, DerivationPath.MAX_INDEX);
    }

    @Test
    void testOfWithAddressIndexOnly() {
        var path = DerivationPath.of(5);
        assertEquals(0, path.account());
        assertEquals(5, path.addressIndex());
    }

    @Test
    void testConstructorWithAccountAndAddressIndex() {
        var path = new DerivationPath(3, 7);
        assertEquals(3, path.account());
        assertEquals(7, path.addressIndex());
    }

    @Test
    void testOfRejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> DerivationPath.of(-1));
    }

    @Test
    void testParseValidPath() {
        var path = DerivationPath.parse("m/44'/60'/0'/0/0");
        assertEquals(0, path.account());
        assertEquals(0, path.addressIndex());
    }

    @Test
    void testParseWithDifferentAccountAndIndex() {
        var path = DerivationPath.parse("m/44'/60'/5'/0/10");
        assertEquals(5, path.account());
        assertEquals(10, path.addressIndex());
    }

    @Test
    void testParseWithHSuffix() {
        var path = DerivationPath.parse("m/44h/60h/3h/0/7");
        assertEquals(3, path.account());
        assertEquals(7, path.addressIndex());
    }

    @Test
    void testParseRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> DerivationPath.parse(null));
    }

    @Test
    void testParseRejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> DerivationPath.parse(""));
    }

    @Test
    void testParseRejectsInvalidFormat() {
        // Wrong number of components
        assertThrows(IllegalArgumentException.class, () -> DerivationPath.parse("m/44'/60'"));
        assertThrows(IllegalArgumentException.class, () -> DerivationPath.parse("m/44'/60'/0'/0/0/1"));

        // Doesn't start with m
        assertThrows(IllegalArgumentException.class, () -> DerivationPath.parse("M/44'/60'/0'/0/0"));
        assertThrows(IllegalArgumentException.class, () -> DerivationPath.parse("44'/60'/0'/0/0"));

        // Wrong purpose
        assertThrows(IllegalArgumentException.class, () -> DerivationPath.parse("m/45'/60'/0'/0/0"));

        // Wrong coin type
        assertThrows(IllegalArgumentException.class, () -> DerivationPath.parse("m/44'/61'/0'/0/0"));

        // Account not hardened
        assertThrows(IllegalArgumentException.class, () -> DerivationPath.parse("m/44'/60'/0/0/0"));

        // Wrong change index
        assertThrows(IllegalArgumentException.class, () -> DerivationPath.parse("m/44'/60'/0'/1/0"));

        // Invalid numeric values
        assertThrows(IllegalArgumentException.class, () -> DerivationPath.parse("m/44'/60'/abc'/0/0"));
        assertThrows(IllegalArgumentException.class, () -> DerivationPath.parse("m/44'/60'/0'/0/xyz"));
    }

    @Test
    void testToPath() {
        assertEquals("m/44'/60'/0'/0/0", new DerivationPath(0, 0).toPath());
        assertEquals("m/44'/60'/5'/0/10", new DerivationPath(5, 10).toPath());
        assertEquals("m/44'/60'/100'/0/999", new DerivationPath(100, 999).toPath());
    }

    @Test
    void testParseAndToPathRoundTrip() {
        String original = "m/44'/60'/7'/0/42";
        var path = DerivationPath.parse(original);
        assertEquals(original, path.toPath());
    }

    @Test
    void testRecordEquality() {
        var path1 = new DerivationPath(1, 2);
        var path2 = new DerivationPath(1, 2);
        var path3 = new DerivationPath(1, 3);

        assertEquals(path1, path2);
        assertNotEquals(path1, path3);
        assertEquals(path1.hashCode(), path2.hashCode());
    }

    @Test
    void testRecordToString() {
        var path = new DerivationPath(3, 5);
        String str = path.toString();
        assertTrue(str.contains("3"));
        assertTrue(str.contains("5"));
    }
}
