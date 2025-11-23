package io.brane.core.types;

import io.brane.primitives.Hex;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Arbitrary length 0x-prefixed hex data.
 */
public record HexData(String value) {
    private static final Pattern HEX = Pattern.compile("^0x([0-9a-fA-F]{2})*$");
    public static final HexData EMPTY = new HexData("0x");

    public HexData {
        Objects.requireNonNull(value, "hex");
        if (!HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid hex data: " + value);
        }
    }

    public byte[] toBytes() {
        return Hex.decode(value);
    }

    public static HexData fromBytes(final byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return EMPTY;
        }
        return new HexData("0x" + Hex.encodeNoPrefix(bytes));
    }
}
