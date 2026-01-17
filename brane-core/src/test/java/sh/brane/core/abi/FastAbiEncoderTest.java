// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.abi;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import sh.brane.core.crypto.Keccak256;
import sh.brane.core.types.Address;

class FastAbiEncoderTest {

    // int256 bounds: -2^255 to 2^255 - 1
    private static final BigInteger MAX_INT256 = BigInteger.TWO.pow(255).subtract(BigInteger.ONE);
    private static final BigInteger MIN_INT256 = BigInteger.TWO.pow(255).negate();

    @Test
    void encodeInt256_maxValue() {
        // MAX_INT256 = 2^255 - 1 should be allowed
        byte[] encoded = FastAbiEncoder.encodeInt256(MAX_INT256);

        assertEquals(32, encoded.length);
        // First byte should be 0x7F (01111111) for max positive
        assertEquals(0x7F, encoded[0] & 0xFF);
        // All other bytes should be 0xFF
        for (int i = 1; i < 32; i++) {
            assertEquals(0xFF, encoded[i] & 0xFF);
        }
    }

    @Test
    void encodeInt256_minValue() {
        // MIN_INT256 = -2^255 should be allowed
        byte[] encoded = FastAbiEncoder.encodeInt256(MIN_INT256);

        assertEquals(32, encoded.length);
        // First byte should be 0x80 (10000000) for min negative
        assertEquals(0x80, encoded[0] & 0xFF);
        // All other bytes should be 0x00
        for (int i = 1; i < 32; i++) {
            assertEquals(0x00, encoded[i] & 0xFF);
        }
    }

