// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.types;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class HashTest {

    @Test
    void accepts32ByteHex() {
        Hash hash =
                new Hash(
                        "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        assertEquals(32, hash.toBytes().length);
    }

    @Test
    void rejectsWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> new Hash("0x1234"));
    }

    @Test
    void fromBytesRequires32() {
        assertThrows(IllegalArgumentException.class, () -> Hash.fromBytes(new byte[10]));
    }

    @Test
    void roundTripBytes() {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) 0xAB);
        Hash hash = Hash.fromBytes(bytes);
        assertArrayEquals(bytes, hash.toBytes());
    }
}
