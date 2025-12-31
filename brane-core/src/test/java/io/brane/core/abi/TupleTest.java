package io.brane.core.abi;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TupleTest {

    @Test
    void defensiveCopyPreventsExternalMutation() {
        // Create a mutable list
        List<AbiType> mutableList = new ArrayList<>();
        mutableList.add(new UInt(256, BigInteger.ONE));
        mutableList.add(new UInt(256, BigInteger.TWO));

        // Create Tuple from the mutable list
        Tuple tuple = new Tuple(mutableList);

        // Verify initial size
        assertEquals(2, tuple.components().size());

        // Modify the original list
        mutableList.add(new UInt(256, BigInteger.TEN));
        mutableList.clear();

        // Tuple should be unaffected
        assertEquals(2, tuple.components().size());
        assertEquals(BigInteger.ONE, ((UInt) tuple.components().get(0)).value());
        assertEquals(BigInteger.TWO, ((UInt) tuple.components().get(1)).value());
    }

    @Test
    void componentsListIsImmutable() {
        Tuple tuple = new Tuple(List.of(new UInt(256, BigInteger.ONE)));

        // Attempting to modify the components list should throw
        assertThrows(UnsupportedOperationException.class, () -> {
            tuple.components().add(new UInt(256, BigInteger.TWO));
        });
    }

    @Test
    void staticTupleByteSize() {
        Tuple tuple = new Tuple(List.of(
                new UInt(256, BigInteger.ONE),
                new UInt(256, BigInteger.TWO)));

        assertEquals(64, tuple.byteSize()); // 2 * 32 bytes
        assertFalse(tuple.isDynamic());
        assertEquals("(uint256,uint256)", tuple.typeName());
    }

    @Test
    void dynamicTupleByteSize() {
        Tuple tuple = new Tuple(List.of(
                new UInt(256, BigInteger.ONE),
                Bytes.of(new byte[] {1, 2, 3})));

        assertEquals(32, tuple.byteSize()); // pointer to dynamic content
        assertTrue(tuple.isDynamic());
    }

    @Test
    void emptyTuple() {
        Tuple tuple = new Tuple(List.of());

        assertEquals(0, tuple.byteSize());
        assertFalse(tuple.isDynamic());
        assertEquals("()", tuple.typeName());
    }
}
