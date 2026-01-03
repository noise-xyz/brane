package io.brane.core.util;

import java.util.Objects;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.primitives.Hex;

/**
 * Utility class for creating event topics from typed values.
 *
 * <p>Event topics in Ethereum are 32-byte values used for filtering logs.
 * Indexed event parameters are stored as topics, with smaller types
 * (like addresses) being left-padded with zeros to fill 32 bytes.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Filter Transfer events by sender address
 * Address sender = new Address("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266");
 * Hash senderTopic = Topics.fromAddress(sender);
 * // Result: 0x000000000000000000000000f39Fd6e51aad88F6F4ce6aB8827279cffFb92266
 *
 * LogFilter filter = LogFilter.builder()
 *     .address(contractAddress)
 *     .topic0(Abi.eventTopic("Transfer(address,address,uint256)"))
 *     .topic1(senderTopic)  // indexed 'from' parameter
 *     .build();
 * }</pre>
 *
 * @see io.brane.core.abi.Abi#eventTopic(String)
 * @since 0.1.0-alpha
 */
public final class Topics {

    /**
     * 12 bytes of zero padding (24 hex chars) needed to convert 20-byte addresses to 32-byte topics.
     */
    private static final String ADDRESS_ZERO_PADDING = "0".repeat(24);

    private Topics() {
        // Utility class
    }

    /**
     * Creates a 32-byte topic from an address (left-padded with zeros).
     *
     * <p>Ethereum addresses are 20 bytes. When used as indexed event parameters,
     * they are left-padded with 12 zero bytes to create a 32-byte topic.
     *
     * @param address the address to convert (must not be null)
     * @return the 32-byte topic hash
     * @throws NullPointerException if address is null
     */
    public static Hash fromAddress(Address address) {
        Objects.requireNonNull(address, "address cannot be null");
        // Address is 20 bytes, we need 32 bytes - pad with 12 bytes of zeros on the left
        String cleanAddress = Hex.cleanPrefix(address.value());
        String padded = ADDRESS_ZERO_PADDING + cleanAddress;
        return new Hash("0x" + padded);
    }
}
