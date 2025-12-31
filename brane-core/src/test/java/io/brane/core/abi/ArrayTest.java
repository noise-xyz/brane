package io.brane.core.abi;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.ArrayList;
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

    @Test
    void defensiveCopyPreventsExternalMutation() {
        // Create a mutable list
        List<UInt> mutableList = new ArrayList<>();
        mutableList.add(new UInt(256, BigInteger.ONE));
        mutableList.add(new UInt(256, BigInteger.TWO));

        // Create Array from the mutable list
        Array<UInt> array = new Array<>(mutableList, UInt.class, false, "uint256");

        // Verify initial size
        assertEquals(2, array.values().size());

        // Modify the original list
        mutableList.add(new UInt(256, BigInteger.TEN));
        mutableList.clear();

        // Array should be unaffected
        assertEquals(2, array.values().size());
        assertEquals(BigInteger.ONE, array.values().get(0).value());
        assertEquals(BigInteger.TWO, array.values().get(1).value());
    }

    @Test
    void valuesListIsImmutable() {
        Array<UInt> array = new Array<>(
                List.of(new UInt(256, BigInteger.ONE)),
                UInt.class,
                false,
                "uint256");

        // Attempting to modify the values list should throw
        assertThrows(UnsupportedOperationException.class, () -> {
            array.values().add(new UInt(256, BigInteger.TWO));
        });
    }
}
