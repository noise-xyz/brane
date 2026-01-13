// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for AccessListWithGas validation.
 */
class AccessListWithGasTest {

    @Test
    void validCreation() {
        AccessListWithGas result = new AccessListWithGas(
                Collections.emptyList(),
                BigInteger.valueOf(21000L)
        );

        assertNotNull(result);
        assertTrue(result.accessList().isEmpty());
        assertEquals(BigInteger.valueOf(21000L), result.gasUsed());
    }

    @Test
    void rejectsNullAccessList() {
        assertThrows(NullPointerException.class, () -> new AccessListWithGas(
                null,  // null accessList
                BigInteger.valueOf(21000L)
        ));
    }

    @Test
    void rejectsNullGasUsed() {
        assertThrows(NullPointerException.class, () -> new AccessListWithGas(
                Collections.emptyList(),
                null  // null gasUsed
        ));
    }

    @Test
    void accessListDefensiveCopy() {
        List<AccessListEntry> mutableList = new ArrayList<>();

        AccessListWithGas result = new AccessListWithGas(
                mutableList,
                BigInteger.valueOf(21000L)
        );

        // Modifying original list should not affect result
        mutableList.add(null);

        assertTrue(result.accessList().isEmpty());
    }

    @Test
    void accessListIsImmutable() {
        AccessListWithGas result = new AccessListWithGas(
                Collections.emptyList(),
                BigInteger.valueOf(21000L)
        );

        assertThrows(UnsupportedOperationException.class, () -> result.accessList().add(null));
    }
}
