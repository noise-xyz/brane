package io.brane.core.types;

import io.brane.internal.web3j.utils.Numeric;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Hex-encoded 32-byte hash.
 */
public record Hash(String value) {
    private static final Pattern HEX = Pattern.compile("^0x[0-9a-fA-F]{64}$");

    public Hash {
        Objects.requireNonNull(value, "hash");
        if (!HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid hash: " + value);
        }
        value = value.toLowerCase(Locale.ROOT);
    }

    public byte[] toBytes() {
        return Numeric.hexStringToByteArray(value);
    }

    public static Hash fromBytes(final byte[] bytes) {
        if (bytes == null || bytes.length != 32) {
            throw new IllegalArgumentException("Hash must be exactly 32 bytes");
        }
        return new Hash("0x" + Numeric.toHexStringNoPrefix(bytes));
    }
}
