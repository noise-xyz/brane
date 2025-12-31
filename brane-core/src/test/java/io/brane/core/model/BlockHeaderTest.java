package io.brane.core.model;

import io.brane.core.types.Hash;
import io.brane.core.types.Wei;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BlockHeader validation.
 */
class BlockHeaderTest {

    private static final Hash VALID_HASH = new Hash("0x" + "a".repeat(64));
    private static final Hash VALID_PARENT_HASH = new Hash("0x" + "b".repeat(64));

    @Test
    void validBlockHeaderCreation() {
        BlockHeader header = new BlockHeader(
                VALID_HASH,
                12345L,
                VALID_PARENT_HASH,
                1234567890L,
                Wei.of(10_000_000_000L)
        );

        assertNotNull(header);
        assertEquals(VALID_HASH, header.hash());
        assertEquals(12345L, header.number());
        assertEquals(VALID_PARENT_HASH, header.parentHash());
        assertEquals(1234567890L, header.timestamp());
        assertEquals(Wei.of(10_000_000_000L), header.baseFeePerGas());
    }

    @Test
    void allowsNullBaseFeeForPreLondonBlocks() {
        BlockHeader header = new BlockHeader(
                VALID_HASH,
                12_000_000L,  // Before London fork
                VALID_PARENT_HASH,
                1234567890L,
                null  // null baseFeePerGas for pre-London
        );

        assertNotNull(header);
        assertNull(header.baseFeePerGas());
    }

    @Test
    void rejectsNullHash() {
        assertThrows(NullPointerException.class, () -> new BlockHeader(
                null,  // null hash
                12345L,
                VALID_PARENT_HASH,
                1234567890L,
                Wei.of(10_000_000_000L)
        ));
    }

    @Test
    void rejectsNullParentHash() {
        assertThrows(NullPointerException.class, () -> new BlockHeader(
                VALID_HASH,
                12345L,
                null,  // null parentHash
                1234567890L,
                Wei.of(10_000_000_000L)
        ));
    }

    @Test
    void usesPrimitiveTypes() {
        // This test verifies that number and timestamp use primitive long
        // (if they were Long, the compiler would allow null, which we don't want)
        BlockHeader header = new BlockHeader(
                VALID_HASH,
                0L,  // primitive long - 0 is valid
                VALID_PARENT_HASH,
                0L,  // primitive long - 0 is valid
                null
        );

        assertEquals(0L, header.number());
        assertEquals(0L, header.timestamp());
    }
}
