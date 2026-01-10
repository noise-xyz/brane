package io.brane.core.tx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.brane.core.types.Blob;

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

    @Test
    void fromRejectsNullData() {
        assertThrows(NullPointerException.class, () -> SidecarBuilder.from(null));
    }

    @Test
    void fromRejectsOversizedData() {
        byte[] oversized = new byte[SidecarBuilder.MAX_DATA_SIZE + 1];
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SidecarBuilder.from(oversized));
        assertEquals("Data size 761849 exceeds maximum 761848 bytes", ex.getMessage());
    }

    @Test
    void fromWithSmallDataCreatesSingleBlob() {
        byte[] data = "Hello, blobs!".getBytes();
        SidecarBuilder builder = SidecarBuilder.from(data);

        List<Blob> blobs = builder.blobs();
        assertNotNull(blobs);
        assertEquals(1, blobs.size());

        // Verify blob structure
        Blob blob = blobs.get(0);
        byte[] blobBytes = blob.toBytes();
        assertEquals(Blob.SIZE, blobBytes.length);

        // First byte of first field element is 0x00
        assertEquals(0x00, blobBytes[0]);

        // Bytes 1-8 contain the length prefix (big-endian)
        long encodedLength = 0;
        for (int i = 1; i <= 8; i++) {
            encodedLength = (encodedLength << 8) | (blobBytes[i] & 0xFF);
        }
        assertEquals(data.length, encodedLength);
    }

    @Test
    void fromEncodesLengthPrefixAsBigEndian() {
        byte[] data = new byte[1000]; // 1000 bytes
        SidecarBuilder builder = SidecarBuilder.from(data);

        Blob blob = builder.blobs().get(0);
        byte[] blobBytes = blob.toBytes();

        // First field element: 0x00 + first 31 bytes of prefixed data
        // The length prefix is 8 bytes big-endian
        // 1000 in big-endian is: 0x00 0x00 0x00 0x00 0x00 0x00 0x03 0xe8

        // Bytes 1-8 in the blob (after 0x00 prefix) contain the length
        assertEquals(0x00, blobBytes[1]);
        assertEquals(0x00, blobBytes[2]);
        assertEquals(0x00, blobBytes[3]);
        assertEquals(0x00, blobBytes[4]);
        assertEquals(0x00, blobBytes[5]);
        assertEquals(0x00, blobBytes[6]);
        assertEquals(0x03, blobBytes[7]);
        assertEquals((byte) 0xe8, blobBytes[8]);
    }

    @Test
    void fromWithEmptyDataCreatesSingleBlob() {
        byte[] empty = new byte[0];
        SidecarBuilder builder = SidecarBuilder.from(empty);

        List<Blob> blobs = builder.blobs();
        assertEquals(1, blobs.size());

        // Verify length prefix is 0
        Blob blob = blobs.get(0);
        byte[] blobBytes = blob.toBytes();
        for (int i = 1; i <= 8; i++) {
            assertEquals(0x00, blobBytes[i], "Length prefix byte " + i + " should be 0");
        }
    }

    @Test
    void fromWithLargeDataCreatesMultipleBlobs() {
        // Data that requires 2 blobs: more than USABLE_BYTES_PER_BLOB - LENGTH_PREFIX_SIZE
        int size = SidecarBuilder.USABLE_BYTES_PER_BLOB; // This plus 8-byte prefix will need 2 blobs
        byte[] data = new byte[size];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }

        SidecarBuilder builder = SidecarBuilder.from(data);

        List<Blob> blobs = builder.blobs();
        assertEquals(2, blobs.size());
    }

    @Test
    void fromPreservesFieldElementConstraint() {
        // Each field element's first byte must be 0x00
        byte[] data = new byte[1000];
        SidecarBuilder builder = SidecarBuilder.from(data);

        Blob blob = builder.blobs().get(0);
        byte[] blobBytes = blob.toBytes();

        // Check that every 32nd byte (first byte of each field element) is 0x00
        for (int fe = 0; fe < Blob.FIELD_ELEMENTS; fe++) {
            int offset = fe * Blob.BYTES_PER_FIELD_ELEMENT;
            assertEquals(0x00, blobBytes[offset],
                    "Field element " + fe + " first byte should be 0x00");
        }
    }

    @Test
    void fromWithMaxDataSucceeds() {
        byte[] maxData = new byte[SidecarBuilder.MAX_DATA_SIZE];
        SidecarBuilder builder = SidecarBuilder.from(maxData);

        List<Blob> blobs = builder.blobs();
        // MAX_DATA_SIZE + LENGTH_PREFIX_SIZE = 6 * USABLE_BYTES_PER_BLOB, so exactly 6 blobs
        assertEquals(6, blobs.size());
    }
}
