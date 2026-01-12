// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.types;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import io.brane.primitives.Hex;

/**
 * Hex-encoded 32-byte hash (Keccak-256).
 * <p>
 * Used for transaction hashes, block hashes, and topic filters.
 * <p>
 * <strong>Validation:</strong>
 * <ul>
 * <li>Must start with "0x"</li>
 * <li>Must be exactly 64 hex characters long (32 bytes)</li>
 * </ul>
 *
 * @since 0.1.0-alpha
 */
public record Hash(@com.fasterxml.jackson.annotation.JsonValue String value) {
    private static final Pattern HEX = Pattern.compile("^0x[0-9a-fA-F]{64}$");

    public Hash {
        Objects.requireNonNull(value, "hash");
        if (!HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid hash: " + value);
        }
        value = value.toLowerCase(Locale.ROOT);
    }

    public byte[] toBytes() {
        return Hex.decode(value);
    }

    public static Hash fromBytes(final byte[] bytes) {
        if (bytes == null || bytes.length != 32) {
            throw new IllegalArgumentException("Hash must be exactly 32 bytes");
        }
        return new Hash("0x" + Hex.encodeNoPrefix(bytes));
    }
}
