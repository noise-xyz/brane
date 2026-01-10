package io.brane.core.tx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SidecarBuilderTest {

    @Test
    void constantsAreCorrect() {
        assertEquals(8, SidecarBuilder.LENGTH_PREFIX_SIZE);
        assertEquals(31, SidecarBuilder.USABLE_BYTES_PER_FIELD_ELEMENT);
        assertEquals(4096, SidecarBuilder.FIELD_ELEMENTS_PER_BLOB);
        assertEquals(126976, SidecarBuilder.USABLE_BYTES_PER_BLOB);
        assertEquals(761848, SidecarBuilder.MAX_DATA_SIZE);
    }

    @Test
    void usableBytesPerBlobIsCalculatedCorrectly() {
        int expected = SidecarBuilder.USABLE_BYTES_PER_FIELD_ELEMENT * SidecarBuilder.FIELD_ELEMENTS_PER_BLOB;
        assertEquals(expected, SidecarBuilder.USABLE_BYTES_PER_BLOB);
    }

    @Test
    void maxDataSizeIsCalculatedCorrectly() {
        // 6 blobs max, minus 8 bytes for length prefix
        int expected = (SidecarBuilder.USABLE_BYTES_PER_BLOB * 6) - SidecarBuilder.LENGTH_PREFIX_SIZE;
        assertEquals(expected, SidecarBuilder.MAX_DATA_SIZE);
    }
}
