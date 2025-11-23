package io.brane.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LogSanitizerTest {

    @Test
    void redactsPrivateKeysAndRawTransactions() {
        final String input =
                "{\"privateKey\":\"0x1234abcd\",\"raw\":\"0xdeadbeef\",\"other\":1}";

        final String sanitized = LogSanitizer.sanitize(input);

        assertTrue(sanitized.contains("0x***[REDACTED]***"));
        assertTrue(sanitized.contains("\"raw\":\"0x***[REDACTED]***\""));
    }

    @Test
    void truncatesVeryLargePayloads() {
        final String longInput = "x".repeat(2100);

        final String sanitized = LogSanitizer.sanitize(longInput);

        assertEquals(208, sanitized.length());
        assertTrue(sanitized.endsWith("...(truncated)"));
    }

    @Test
    void returnsNullStringLiteralForNullInput() {
        assertEquals("null", LogSanitizer.sanitize(null));
    }
}
