// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.primitives.rlp;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import sh.brane.primitives.Hex;

/**
 * Tests for {@link RlpNumeric} boundary values and error cases.
 */
class RlpNumericTest {

    @Nested
    @DisplayName("encodeLongUnsigned boundary values")
    class EncodeLongUnsignedBoundary {

        @Test
        @DisplayName("0 encodes as empty string (0x80)")
        void testZero() {
            byte[] encoded = RlpNumeric.encodeLongUnsigned(0L);
            assertEquals("80", Hex.encodeNoPrefix(encoded));
        }

        @Test
        @DisplayName("127 encodes as single byte (0x7f)")
        void test127() {
            byte[] encoded = RlpNumeric.encodeLongUnsigned(127L);
            assertEquals("7f", Hex.encodeNoPrefix(encoded));
        }

        @Test
        @DisplayName("128 encodes as string with length prefix (0x8180)")
        void test128() {
            byte[] encoded = RlpNumeric.encodeLongUnsigned(128L);
            assertEquals("8180", Hex.encodeNoPrefix(encoded));
        }

        @Test
        @DisplayName("255 encodes as string with length prefix (0x81ff)")
        void test255() {
            byte[] encoded = RlpNumeric.encodeLongUnsigned(255L);
            assertEquals("81ff", Hex.encodeNoPrefix(encoded));
        }

        @Test
        @DisplayName("256 encodes as two-byte string (0x820100)")
        void test256() {
            byte[] encoded = RlpNumeric.encodeLongUnsigned(256L);
            assertEquals("820100", Hex.encodeNoPrefix(encoded));
        }

        @Test
        @DisplayName("Long.MAX_VALUE encodes correctly")
        void testLongMaxValue() {
            byte[] encoded = RlpNumeric.encodeLongUnsigned(Long.MAX_VALUE);
            // Long.MAX_VALUE = 0x7FFFFFFFFFFFFFFF (8 bytes)
            assertEquals("887fffffffffffffff", Hex.encodeNoPrefix(encoded));
        }
    }

    @Nested
    @DisplayName("encodeLongUnsigned error cases")
    class EncodeLongUnsignedErrors {

