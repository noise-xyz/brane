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
    void testOfWithAccountAndAddressIndex() {
        var path = DerivationPath.of(3, 7);
        assertEquals(3, path.account());
        assertEquals(7, path.addressIndex());
    }

    @Test
    void testOfRejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> DerivationPath.of(-1));
        assertThrows(IllegalArgumentException.class, () -> DerivationPath.of(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> DerivationPath.of(0, -1));
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
        assertEquals("m/44'/60'/100'/0/999", DerivationPath.of(100, 999).toPath());
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
