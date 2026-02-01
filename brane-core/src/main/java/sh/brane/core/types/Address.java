// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.types;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import sh.brane.primitives.Hex;

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
    private static final int BYTE_LENGTH = 20;
    private static final Pattern HEX = HexValidator.fixedLength(BYTE_LENGTH);

    /**
     * The zero address ({@code 0x0000000000000000000000000000000000000000}).
     * <p>
     * Commonly used as a sentinel value to represent "no address" or the burn address.
     */
    public static final Address ZERO = new Address("0x0000000000000000000000000000000000000000");

    public Address {
        Objects.requireNonNull(value, "address");
        if (!HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid address: " + value);
        }
        value = value.toLowerCase(Locale.ROOT);
    }

    /**
     * Decodes this address to a 20-byte array.
     *
     * <p><b>Allocation:</b> 1 allocation per call (result byte[]). For hot paths,
     * cache the result or use {@link Hex#decodeTo} with a pre-allocated buffer.
     *
     * @return 20-byte array representation
     */
    public byte[] toBytes() {
        return Hex.decode(value);
    }

    public static Address fromBytes(final byte[] bytes) {
        if (bytes == null || bytes.length != BYTE_LENGTH) {
            throw new IllegalArgumentException("Address must be exactly " + BYTE_LENGTH + " bytes");
        }
        return new Address("0x" + Hex.encodeNoPrefix(bytes));
    }
}
