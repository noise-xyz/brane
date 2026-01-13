// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LogSanitizerTest {

    @Test
    void sanitizesPrivateKey() {
        String input = "{\"privateKey\":\"0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef\"}";
        String sanitized = LogSanitizer.sanitize(input);

        assertEquals("{\"privateKey\":\"0x***[REDACTED]***\"}", sanitized);
    }

    @Test
    void sanitizesRawTransaction() {
        String input = "{\"raw\":\"0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef\"}";
        String sanitized = LogSanitizer.sanitize(input);

        assertEquals("{\"raw\":\"0x***[REDACTED]***\"}", sanitized);
    }

    @Test
    void truncatesLongData() {
        String longData = "a".repeat(2500);
        String input = "{\"data\":\"" + longData + "\"}";
        String sanitized = LogSanitizer.sanitize(input);

        assertTrue(sanitized.contains("...(truncated)"));
        assertTrue(sanitized.length() < 2500);
    }

    @Test
    void truncatesToExactMaxLength() {
        // MAX_LOG_LENGTH is 2000, TRUNCATION_SUFFIX is "...(truncated)" (14 chars)
        // So truncated output should be exactly 2000 chars
        String longData = "x".repeat(3000);
        String sanitized = LogSanitizer.sanitize(longData);

        assertEquals(2000, sanitized.length());
        assertTrue(sanitized.endsWith("...(truncated)"));
        // Content before suffix should be 2000 - 14 = 1986 chars of 'x'
        assertEquals("x".repeat(1986) + "...(truncated)", sanitized);
    }

    @Test
    void doesNotTruncateAtExactLimit() {
        // String exactly at MAX_LOG_LENGTH should not be truncated
        String exactLimit = "y".repeat(2000);
        String sanitized = LogSanitizer.sanitize(exactLimit);

        assertEquals(2000, sanitized.length());
        assertEquals(exactLimit, sanitized);
    }

    @Test
    void truncatesStringJustOverLimit() {
        // String just over MAX_LOG_LENGTH should be truncated
        String justOver = "z".repeat(2001);
        String sanitized = LogSanitizer.sanitize(justOver);

        assertEquals(2000, sanitized.length());
        assertTrue(sanitized.endsWith("...(truncated)"));
    }

    @Test
    void handlesNull() {
        assertEquals("null", LogSanitizer.sanitize(null));
    }

    @Test
    void leavesSafeDataUntouched() {
        String input = "{\"method\":\"eth_call\",\"params\":[]}";
        assertEquals(input, LogSanitizer.sanitize(input));
    }
}
