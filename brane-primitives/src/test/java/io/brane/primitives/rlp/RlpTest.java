package io.brane.primitives.rlp;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.brane.primitives.Hex;

class RlpTest {
    @Test
    @DisplayName("String encoding vectors from Ethereum RLP spec")
    void testStringEncodingVectors() {
        assertEquals("80", Hex.encodeNoPrefix(RlpString.of(new byte[] {}).encode()));
        assertEquals(
                "83646f67",
                Hex.encodeNoPrefix(RlpString.of("dog".getBytes(StandardCharsets.US_ASCII)).encode()));
        assertEquals("00", Hex.encodeNoPrefix(RlpString.of(new byte[] { 0x00 }).encode()));
        assertEquals("0f", Hex.encodeNoPrefix(RlpString.of(new byte[] { 0x0f }).encode()));
        assertEquals("820400", Hex.encodeNoPrefix(RlpString.of(1024L).encode()));
    }

    @Test
    @DisplayName("List encoding vectors from Ethereum RLP spec")
    void testListEncodingVectors() {
        assertEquals("c0", Hex.encodeNoPrefix(RlpList.of().encode()));

        final RlpList animals = RlpList.of(
                RlpString.of("cat".getBytes(StandardCharsets.US_ASCII)),
                RlpString.of("dog".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("c88363617483646f67", Hex.encodeNoPrefix(animals.encode()));

        final RlpList nestedEmpty = RlpList.of(RlpList.of());
        assertEquals("c1c0", Hex.encodeNoPrefix(nestedEmpty.encode()));
    }

    @Test
    void testRoundTripEncodeDecode() {
        final List<RlpItem> samples = List.of(
                RlpString.of(new byte[] {}),
                RlpString.of(BigInteger.ZERO),
                RlpString.of(15L),
                RlpList.of(
                        RlpString.of("hello".getBytes(StandardCharsets.US_ASCII)),
                        RlpList.of(RlpString.of(1024L))),
                RlpList.of(
                        RlpString.of(new byte[60]),
                        RlpList.of(RlpString.of("braneworld".getBytes(StandardCharsets.US_ASCII)))));

        for (final RlpItem item : samples) {
            final byte[] encoded = Rlp.encode(item);
            final RlpItem decoded = Rlp.decode(encoded);
            assertEquals(item, decoded);
        }
    }

    @Test
    void testLongStringAndListEncoding() {
        final byte[] longString = new byte[60];
        Arrays.fill(longString, (byte) 0x01);

        final byte[] encodedString = RlpString.of(longString).encode();
        assertEquals((byte) 0xB8, encodedString[0]);
        assertEquals((byte) 0x3C, encodedString[1]);
        assertEquals(62, encodedString.length);
        assertArrayEquals(longString, ((RlpString) Rlp.decode(encodedString)).bytes());

        final List<RlpItem> manyItems = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            manyItems.add(RlpString.of((long) i));
        }
        final byte[] encodedList = RlpList.of(manyItems).encode();
        assertEquals((byte) 0xF8, encodedList[0]);
        assertEquals((byte) 0x3C, encodedList[1]);
        final List<RlpItem> decoded = Rlp.decodeList(encodedList);
        assertEquals(60, decoded.size());
        for (int i = 0; i < decoded.size(); i++) {
            assertEquals(RlpString.of((long) i), decoded.get(i));
        }
    }

    @Test
    void testDecodeListGuardrails() {
        assertThrows(IllegalArgumentException.class, () -> Rlp.decodeList(RlpString.of(1L).encode()));

        final byte[] invalidLength = new byte[] { (byte) 0xB8, 0x01, 0x00 };
        assertThrows(IllegalArgumentException.class, () -> Rlp.decode(invalidLength));
    }

    @Test
    @DisplayName("Test RlpString convenience extractors")
    void testConvenienceExtractors() {
        // Test asLong() - valid range
        assertEquals(0L, RlpString.of(new byte[] {}).asLong());
        assertEquals(15L, RlpString.of(15L).asLong());
        assertEquals(1024L, RlpString.of(1024L).asLong());
        assertEquals(Long.MAX_VALUE, RlpString.of(Long.MAX_VALUE).asLong());

        // Test asLong() error for values with too many bytes (> 8)
        final byte[] tooLarge = new byte[9];
        Arrays.fill(tooLarge, (byte) 0xFF);
        final RlpString tooLargeString = RlpString.of(tooLarge);
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                tooLargeString::asLong);
        assertTrue(ex.getMessage().contains("too large for long"));

        // Test asLong() error for values exceeding Long.MAX_VALUE
        // 0x8000000000000000 = Long.MIN_VALUE if interpreted as signed
        final byte[] exceedsMax = new byte[] { (byte) 0x80, 0, 0, 0, 0, 0, 0, 0 };
        final IllegalArgumentException ex2 = assertThrows(
                IllegalArgumentException.class,
                () -> RlpString.of(exceedsMax).asLong());
        assertTrue(ex2.getMessage().contains("exceeds Long.MAX_VALUE"));
        assertTrue(ex2.getMessage().contains("asBigInteger"));

        // Verify the boundary: Long.MAX_VALUE works, but Long.MAX_VALUE + 1 fails
        final byte[] maxValue = new byte[] { 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
        assertEquals(Long.MAX_VALUE, RlpString.of(maxValue).asLong());

        final byte[] maxPlusOne = new byte[] { (byte) 0x80, 0, 0, 0, 0, 0, 0, 0 };
        assertThrows(IllegalArgumentException.class, () -> RlpString.of(maxPlusOne).asLong());

        // Test asBigInteger() - handles values beyond Long.MAX_VALUE
        assertEquals(BigInteger.ZERO, RlpString.of(new byte[] {}).asBigInteger());
        assertEquals(BigInteger.valueOf(1024), RlpString.of(1024L).asBigInteger());
        final BigInteger large = new BigInteger("123456789012345678901234567890");
        assertEquals(large, RlpString.of(large).asBigInteger());

        // Verify asBigInteger() works for values that asLong() rejects
        assertEquals(new BigInteger("9223372036854775808"), RlpString.of(exceedsMax).asBigInteger());

        // Test asHexString()
        assertEquals("0x", RlpString.of(new byte[] {}).asHexString());
        assertEquals("0x0f", RlpString.of(new byte[] { 0x0f }).asHexString());
        assertEquals("0x0400", RlpString.of(1024L).asHexString());
    }

    @Test
    @DisplayName("Test enhanced error messages with context")
    void testEnhancedErrorMessages() {
        // Test bounds check error message includes context
        final byte[] invalidStringLength = new byte[] { (byte) 0x85, 0x01, 0x02 };
        final IllegalArgumentException ex1 = assertThrows(
                IllegalArgumentException.class,
                () -> Rlp.decode(invalidStringLength));
        assertTrue(ex1.getMessage().contains("offset="));
        assertTrue(ex1.getMessage().contains("required="));
        assertTrue(ex1.getMessage().contains("available="));

        // Test non-minimal encoding error includes details
        final byte[] nonMinimalString = new byte[] { (byte) 0xB8, 0x30, 0x01 };
        final IllegalArgumentException ex2 = assertThrows(
                IllegalArgumentException.class,
                () -> Rlp.decode(nonMinimalString));
        assertTrue(ex2.getMessage().contains("Non-minimal"));
        assertTrue(ex2.getMessage().contains("prefix="));

        // Test list length mismatch error includes details
        // 0xC2 = list of length 2.
        // Item 1: 0x82, 0x00, 0x00 (String length 2). Consumes 3 bytes.
        // currentOffset becomes 1 + 3 = 4.
        // expected end = 1 + 2 = 3.
        // Mismatch: expected 3, actual 4.
        final byte[] mismatchedList = new byte[] { (byte) 0xC2, (byte) 0x82, 0x00, 0x00 };
        final IllegalArgumentException ex3 = assertThrows(
                IllegalArgumentException.class,
                () -> Rlp.decode(mismatchedList));
        assertTrue(ex3.getMessage().contains("RLP list length mismatch"));
        assertTrue(ex3.getMessage().contains("expected end=3, actual=4"));
    }
}
