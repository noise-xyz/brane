// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.types;

import static org.junit.jupiter.api.Assertions.*;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link HexValidator}.
 */
class HexValidatorTest {

    @Test
    void fixedLengthCreatesPatternFor20Bytes() {
        Pattern pattern = HexValidator.fixedLength(20);

        // Valid 20-byte (40 hex chars) address
        assertTrue(pattern.matcher("0x1234567890abcdef1234567890abcdef12345678").matches());
        assertTrue(pattern.matcher("0x1234567890ABCDEF1234567890ABCDEF12345678").matches());
        assertTrue(pattern.matcher("0x0000000000000000000000000000000000000000").matches());

        // Invalid: wrong length
        assertFalse(pattern.matcher("0x1234").matches());
        assertFalse(pattern.matcher("0x123456789012345678901234567890123456789012").matches());

        // Invalid: missing prefix
        assertFalse(pattern.matcher("1234567890abcdef1234567890abcdef12345678").matches());

        // Invalid: non-hex characters
        assertFalse(pattern.matcher("0x1234567890abcdef1234567890abcdef1234567g").matches());
    }

    @Test
    void fixedLengthCreatesPatternFor32Bytes() {
        Pattern pattern = HexValidator.fixedLength(32);

        // Valid 32-byte (64 hex chars) hash
        assertTrue(pattern.matcher("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef").matches());
        assertTrue(pattern.matcher("0x0000000000000000000000000000000000000000000000000000000000000000").matches());

        // Invalid: too short (20 bytes)
        assertFalse(pattern.matcher("0x1234567890abcdef1234567890abcdef12345678").matches());

        // Invalid: too long
        assertFalse(pattern.matcher("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef00").matches());
    }

    @Test
    void fixedLengthCreatesPatternFor1Byte() {
        Pattern pattern = HexValidator.fixedLength(1);

        assertTrue(pattern.matcher("0xff").matches());
        assertTrue(pattern.matcher("0x00").matches());
        assertTrue(pattern.matcher("0xAB").matches());

        assertFalse(pattern.matcher("0x0").matches());
        assertFalse(pattern.matcher("0x000").matches());
    }

    @Test
    void fixedLengthCreatesPatternFor0Bytes() {
        Pattern pattern = HexValidator.fixedLength(0);

        // Only "0x" should match for zero bytes
        assertTrue(pattern.matcher("0x").matches());

        assertFalse(pattern.matcher("0x00").matches());
        assertFalse(pattern.matcher("").matches());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "0x",           // invalid: empty after prefix for 1-byte pattern
        "0X1234",       // invalid: uppercase X prefix
        "1234",         // invalid: no prefix
        "",             // invalid: empty string
        "0x12 34",      // invalid: space in hex
        "0x12\t34"      // invalid: tab in hex
    })
    void fixedLengthRejectsInvalidFormats(String input) {
        Pattern pattern = HexValidator.fixedLength(2);
        assertFalse(pattern.matcher(input).matches());
    }

    @Test
    void fixedLengthAcceptsMixedCase() {
        Pattern pattern = HexValidator.fixedLength(3);

        assertTrue(pattern.matcher("0xAbCdEf").matches());
        assertTrue(pattern.matcher("0xABCDEF").matches());
        assertTrue(pattern.matcher("0xabcdef").matches());
    }
}
