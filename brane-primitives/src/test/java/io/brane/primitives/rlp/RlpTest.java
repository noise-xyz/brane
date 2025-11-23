package io.brane.primitives.rlp;

import static org.junit.jupiter.api.Assertions.*;

import io.brane.primitives.Hex;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RlpTest {
    @Test
    @DisplayName("String encoding vectors from Ethereum RLP spec")
    void testStringEncodingVectors() {
        assertEquals("80", Hex.encodeNoPrefix(RlpString.of(new byte[] {}).encode()));
        assertEquals(
                "83646f67",
                Hex.encodeNoPrefix(RlpString.of("dog".getBytes(StandardCharsets.US_ASCII)).encode()));
        assertEquals("00", Hex.encodeNoPrefix(RlpString.of(new byte[] {0x00}).encode()));
        assertEquals("0f", Hex.encodeNoPrefix(RlpString.of(new byte[] {0x0f}).encode()));
        assertEquals("820400", Hex.encodeNoPrefix(RlpString.of(1024L).encode()));
    }

    @Test
    @DisplayName("List encoding vectors from Ethereum RLP spec")
    void testListEncodingVectors() {
        assertEquals("c0", Hex.encodeNoPrefix(RlpList.of().encode()));

        final RlpList animals =
                RlpList.of(
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

        final byte[] invalidLength = new byte[] {(byte) 0xB8, 0x01, 0x00};
        assertThrows(IllegalArgumentException.class, () -> Rlp.decode(invalidLength));
    }
}
