// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.builder;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.brane.core.types.HexData;

class BuilderValidationTest {

    @Test
    void contractCreationWithEmptyHexDataThrows() {
        // "0x" is empty hex data (0 bytes) - should reject for contract creation
        HexData emptyData = new HexData("0x");
        assertEquals(0, emptyData.byteLength());

        assertThrows(BraneTxBuilderException.class, () -> {
            BuilderValidation.validateTarget(null, emptyData);
        });
    }

    @Test
    void contractCreationWithNullDataThrows() {
        assertThrows(BraneTxBuilderException.class, () -> {
            BuilderValidation.validateTarget(null, null);
        });
    }

    @Test
    void contractCreationWithNonEmptyDataSucceeds() {
        HexData bytecode = new HexData("0x6080604052");

        // Should not throw
        assertDoesNotThrow(() -> {
            BuilderValidation.validateTarget(null, bytecode);
        });
    }
}
