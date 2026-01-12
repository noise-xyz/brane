// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.crypto.eip712;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import io.brane.core.error.Eip712Exception;

/**
 * Parser for converting Solidity type strings to {@link Eip712Type} instances.
 * <p>
 * Supports all EIP-712 compatible Solidity types:
 * <ul>
 *   <li>Atomic types: address, bool, bytes, string</li>
 *   <li>Integer types: uint8-uint256, int8-int256</li>
 *   <li>Fixed bytes: bytes1-bytes32</li>
 *   <li>Arrays: T[] (dynamic), T[N] (fixed)</li>
 *   <li>Structs: custom type names</li>
 * </ul>
 *
 * @see <a href="https://eips.ethereum.org/EIPS/eip-712">EIP-712</a>
 */
final class Eip712TypeParser {
    private Eip712TypeParser() {}

    // Regex patterns for type parsing
    private static final Pattern UINT_PATTERN = Pattern.compile("^uint(\\d*)$");
    private static final Pattern INT_PATTERN = Pattern.compile("^int(\\d*)$");
    private static final Pattern FIXED_BYTES_PATTERN = Pattern.compile("^bytes(\\d+)$");
    private static final Pattern FIXED_ARRAY_PATTERN = Pattern.compile("^(.+)\\[(\\d+)\\]$");
    private static final Pattern DYNAMIC_ARRAY_PATTERN = Pattern.compile("^(.+)\\[\\]$");

    /**
     * Parses a Solidity type string into an Eip712Type.
     *
     * @param type  the Solidity type string (e.g., "uint256", "address", "bytes32[]")
     * @param types the type definitions (for resolving struct types)
     * @return the parsed Eip712Type
     * @throws Eip712Exception if the type is unknown or invalid
     */
    static Eip712Type parse(String type, Map<String, List<TypedDataField>> types) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(types, "types");

        // Check for dynamic array suffix: "uint256[]"
        var dynamicArrayMatch = DYNAMIC_ARRAY_PATTERN.matcher(type);
        if (dynamicArrayMatch.matches()) {
            String elementType = dynamicArrayMatch.group(1);
            return new Eip712Type.Array(parse(elementType, types), null);
        }

        // Check for fixed-size array: "bytes32[10]"
        var fixedArrayMatch = FIXED_ARRAY_PATTERN.matcher(type);
        if (fixedArrayMatch.matches()) {
            String elementType = fixedArrayMatch.group(1);
            int size = Integer.parseInt(fixedArrayMatch.group(2));
            if (size <= 0) {
                throw Eip712Exception.invalidValue(type, "array size must be positive");
            }
            return new Eip712Type.Array(parse(elementType, types), size);
        }

        // Check for uint with bit width: "uint256", "uint8", "uint"
        var uintMatch = UINT_PATTERN.matcher(type);
        if (uintMatch.matches()) {
            String bitsStr = uintMatch.group(1);
            int bits = bitsStr.isEmpty() ? 256 : Integer.parseInt(bitsStr);
            return new Eip712Type.Uint(bits);
        }

        // Check for int with bit width: "int256", "int8", "int"
        var intMatch = INT_PATTERN.matcher(type);
        if (intMatch.matches()) {
            String bitsStr = intMatch.group(1);
            int bits = bitsStr.isEmpty() ? 256 : Integer.parseInt(bitsStr);
            return new Eip712Type.Int(bits);
        }

        // Check for fixed bytes: "bytes1" through "bytes32"
        var fixedBytesMatch = FIXED_BYTES_PATTERN.matcher(type);
        if (fixedBytesMatch.matches()) {
            int length = Integer.parseInt(fixedBytesMatch.group(1));
            return new Eip712Type.FixedBytes(length);
        }

        // Primitive types
        return switch (type) {
            case "address" -> new Eip712Type.Address();
            case "bool" -> new Eip712Type.Bool();
            case "bytes" -> new Eip712Type.DynamicBytes();
            case "string" -> new Eip712Type.String();
            default -> {
                // Must be a custom struct type - verify it exists in types map
                if (!types.containsKey(type)) {
                    throw Eip712Exception.unknownType(type);
                }
                yield new Eip712Type.Struct(type);
            }
        };
    }

    /**
     * Converts an Eip712Type back to its Solidity type string.
     *
     * @param type the Eip712Type to convert
     * @return the Solidity type string representation
     */
    static String toSolidityType(Eip712Type type) {
        return switch (type) {
            case Eip712Type.Uint u -> "uint" + u.bits();
            case Eip712Type.Int i -> "int" + i.bits();
            case Eip712Type.Address a -> "address";
            case Eip712Type.Bool b -> "bool";
            case Eip712Type.FixedBytes fb -> "bytes" + fb.length();
            case Eip712Type.DynamicBytes db -> "bytes";
            case Eip712Type.String s -> "string";
            case Eip712Type.Array arr -> {
                String base = toSolidityType(arr.elementType());
                yield arr.fixedLength() != null
                    ? base + "[" + arr.fixedLength() + "]"
                    : base + "[]";
            }
            case Eip712Type.Struct struct -> struct.name();
        };
    }
}
