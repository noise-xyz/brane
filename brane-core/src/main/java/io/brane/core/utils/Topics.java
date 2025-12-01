package io.brane.core.utils;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.primitives.Hex;

/**
 * Utility class for creating event topics.
 */
public final class Topics {

    private Topics() {
        // Utility class
    }

    /**
     * Creates a 32-byte topic from an address (left-padded with zeros).
     * 
     * @param address the address to convert
     * @return the 32-byte topic hash
     */
    public static Hash fromAddress(Address address) {
        if (address == null) {
            throw new IllegalArgumentException("Address cannot be null");
        }
        // Address is 20 bytes, we need 32 bytes.
        // Pad with 12 bytes of zeros on the left.
        String cleanAddress = Hex.cleanPrefix(address.value());
        String padded = "000000000000000000000000" + cleanAddress;
        return new Hash("0x" + padded);
    }
}