    @Test
    void encodeInt256_overflow() {
        // 2^255 is one more than MAX_INT256, should be rejected
        BigInteger tooLarge = BigInteger.TWO.pow(255);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> FastAbiEncoder.encodeInt256(tooLarge));
        assertTrue(ex.getMessage().contains("int256 range"));
    }

    @Test
    void encodeInt256_underflow() {
        // -2^255 - 1 is one less than MIN_INT256, should be rejected
        BigInteger tooSmall = MIN_INT256.subtract(BigInteger.ONE);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> FastAbiEncoder.encodeInt256(tooSmall));
        assertTrue(ex.getMessage().contains("int256 range"));
    }

    @Test
    void encodeInt256_zero() {
        byte[] encoded = FastAbiEncoder.encodeInt256(BigInteger.ZERO);

        assertEquals(32, encoded.length);
        for (byte b : encoded) {
            assertEquals(0x00, b);
        }
    }

    @Test
    void encodeInt256_negativeOne() {
        byte[] encoded = FastAbiEncoder.encodeInt256(BigInteger.ONE.negate());

        assertEquals(32, encoded.length);
        // -1 in two's complement is all 0xFF bytes
        for (byte b : encoded) {
            assertEquals(0xFF, b & 0xFF);
        }
    }

    @Test
    void encodeUint256Long_zero() {
        var buffer = java.nio.ByteBuffer.allocate(32);
        FastAbiEncoder.encodeUint256(0L, buffer);

        byte[] encoded = buffer.array();
        assertEquals(32, encoded.length);
        for (byte b : encoded) {
            assertEquals(0x00, b);
        }
    }

    @Test
    void encodeUint256Long_one() {
        var buffer = java.nio.ByteBuffer.allocate(32);
        FastAbiEncoder.encodeUint256(1L, buffer);

        byte[] encoded = buffer.array();
        assertEquals(32, encoded.length);
        // Last byte should be 1, all others 0
        for (int i = 0; i < 31; i++) {
            assertEquals(0x00, encoded[i]);
        }
        assertEquals(0x01, encoded[31]);
    }

    @Test
    void encodeUint256Long_integerMaxValue() {
        var buffer = java.nio.ByteBuffer.allocate(32);
        FastAbiEncoder.encodeUint256((long) Integer.MAX_VALUE, buffer);

        byte[] encoded = buffer.array();
        assertEquals(32, encoded.length);
        // Should match BigInteger encoding
        byte[] expected = FastAbiEncoder.encodeUInt256(BigInteger.valueOf(Integer.MAX_VALUE));
        assertArrayEquals(expected, encoded);
    }

    @Test
    void encodeUint256Long_maxValue() {
        var buffer = java.nio.ByteBuffer.allocate(32);
        FastAbiEncoder.encodeUint256(Long.MAX_VALUE, buffer);

        byte[] encoded = buffer.array();
        assertEquals(32, encoded.length);
        // Should match BigInteger encoding
        byte[] expected = FastAbiEncoder.encodeUInt256(BigInteger.valueOf(Long.MAX_VALUE));
        assertArrayEquals(expected, encoded);
    }

    @Test
    void encodeUint256Long_negativeThrows() {
        var buffer = java.nio.ByteBuffer.allocate(32);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> FastAbiEncoder.encodeUint256(-1L, buffer));
        assertTrue(ex.getMessage().contains("negative"));
    }

    @Test
    void encodeUint256Long_matchesBigIntegerEncoding() {
        // Verify the long variant produces identical output to BigInteger variant
        long[] testValues = {0L, 1L, 255L, 256L, 65535L, 1_000_000L, Long.MAX_VALUE};

        for (long value : testValues) {
            var buffer = java.nio.ByteBuffer.allocate(32);
            FastAbiEncoder.encodeUint256(value, buffer);
            byte[] fromLong = buffer.array();

            byte[] fromBigInteger = FastAbiEncoder.encodeUInt256(BigInteger.valueOf(value));

            assertArrayEquals(fromBigInteger, fromLong,
                    "Mismatch for value " + value);
        }
    }

    @Test
    void encodeTo_matchesEncodeFunction() {
        // Test that encodeTo produces identical output to encodeFunction
        String signature = "transfer(address,uint256)";
        byte[] selector = Arrays.copyOf(
                Keccak256.hash(signature.getBytes(StandardCharsets.UTF_8)), 4);
        Address recipient = new Address("0x1234567890123456789012345678901234567890");
        BigInteger amount = BigInteger.valueOf(1000);

        Object[] args = {new AddressType(recipient), new UInt(256, amount)};
        List<AbiType> argsList = List.of(new AddressType(recipient), new UInt(256, amount));

        // Calculate expected size: 4 (selector) + 32 (address) + 32 (uint256)
        byte[] expected = FastAbiEncoder.encodeFunction(signature, argsList);
        ByteBuffer buffer = ByteBuffer.allocate(expected.length);

        FastAbiEncoder.encodeTo(selector, args, buffer);

        assertArrayEquals(expected, buffer.array());
        assertEquals(expected.length, buffer.position(), "Buffer position should advance by encoded length");
    }

    @Test
    void encodeTo_advancesBufferPosition() {
        byte[] selector = new byte[]{0x01, 0x02, 0x03, 0x04};
        Object[] args = {new UInt(256, BigInteger.TEN)};

        // 4 (selector) + 32 (uint256) = 36 bytes
        ByteBuffer buffer = ByteBuffer.allocate(64);
        int startPos = buffer.position();

        FastAbiEncoder.encodeTo(selector, args, buffer);

        assertEquals(startPos + 36, buffer.position());
    }

    @Test
    void encodeTo_emptyArgs() {
        byte[] selector = new byte[]{0x01, 0x02, 0x03, 0x04};
        Object[] args = {};

        ByteBuffer buffer = ByteBuffer.allocate(4);
        FastAbiEncoder.encodeTo(selector, args, buffer);

        byte[] result = new byte[4];
        buffer.flip();
        buffer.get(result);
        assertArrayEquals(selector, result);
    }

    @Test
    void encodeTo_invalidSelectorLength() {
        byte[] shortSelector = new byte[]{0x01, 0x02, 0x03};
        Object[] args = {};
        ByteBuffer buffer = ByteBuffer.allocate(10);

        var ex = assertThrows(IllegalArgumentException.class,
                () -> FastAbiEncoder.encodeTo(shortSelector, args, buffer));
        assertTrue(ex.getMessage().contains("4 bytes"));

        byte[] longSelector = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05};
        var ex2 = assertThrows(IllegalArgumentException.class,
                () -> FastAbiEncoder.encodeTo(longSelector, args, buffer));
        assertTrue(ex2.getMessage().contains("4 bytes"));
    }

    @Test
    void encodeTo_multipleArgs() {
        String signature = "foo(uint256,bool,address)";
        byte[] selector = Arrays.copyOf(
                Keccak256.hash(signature.getBytes(StandardCharsets.UTF_8)), 4);
        Address addr = new Address("0xabcdef0123456789abcdef0123456789abcdef01");

        Object[] args = {
                new UInt(256, BigInteger.valueOf(42)),
                new Bool(true),
                new AddressType(addr)
        };
        List<AbiType> argsList = List.of(
                new UInt(256, BigInteger.valueOf(42)),
                new Bool(true),
                new AddressType(addr));

        byte[] expected = FastAbiEncoder.encodeFunction(signature, argsList);
        ByteBuffer buffer = ByteBuffer.allocate(expected.length);

        FastAbiEncoder.encodeTo(selector, args, buffer);

        assertArrayEquals(expected, buffer.array());
    }
}
