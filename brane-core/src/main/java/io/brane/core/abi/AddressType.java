package io.brane.core.abi;

import java.util.Objects;

import io.brane.core.types.Address;

/**
 * Represents a Solidity {@code address} type for ABI encoding/decoding.
 * <p>
 * In the Ethereum ABI encoding specification, addresses are 20 bytes but occupy
 * a full 32-byte slot when encoded (left-padded with 12 zero bytes).
 * <p>
 * <strong>Encoding format:</strong>
 * <pre>
 * | 12 bytes padding | 20 bytes address |
 * |   0x00...00      |  actual address  |
 * </pre>
 * <p>
 * <strong>Example usage:</strong>
 * <pre>{@code
 * // Create from Address
 * Address addr = new Address("0x742d35Cc6634C0532925a3b844Bc9e7595f2bD32");
 * AddressType addressType = new AddressType(addr);
 *
 * // Use with ABI encoder
 * byte[] encoded = AbiEncoder.encode(addressType);
 *
 * // Decode from ABI data
 * AddressType decoded = AbiDecoder.decode(data, AddressType.class);
 * Address address = decoded.value();
 * }</pre>
 * <p>
 * <strong>Validation:</strong> The wrapped {@link Address} value cannot be null
 * and must be a valid 20-byte Ethereum address.
 *
 * @param value the Address wrapper containing the 20-byte value
 * @since 0.1.0-alpha
 */
public record AddressType(Address value) implements AbiType {
    public AddressType {
        Objects.requireNonNull(value, "value cannot be null");
    }

    @Override
    public int byteSize() {
        return 32;
    }

    @Override
    public boolean isDynamic() {
        return false;
    }

    @Override
    public String typeName() {
        return "address";
    }
}
