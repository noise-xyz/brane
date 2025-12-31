package io.brane.core.types;

import io.brane.primitives.Hex;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Hex-encoded 20-byte Ethereum address.
 * <p>
 * Represents an account address (EOA or Contract).
 * <p>
 * <strong>Validation:</strong>
 * <ul>
 * <li>Must start with "0x"</li>
 * <li>Must be exactly 40 hex characters long (20 bytes)</li>
 * </ul>
 * <p>
 * The value is stored in lowercase.
 *
 * @since 0.1.0-alpha
 */
public record Address(@com.fasterxml.jackson.annotation.JsonValue String value) {
    private static final Pattern HEX = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    public Address {
        Objects.requireNonNull(value, "address");
        if (!HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid address: " + value);
        }
        value = value.toLowerCase(Locale.ROOT);
    }

    public byte[] toBytes() {
        return Hex.decode(value);
    }

    public static Address fromBytes(final byte[] bytes) {
        if (bytes == null || bytes.length != 20) {
            throw new IllegalArgumentException("Address must be exactly 20 bytes");
        }
        return new Address("0x" + Hex.encodeNoPrefix(bytes));
    }
}
