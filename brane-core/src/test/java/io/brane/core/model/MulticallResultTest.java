package io.brane.core.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.brane.core.types.HexData;

/**
 * Tests for MulticallResult validation.
 */
class MulticallResultTest {

    @Test
    void validSuccessfulResult() {
        MulticallResult result = new MulticallResult(true, HexData.EMPTY);

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals(HexData.EMPTY, result.returnData());
    }

    @Test
    void validFailedResult() {
        HexData revertReason = new HexData("0x08c379a0" + "0".repeat(56));  // Error(string) selector
        MulticallResult result = new MulticallResult(false, revertReason);

        assertFalse(result.success());
        assertEquals(revertReason, result.returnData());
    }

    @Test
    void rejectsNullReturnData() {
        assertThrows(NullPointerException.class, () -> new MulticallResult(
                true,
                null  // null returnData
        ));
    }
}
