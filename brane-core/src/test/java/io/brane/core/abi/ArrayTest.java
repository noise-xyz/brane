package io.brane.core.abi;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArrayTest {

    @Test
    void emptyStaticArrayHasZeroByteSize() {
        // T[0] is a static empty array - should have 0 byte size
        Array<UInt> emptyStaticArray = new Array<>(
                List.of(),
                UInt.class,
                false, // not dynamic length
                "uint256");

        assertEquals(0, emptyStaticArray.byteSize());
        assertFalse(emptyStaticArray.isDynamic());
        assertEquals("uint256[0]", emptyStaticArray.typeName());
    }

    @Test
    void emptyDynamicArrayIsStillDynamic() {
        // T[] with 0 elements is still dynamic
        Array<UInt> emptyDynamicArray = new Array<>(
                List.of(),
                UInt.class,
                true, // dynamic length
                "uint256");

        assertEquals(32, emptyDynamicArray.byteSize()); // pointer slot
        assertTrue(emptyDynamicArray.isDynamic());
        assertEquals("uint256[]", emptyDynamicArray.typeName());
    }

    @Test
    void staticArrayByteSize() {
        Array<UInt> staticArray = new Array<>(
                List.of(new UInt(256, BigInteger.ONE), new UInt(256, BigInteger.TWO)),
                UInt.class,
                false,
                "uint256");

        assertEquals(64, staticArray.byteSize()); // 2 * 32 bytes
        assertFalse(staticArray.isDynamic());
        assertEquals("uint256[2]", staticArray.typeName());
    }
}
