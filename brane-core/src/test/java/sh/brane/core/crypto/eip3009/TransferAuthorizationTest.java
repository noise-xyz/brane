// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.crypto.eip3009;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import sh.brane.core.types.Address;

class TransferAuthorizationTest {

    private static final Address FROM =
        new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    private static final Address TO =
        new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    private static final BigInteger VALUE = BigInteger.valueOf(1_000_000);
    private static final BigInteger VALID_AFTER = BigInteger.ZERO;
    private static final BigInteger VALID_BEFORE = BigInteger.valueOf(1_800_000_000L);
    private static final byte[] NONCE = new byte[32]; // all zeros

    @Test
    void constructor_validatesAllFields() {
        var auth = new TransferAuthorization(FROM, TO, VALUE, VALID_AFTER, VALID_BEFORE, NONCE);
        assertEquals(FROM, auth.from());
        assertEquals(TO, auth.to());
        assertEquals(VALUE, auth.value());
        assertEquals(VALID_AFTER, auth.validAfter());
        assertEquals(VALID_BEFORE, auth.validBefore());
        assertArrayEquals(NONCE, auth.nonce());
    }

    @Test
    void constructor_rejectsNullFrom() {
        assertThrows(NullPointerException.class,
            () -> new TransferAuthorization(null, TO, VALUE, VALID_AFTER, VALID_BEFORE, NONCE));
    }

    @Test
    void constructor_rejectsNullTo() {
        assertThrows(NullPointerException.class,
            () -> new TransferAuthorization(FROM, null, VALUE, VALID_AFTER, VALID_BEFORE, NONCE));
    }

    @Test
    void constructor_rejectsNullValue() {
        assertThrows(NullPointerException.class,
            () -> new TransferAuthorization(FROM, TO, null, VALID_AFTER, VALID_BEFORE, NONCE));
    }

    @Test
    void constructor_rejectsNullValidAfter() {
        assertThrows(NullPointerException.class,
            () -> new TransferAuthorization(FROM, TO, VALUE, null, VALID_BEFORE, NONCE));
    }

    @Test
    void constructor_rejectsNullValidBefore() {
        assertThrows(NullPointerException.class,
            () -> new TransferAuthorization(FROM, TO, VALUE, VALID_AFTER, null, NONCE));
    }

    @Test
    void constructor_rejectsNullNonce() {
        assertThrows(NullPointerException.class,
            () -> new TransferAuthorization(FROM, TO, VALUE, VALID_AFTER, VALID_BEFORE, null));
    }

    @Test
    void constructor_rejectsShortNonce() {
        var shortNonce = new byte[31];
        assertThrows(IllegalArgumentException.class,
            () -> new TransferAuthorization(FROM, TO, VALUE, VALID_AFTER, VALID_BEFORE, shortNonce));
    }

    @Test
    void constructor_rejectsLongNonce() {
        var longNonce = new byte[33];
        assertThrows(IllegalArgumentException.class,
            () -> new TransferAuthorization(FROM, TO, VALUE, VALID_AFTER, VALID_BEFORE, longNonce));
    }

    @Test
    void nonce_returnsDefensiveCopy() {
        var original = new byte[32];
        original[0] = 0x42;
        var auth = new TransferAuthorization(FROM, TO, VALUE, VALID_AFTER, VALID_BEFORE, original);

        // Mutating original should not affect stored nonce
        original[0] = 0x00;
        assertEquals((byte) 0x42, auth.nonce()[0]);

        // Mutating returned nonce should not affect stored nonce
        byte[] returned = auth.nonce();
        returned[0] = 0x00;
        assertEquals((byte) 0x42, auth.nonce()[0]);
    }

    @Test
    void equals_sameValues() {
        var a = new TransferAuthorization(FROM, TO, VALUE, VALID_AFTER, VALID_BEFORE, NONCE);
        var b = new TransferAuthorization(FROM, TO, VALUE, VALID_AFTER, VALID_BEFORE, NONCE);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equals_differentNonce() {
        var nonceA = new byte[32];
        nonceA[0] = 1;
        var nonceB = new byte[32];
        nonceB[0] = 2;
        var a = new TransferAuthorization(FROM, TO, VALUE, VALID_AFTER, VALID_BEFORE, nonceA);
        var b = new TransferAuthorization(FROM, TO, VALUE, VALID_AFTER, VALID_BEFORE, nonceB);
        assertNotEquals(a, b);
    }

    @Test
    void equals_reflexive() {
        var auth = new TransferAuthorization(FROM, TO, VALUE, VALID_AFTER, VALID_BEFORE, NONCE);
        assertEquals(auth, auth);
    }

    @Test
    void equals_notEqualToNull() {
        var auth = new TransferAuthorization(FROM, TO, VALUE, VALID_AFTER, VALID_BEFORE, NONCE);
        assertNotEquals(null, auth);
    }

    @Test
    void constructor_rejectsInvertedWindow() {
        // validBefore (500) <= validAfter (1000)
        assertThrows(IllegalArgumentException.class,
            () -> new TransferAuthorization(FROM, TO, VALUE,
                BigInteger.valueOf(1000), BigInteger.valueOf(500), NONCE));
    }

    @Test
    void constructor_rejectsEqualWindow() {
        // validBefore == validAfter
        var same = BigInteger.valueOf(1000);
        assertThrows(IllegalArgumentException.class,
            () -> new TransferAuthorization(FROM, TO, VALUE, same, same, NONCE));
    }

    @Test
    void toString_includesHexNonce() {
        var nonce = new byte[32];
        nonce[0] = 0x42;
        var auth = new TransferAuthorization(FROM, TO, VALUE, VALID_AFTER, VALID_BEFORE, nonce);
        String str = auth.toString();
        assertTrue(str.contains("nonce=0x42"), "toString should contain hex nonce, got: " + str);
        assertFalse(str.contains("[B@"), "toString should not contain array reference, got: " + str);
    }

    @Test
    void definition_hasCorrectPrimaryType() {
        assertEquals("TransferWithAuthorization", TransferAuthorization.DEFINITION.primaryType());
    }

    @Test
    void definition_hasCorrectFieldCount() {
        var fields = TransferAuthorization.DEFINITION.types().get("TransferWithAuthorization");
        assertNotNull(fields);
        assertEquals(6, fields.size());
    }
}