        @Test
        @DisplayName("Negative value throws IllegalArgumentException")
        void testNegativeValue() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> RlpNumeric.encodeLongUnsigned(-1L));
            assertTrue(ex.getMessage().contains("non-negative"));
        }

        @Test
        @DisplayName("Large negative value throws IllegalArgumentException")
        void testLargeNegativeValue() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> RlpNumeric.encodeLongUnsigned(Long.MIN_VALUE));
            assertTrue(ex.getMessage().contains("non-negative"));
        }
    }

    @Nested
    @DisplayName("encodeLongUnsignedItem boundary values")
    class EncodeLongUnsignedItemBoundary {

        @Test
        @DisplayName("0 produces empty RlpString")
        void testZero() {
            RlpItem item = RlpNumeric.encodeLongUnsignedItem(0L);
            assertInstanceOf(RlpString.class, item);
            assertEquals(0, ((RlpString) item).bytes().length);
        }

        @Test
        @DisplayName("127 produces single-byte RlpString")
        void test127() {
            RlpItem item = RlpNumeric.encodeLongUnsignedItem(127L);
            assertInstanceOf(RlpString.class, item);
            assertArrayEquals(new byte[]{0x7f}, ((RlpString) item).bytes());
        }

        @Test
        @DisplayName("128 produces single-byte RlpString with value 0x80")
        void test128() {
            RlpItem item = RlpNumeric.encodeLongUnsignedItem(128L);
            assertInstanceOf(RlpString.class, item);
            assertArrayEquals(new byte[]{(byte) 0x80}, ((RlpString) item).bytes());
        }

        @Test
        @DisplayName("Long.MAX_VALUE produces 8-byte RlpString")
        void testLongMaxValue() {
            RlpItem item = RlpNumeric.encodeLongUnsignedItem(Long.MAX_VALUE);
            assertInstanceOf(RlpString.class, item);
            byte[] expected = new byte[]{0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff};
            assertArrayEquals(expected, ((RlpString) item).bytes());
        }
    }

    @Nested
    @DisplayName("encodeLongUnsignedItem error cases")
    class EncodeLongUnsignedItemErrors {

        @Test
        @DisplayName("Negative value throws IllegalArgumentException")
        void testNegativeValue() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> RlpNumeric.encodeLongUnsignedItem(-1L));
            assertTrue(ex.getMessage().contains("non-negative"));
        }
    }

    @Nested
    @DisplayName("encodeBigIntegerUnsigned boundary values")
    class EncodeBigIntegerUnsignedBoundary {

        @Test
        @DisplayName("0 encodes as empty string (0x80)")
        void testZero() {
            byte[] encoded = RlpNumeric.encodeBigIntegerUnsigned(BigInteger.ZERO);
            assertEquals("80", Hex.encodeNoPrefix(encoded));
        }

        @Test
        @DisplayName("127 encodes as single byte (0x7f)")
        void test127() {
            byte[] encoded = RlpNumeric.encodeBigIntegerUnsigned(BigInteger.valueOf(127));
            assertEquals("7f", Hex.encodeNoPrefix(encoded));
        }

        @Test
        @DisplayName("128 encodes as string with length prefix (0x8180)")
        void test128() {
            byte[] encoded = RlpNumeric.encodeBigIntegerUnsigned(BigInteger.valueOf(128));
            assertEquals("8180", Hex.encodeNoPrefix(encoded));
        }

        @Test
        @DisplayName("255 encodes as string with length prefix (0x81ff)")
        void test255() {
            byte[] encoded = RlpNumeric.encodeBigIntegerUnsigned(BigInteger.valueOf(255));
            assertEquals("81ff", Hex.encodeNoPrefix(encoded));
        }

        @Test
        @DisplayName("256 encodes as two-byte string (0x820100)")
        void test256() {
            byte[] encoded = RlpNumeric.encodeBigIntegerUnsigned(BigInteger.valueOf(256));
            assertEquals("820100", Hex.encodeNoPrefix(encoded));
        }

        @Test
        @DisplayName("Long.MAX_VALUE encodes correctly")
        void testLongMaxValue() {
            byte[] encoded = RlpNumeric.encodeBigIntegerUnsigned(BigInteger.valueOf(Long.MAX_VALUE));
            assertEquals("887fffffffffffffff", Hex.encodeNoPrefix(encoded));
        }

        @Test
        @DisplayName("Value larger than Long.MAX_VALUE encodes correctly")
        void testBeyondLongMaxValue() {
            // Long.MAX_VALUE + 1 = 0x8000000000000000
            BigInteger beyondMax = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
            byte[] encoded = RlpNumeric.encodeBigIntegerUnsigned(beyondMax);
            assertEquals("888000000000000000", Hex.encodeNoPrefix(encoded));
        }
    }

    @Nested
    @DisplayName("encodeBigIntegerUnsigned error cases")
    class EncodeBigIntegerUnsignedErrors {

        @Test
        @DisplayName("Null value throws NullPointerException")
        void testNullValue() {
            NullPointerException ex = assertThrows(
                    NullPointerException.class,
                    () -> RlpNumeric.encodeBigIntegerUnsigned(null));
            assertTrue(ex.getMessage().contains("null"));
        }

        @Test
        @DisplayName("Negative value throws IllegalArgumentException")
        void testNegativeValue() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> RlpNumeric.encodeBigIntegerUnsigned(BigInteger.valueOf(-1)));
            assertTrue(ex.getMessage().contains("non-negative"));
        }

        @Test
        @DisplayName("Large negative value throws IllegalArgumentException")
        void testLargeNegativeValue() {
            BigInteger largeNegative = BigInteger.valueOf(Long.MIN_VALUE);
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> RlpNumeric.encodeBigIntegerUnsigned(largeNegative));
            assertTrue(ex.getMessage().contains("non-negative"));
        }
    }

    @Nested
    @DisplayName("encodeBigIntegerUnsignedItem boundary values")
    class EncodeBigIntegerUnsignedItemBoundary {

        @Test
        @DisplayName("0 produces empty RlpString")
        void testZero() {
            RlpItem item = RlpNumeric.encodeBigIntegerUnsignedItem(BigInteger.ZERO);
            assertInstanceOf(RlpString.class, item);
            assertEquals(0, ((RlpString) item).bytes().length);
        }

        @Test
        @DisplayName("127 produces single-byte RlpString")
        void test127() {
            RlpItem item = RlpNumeric.encodeBigIntegerUnsignedItem(BigInteger.valueOf(127));
            assertInstanceOf(RlpString.class, item);
            assertArrayEquals(new byte[]{0x7f}, ((RlpString) item).bytes());
        }

        @Test
        @DisplayName("128 produces single-byte RlpString with value 0x80")
        void test128() {
            RlpItem item = RlpNumeric.encodeBigIntegerUnsignedItem(BigInteger.valueOf(128));
            assertInstanceOf(RlpString.class, item);
            assertArrayEquals(new byte[]{(byte) 0x80}, ((RlpString) item).bytes());
        }
    }

    @Nested
    @DisplayName("encodeBigIntegerUnsignedItem error cases")
    class EncodeBigIntegerUnsignedItemErrors {

        @Test
        @DisplayName("Null value throws NullPointerException")
        void testNullValue() {
            NullPointerException ex = assertThrows(
                    NullPointerException.class,
                    () -> RlpNumeric.encodeBigIntegerUnsignedItem(null));
            assertTrue(ex.getMessage().contains("null"));
        }

        @Test
        @DisplayName("Negative value throws IllegalArgumentException")
        void testNegativeValue() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> RlpNumeric.encodeBigIntegerUnsignedItem(BigInteger.valueOf(-1)));
            assertTrue(ex.getMessage().contains("non-negative"));
        }
    }

    @Nested
    @DisplayName("Round-trip encoding consistency")
    class RoundTripConsistency {

        @Test
        @DisplayName("Long and BigInteger produce same encoding for same value")
        void testConsistentEncoding() {
            long[] testValues = {0L, 1L, 127L, 128L, 255L, 256L, 1024L, Long.MAX_VALUE};

            for (long value : testValues) {
                byte[] fromLong = RlpNumeric.encodeLongUnsigned(value);
                byte[] fromBigInt = RlpNumeric.encodeBigIntegerUnsigned(BigInteger.valueOf(value));
                assertArrayEquals(fromLong, fromBigInt,
                        "Encoding mismatch for value " + value);
            }
        }

        @Test
        @DisplayName("Item and byte encoding produce same RLP output")
        void testItemVsByteEncoding() {
            long[] testValues = {0L, 127L, 128L, 255L, 256L, Long.MAX_VALUE};

            for (long value : testValues) {
                byte[] directEncoding = RlpNumeric.encodeLongUnsigned(value);
                RlpItem item = RlpNumeric.encodeLongUnsignedItem(value);
                byte[] itemEncoding = Rlp.encode(item);
                assertArrayEquals(directEncoding, itemEncoding,
                        "Item encoding mismatch for value " + value);
            }
        }
    }
}
