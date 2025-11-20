package io.brane.core.types;

import io.brane.internal.web3j.utils.Numeric;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Hex-encoded 20-byte Ethereum address.
 */
public record Address(String value) {
    private static final Pattern HEX = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    public Address {
        Objects.requireNonNull(value, "address");
        if (!HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid address: " + value);
        }
        value = value.toLowerCase(Locale.ROOT);
    }

    public byte[] toBytes() {
        return Numeric.hexStringToByteArray(value);
    }

    public static Address fromBytes(final byte[] bytes) {
        if (bytes == null || bytes.length != 20) {
            throw new IllegalArgumentException("Address must be exactly 20 bytes");
        }
        return new Address("0x" + Numeric.toHexStringNoPrefix(bytes));
    }
}
