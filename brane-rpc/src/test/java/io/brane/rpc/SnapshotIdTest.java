// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

class SnapshotIdTest {

    @Test
    void constructorValidatesNull() {
        assertThrows(NullPointerException.class, () -> new SnapshotId(null));
    }

    @Test
    void constructorValidates0xPrefix() {
        // Missing 0x prefix
        assertThrows(IllegalArgumentException.class, () -> new SnapshotId("1"));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotId("abc"));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotId("0X1")); // uppercase X not allowed
    }

    @Test
    void constructorRejectsInvalidHexCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new SnapshotId("0xGHI"));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotId("0x1g2"));
    }

    @Test
    void constructorAcceptsValid0xPrefixedValues() {
        // Typical snapshot IDs
        SnapshotId id1 = new SnapshotId("0x1");
        assertEquals("0x1", id1.value());

        SnapshotId id2 = new SnapshotId("0xa");
        assertEquals("0xa", id2.value());

        SnapshotId id3 = new SnapshotId("0xABCDEF");
        assertEquals("0xABCDEF", id3.value());

        // Empty hex after prefix (edge case, but valid)
        SnapshotId id4 = new SnapshotId("0x");
        assertEquals("0x", id4.value());

        // Longer hex values
        SnapshotId id5 = new SnapshotId("0x1234567890abcdef");
        assertEquals("0x1234567890abcdef", id5.value());
    }

    @Test
    void fromFactoryMethod() {
        SnapshotId id = SnapshotId.from("0x1");
        assertEquals("0x1", id.value());
    }

    @Test
    void fromFactoryMethodValidatesNull() {
        assertThrows(NullPointerException.class, () -> SnapshotId.from(null));
    }

    @Test
    void fromFactoryMethodValidates0xPrefix() {
        assertThrows(IllegalArgumentException.class, () -> SnapshotId.from("1"));
    }

    @Test
    void revertUsingValidatesTesterNotNull() {
        SnapshotId id = new SnapshotId("0x1");
        assertThrows(NullPointerException.class, () -> id.revertUsing(null));
    }

    @Test
    void revertUsingCallsTesterRevert() {
        SnapshotId id = new SnapshotId("0x1");
        DefaultTester tester = mock(DefaultTester.class);
        when(tester.revert(id)).thenReturn(true);

        boolean result = id.revertUsing(tester);

        assertTrue(result);
        verify(tester).revert(id);
    }

    @Test
    void revertUsingReturnsFalseWhenTesterReturnsFalse() {
        SnapshotId id = new SnapshotId("0x1");
        DefaultTester tester = mock(DefaultTester.class);
        when(tester.revert(id)).thenReturn(false);

        boolean result = id.revertUsing(tester);

        assertFalse(result);
        verify(tester).revert(id);
    }

    @Test
    void recordEquality() {
        SnapshotId id1 = new SnapshotId("0x1");
        SnapshotId id2 = new SnapshotId("0x1");
        SnapshotId id3 = new SnapshotId("0x2");

        assertEquals(id1, id2);
        assertNotEquals(id1, id3);
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void toStringIncludesValue() {
        SnapshotId id = new SnapshotId("0x1");
        assertTrue(id.toString().contains("0x1"));
    }
}
