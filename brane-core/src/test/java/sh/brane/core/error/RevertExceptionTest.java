// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.error;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import sh.brane.core.RevertDecoder.RevertKind;

/**
 * Tests for RevertException.
 */
class RevertExceptionTest {

    @Test
    void validCreationWithAllFields() {
        RevertException ex = new RevertException(
                RevertKind.ERROR_STRING,
                "Transfer failed",
                "0x08c379a0...",
                null
        );

        assertEquals(RevertKind.ERROR_STRING, ex.kind());
        assertEquals("Transfer failed", ex.revertReason());
        assertEquals("0x08c379a0...", ex.rawDataHex());
        assertTrue(ex.getMessage().contains("Transfer failed"));
        assertTrue(ex.getMessage().contains("[ERROR_STRING]"));
    }

    @Test
    void validCreationWithNullRevertReason() {
        RevertException ex = new RevertException(
                RevertKind.PANIC,
                null,
                "0x4e487b71...",
                null
        );

        assertEquals(RevertKind.PANIC, ex.kind());
        assertNull(ex.revertReason());
        assertTrue(ex.getMessage().contains("no reason"));
        assertTrue(ex.getMessage().contains("[PANIC]"));
    }

    @Test
    void validCreationWithNullRawDataHex() {
        RevertException ex = new RevertException(
                RevertKind.UNKNOWN,
                "Something went wrong",
                null,
                null
        );

        assertEquals(RevertKind.UNKNOWN, ex.kind());
        assertNull(ex.rawDataHex());
        // UNKNOWN kind doesn't show in message
        assertFalse(ex.getMessage().contains("[UNKNOWN]"));
    }

    @Test
    void validCreationWithCause() {
        RuntimeException cause = new RuntimeException("Underlying error");
        RevertException ex = new RevertException(
                RevertKind.CUSTOM,
                "CustomError()",
                "0xabcdef",
                cause
        );

        assertSame(cause, ex.getCause());
    }

    @Test
    void rejectsNullKind() {
        assertThrows(NullPointerException.class, () -> new RevertException(
                null,  // null kind
                "reason",
                "0x1234",
                null
        ));
    }
}
