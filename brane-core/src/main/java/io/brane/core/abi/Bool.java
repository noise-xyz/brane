// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.abi;

/**
 * Represents a Solidity boolean ({@code bool}) type for ABI encoding/decoding.
 * <p>
 * In the Ethereum ABI encoding specification, boolean values occupy a full 32-byte slot:
 * <ul>
 * <li>{@code true} is encoded as {@code 0x0000...0001} (31 zero bytes followed by 0x01)</li>
 * <li>{@code false} is encoded as {@code 0x0000...0000} (32 zero bytes)</li>
 * </ul>
 * <p>
 * <strong>Example usage:</strong>
 * <pre>{@code
 * // Create from boolean
 * Bool trueValue = new Bool(true);
 * Bool falseValue = new Bool(false);
 *
 * // Use with ABI encoder
 * byte[] encoded = AbiEncoder.encode(new Bool(true));
 *
 * // Decode from ABI data
 * Bool decoded = AbiDecoder.decode(data, Bool.class);
 * boolean value = decoded.value();
 * }</pre>
 *
 * @param value the boolean value
 * @since 0.1.0-alpha
 */
public record Bool(boolean value) implements StaticAbiType {
    @Override
    public String typeName() {
        return "bool";
    }
}
