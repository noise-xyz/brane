package io.brane.core.tx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.brane.core.types.Blob;

class BlobDecoderTest {

    @Test
    void decodeRejectsNullBlobs() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> BlobDecoder.decode(null));
        assertEquals("blobs", ex.getMessage());
    }

    @Test
    void decodeRejectsEmptyBlobs() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> BlobDecoder.decode(List.of()));
        assertEquals("blobs must not be empty", ex.getMessage());
    }

    @Test
    void decodeRejectsNullElement() {
        List<Blob> blobsWithNull = new ArrayList<>();
        blobsWithNull.add(new Blob(new byte[Blob.SIZE]));
        blobsWithNull.add(null);

        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> BlobDecoder.decode(blobsWithNull));
        assertEquals("blobs[1] is null", ex.getMessage());
    }

    @Test
    void decodeRejectsNullFirstElement() {
        List<Blob> blobsWithNull = new ArrayList<>();
        blobsWithNull.add(null);

        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> BlobDecoder.decode(blobsWithNull));
        assertEquals("blobs[0] is null", ex.getMessage());
    }

    @Test
    void decodeEmptyData() {
        byte[] original = new byte[0];
        SidecarBuilder builder = SidecarBuilder.from(original);
        List<Blob> blobs = builder.blobs();

        byte[] decoded = BlobDecoder.decode(blobs);

        assertArrayEquals(original, decoded);
    }

    @Test
    void decodeSmallData() {
        byte[] original = "Hello, blobs!".getBytes();
        SidecarBuilder builder = SidecarBuilder.from(original);
        List<Blob> blobs = builder.blobs();

        byte[] decoded = BlobDecoder.decode(blobs);

        assertArrayEquals(original, decoded);
    }

    @Test
    void decodeDataWithPattern() {
        byte[] original = new byte[1000];
        for (int i = 0; i < original.length; i++) {
            original[i] = (byte) (i % 256);
        }
        SidecarBuilder builder = SidecarBuilder.from(original);
        List<Blob> blobs = builder.blobs();

        byte[] decoded = BlobDecoder.decode(blobs);

        assertArrayEquals(original, decoded);
    }

    @Test
    void decodeLargeDataRequiringMultipleBlobs() {
        // Data requiring 2 blobs
        int size = SidecarBuilder.USABLE_BYTES_PER_BLOB;
        byte[] original = new byte[size];
        for (int i = 0; i < original.length; i++) {
            original[i] = (byte) ((i * 7) % 256);
        }
        SidecarBuilder builder = SidecarBuilder.from(original);
        List<Blob> blobs = builder.blobs();

        assertEquals(2, blobs.size());

        byte[] decoded = BlobDecoder.decode(blobs);

        assertArrayEquals(original, decoded);
    }

    @Test
    void decodeMaxSizeData() {
        byte[] original = new byte[SidecarBuilder.MAX_DATA_SIZE];
        for (int i = 0; i < original.length; i++) {
            original[i] = (byte) ((i * 13) % 256);
        }
        SidecarBuilder builder = SidecarBuilder.from(original);
        List<Blob> blobs = builder.blobs();

        assertEquals(6, blobs.size());

        byte[] decoded = BlobDecoder.decode(blobs);

        assertArrayEquals(original, decoded);
    }

    @Test
    void decodeDataAtFieldElementBoundary() {
        // Data that exactly fills one field element (31 bytes - 8 byte prefix = 23 bytes)
        byte[] original = new byte[23];
        Arrays.fill(original, (byte) 0xAB);
        SidecarBuilder builder = SidecarBuilder.from(original);
        List<Blob> blobs = builder.blobs();

        byte[] decoded = BlobDecoder.decode(blobs);

        assertArrayEquals(original, decoded);
    }

    @Test
    void decodeDataSpanningFieldElements() {
        // Data that spans multiple field elements
        byte[] original = new byte[100];
        for (int i = 0; i < original.length; i++) {
            original[i] = (byte) (255 - i);
        }
        SidecarBuilder builder = SidecarBuilder.from(original);
        List<Blob> blobs = builder.blobs();

        byte[] decoded = BlobDecoder.decode(blobs);

        assertArrayEquals(original, decoded);
    }

    @Test
    void decodePreservesBinaryData() {
        // Test with all byte values
        byte[] original = new byte[256];
        for (int i = 0; i < 256; i++) {
            original[i] = (byte) i;
        }
        SidecarBuilder builder = SidecarBuilder.from(original);
        List<Blob> blobs = builder.blobs();

        byte[] decoded = BlobDecoder.decode(blobs);

        assertArrayEquals(original, decoded);
    }

    @Test
    void decodeRejectsInvalidLengthPrefix() {
        // Create a blob with an invalid (too large) length prefix
        byte[] blobData = new byte[Blob.SIZE];
        // Set a length prefix larger than possible for a single blob
        // Max usable bytes in one blob is 126976, minus 8 for prefix = 126968
        // Set length to something larger: 200000 = 0x30D40
        long invalidLength = 200000L;
        int prefixOffset = 1; // First byte is 0x00 for field element constraint
        for (int i = SidecarBuilder.LENGTH_PREFIX_SIZE - 1; i >= 0; i--) {
            blobData[prefixOffset + i] = (byte) (invalidLength & 0xFF);
            invalidLength >>>= 8;
        }

        Blob blob = new Blob(blobData);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> BlobDecoder.decode(List.of(blob)));
        assertEquals("Invalid data length: 200000 exceeds maximum 126968 bytes", ex.getMessage());
    }

    @Test
    void roundTripWithVariousSizes() {
        // Test various data sizes for round-trip encoding/decoding
        int[] sizes = {1, 10, 30, 31, 32, 100, 1000, 10000, 100000};
        for (int size : sizes) {
            byte[] original = new byte[size];
            for (int i = 0; i < size; i++) {
                original[i] = (byte) ((i * 17 + size) % 256);
            }

            SidecarBuilder builder = SidecarBuilder.from(original);
            List<Blob> blobs = builder.blobs();
            byte[] decoded = BlobDecoder.decode(blobs);

            assertArrayEquals(original, decoded, "Round-trip failed for size " + size);
        }
    }
}
