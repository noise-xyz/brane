// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.abi;

import java.util.List;

/**
 * Encodes ABI types into byte arrays according to the Ethereum Contract ABI
 * specification.
 *
 * <p>
 * This class provides static methods for encoding lists of {@link AbiType}
 * (tuples) and function calls. It handles both static and dynamic types,
 * including
 * correct padding and offset calculation.
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Encode a function call: transfer(address,uint256)
 * List<AbiType> args = List.of(
 *         new AddressType(new Address("0x...")),
 *         new UInt(256, BigInteger.valueOf(1000)));
 * byte[] data = AbiEncoder.encodeFunction("transfer(address,uint256)", args);
 * }</pre>
 *
 * @see AbiType
 * @see AbiDecoder
 */
public final class AbiEncoder {

    private AbiEncoder() {
    }

    /**
     * Encodes a list of ABI types as a tuple.
     *
     * <p>
     * This is equivalent to encoding the arguments of a function call (without the
     * selector)
     * or the data of a constructor.
     *
     * @param args the list of ABI types to encode
     * @return the encoded byte array
     */
    public static byte[] encode(List<AbiType> args) {
        return FastAbiEncoder.encode(args);
    }

    /**
     * Encodes a function call with its arguments.
     *
     * <p>
     * The encoding consists of the 4-byte function selector (Keccak-256 hash of the
     * signature)
     * followed by the encoded arguments.
     *
     * @param signature the function signature (e.g., "transfer(address,uint256)")
     * @param args      the list of arguments to encode
     * @return the encoded byte array including the selector
     */
    public static byte[] encodeFunction(String signature, List<AbiType> args) {
        return FastAbiEncoder.encodeFunction(signature, args);
    }
}
