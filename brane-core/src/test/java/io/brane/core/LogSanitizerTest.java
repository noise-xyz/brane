package io.brane.core;

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
    void handlesNull() {
        assertEquals("null", LogSanitizer.sanitize(null));
    }

    @Test
    void leavesSafeDataUntouched() {
        String input = "{\"method\":\"eth_call\",\"params\":[]}";
        assertEquals(input, LogSanitizer.sanitize(input));
    }
}
