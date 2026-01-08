package io.brane.core.crypto.eip712;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.brane.core.crypto.Keccak256;
import io.brane.core.error.Eip712Exception;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;

/**
 * Internal encoder for EIP-712 typed data structures.
 * <p>
 * Implements the encoding algorithms specified in EIP-712:
 * <ul>
 *   <li>{@link #encodeType} - canonical type string encoding</li>
 *   <li>{@link #typeHash} - keccak256 of the encoded type</li>
 *   <li>{@link #encodeData} - field value encoding</li>
 *   <li>{@link #hashStruct} - complete struct hashing</li>
 *   <li>{@link #hashDomain} - domain separator computation</li>
 * </ul>
 *
 * @see <a href="https://eips.ethereum.org/EIPS/eip-712">EIP-712</a>
 */
final class TypedDataEncoder {
    private TypedDataEncoder() {}

    // ═══════════════════════════════════════════════════════════════
    // TYPE ENCODING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Encodes a type definition to its canonical string form.
     * Example: "Mail(Person from,Person to,string contents)Person(string name,address wallet)"
     *
     * <p>This follows viem's implementation pattern:
     * <ol>
     *   <li>Collect ALL dependencies recursively</li>
     *   <li>Sort dependencies alphabetically (primary type first)</li>
     *   <li>Format each type string (NO recursion in formatting)</li>
     * </ol>
     */
    static String encodeType(
            String typeName,
            Map<String, List<TypedDataField>> types) {

        // Phase 1: Collect ALL dependencies recursively with cycle detection
        Set<String> allDeps = new LinkedHashSet<>();
        Set<String> visiting = new HashSet<>();
        collectDependencies(typeName, types, allDeps, visiting);

        // Phase 2: Sort - primary type first, then remaining alphabetically
        List<String> sortedTypes = new ArrayList<>();
        sortedTypes.add(typeName);
        allDeps.remove(typeName);
        allDeps.stream().sorted().forEach(sortedTypes::add);

        // Phase 3: Format each type (NO recursion - just string formatting)
        var result = new StringBuilder();
        for (String t : sortedTypes) {
            result.append(formatSingleType(t, types));
        }

        return result.toString();
    }

    /**
     * Formats a single type definition without recursion.
     * Output: "TypeName(type1 field1,type2 field2,...)"
     */
    private static String formatSingleType(
            String typeName,
            Map<String, List<TypedDataField>> types) {
        var fields = types.get(typeName);
        var sb = new StringBuilder();
        sb.append(typeName).append("(");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(",");
            var field = fields.get(i);
            sb.append(field.type()).append(" ").append(field.name());
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Recursively collects all struct type dependencies.
     *
     * @param typeName the type to collect dependencies for
     * @param types    all type definitions
     * @param deps     accumulator for dependencies found
     * @param visiting types currently being visited (for cycle detection)
     * @throws Eip712Exception if a cyclic dependency is detected
     */
    private static void collectDependencies(
            String typeName,
            Map<String, List<TypedDataField>> types,
            Set<String> deps,
            Set<String> visiting) {

        // Cycle detection
        if (visiting.contains(typeName)) {
            throw Eip712Exception.cyclicDependency(typeName);
        }

        var fields = types.get(typeName);
        if (fields == null) {
            return;  // Not a struct type (primitive)
        }

        // Add this type and mark as visiting
        deps.add(typeName);
        visiting.add(typeName);

        // Recurse into field types
        for (var field : fields) {
            String baseType = getBaseType(field.type());  // Strip [] suffix
            if (types.containsKey(baseType)) {
                collectDependencies(baseType, types, deps, visiting);
            }
        }

        // Done visiting this type
        visiting.remove(typeName);
    }

    /**
     * Extracts the base type from a potentially array type.
     * "uint256[]" -> "uint256", "Person[3]" -> "Person", "address" -> "address"
     */
    private static String getBaseType(String type) {
        int bracketIdx = type.indexOf('[');
        return bracketIdx >= 0 ? type.substring(0, bracketIdx) : type;
    }

    /**
     * Computes typeHash = keccak256(encodeType(typeName))
     */
    static byte[] typeHash(
            String typeName,
            Map<String, List<TypedDataField>> types) {
        var encoded = encodeType(typeName, types);
        return Keccak256.hash(encoded.getBytes(StandardCharsets.UTF_8));
    }

    // ═══════════════════════════════════════════════════════════════
    // DATA ENCODING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Encodes struct data according to EIP-712.
     * Returns concatenated 32-byte encoded field values.
     */
    static byte[] encodeData(
            String typeName,
            Map<String, List<TypedDataField>> types,
            Map<String, Object> data) {
        var fields = types.get(typeName);
        var encoded = new ByteArrayOutputStream();

        for (var field : fields) {
            var value = data.get(field.name());
            var fieldEncoded = encodeField(field.type(), value, types);
            encoded.writeBytes(fieldEncoded);
        }

        return encoded.toByteArray();
    }

    /**
     * Encodes a single field value based on its type.
     * Uses Eip712TypeParser to resolve the type string.
     */
    static byte[] encodeField(
            String type,
            Object value,
            Map<String, List<TypedDataField>> types) {
        Eip712Type parsedType = Eip712TypeParser.parse(type, types);
        return switch (parsedType) {
            case Eip712Type.Uint u -> encodeUint(value, u.bits());
            case Eip712Type.Int i -> encodeInt(value, i.bits());
            case Eip712Type.Address a -> encodeAddress(value);
            case Eip712Type.Bool b -> encodeBool(value);
            case Eip712Type.FixedBytes fb -> encodeFixedBytes(value, fb.length());
            case Eip712Type.DynamicBytes db -> encodeDynamicBytes(value);
            case Eip712Type.String s -> encodeString(value);
            case Eip712Type.Array arr -> encodeArray(arr, value, types);
            case Eip712Type.Struct struct -> hashStruct(struct.name(), types, asMap(value));
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // STRUCT HASHING
    // ═══════════════════════════════════════════════════════════════

    /**
     * hashStruct(s) = keccak256(typeHash || encodeData(s))
     */
    static byte[] hashStruct(
            String typeName,
            Map<String, List<TypedDataField>> types,
            Map<String, Object> data) {
        var typeHashBytes = typeHash(typeName, types);
        var encodedData = encodeData(typeName, types, data);
        return Keccak256.hash(typeHashBytes, encodedData);
    }

    // ═══════════════════════════════════════════════════════════════
    // DOMAIN HASHING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Computes domain separator from Eip712Domain.
     */
    static Hash hashDomain(Eip712Domain domain) {
        var types = buildDomainTypes(domain);
        var data = buildDomainData(domain);
        var hash = hashStruct("EIP712Domain", types, data);
        return Hash.fromBytes(hash);
    }

    private static Map<String, List<TypedDataField>> buildDomainTypes(Eip712Domain domain) {
        var fields = new ArrayList<TypedDataField>();
        if (domain.name() != null) fields.add(TypedDataField.of("name", "string"));
        if (domain.version() != null) fields.add(TypedDataField.of("version", "string"));
        if (domain.chainId() != null) fields.add(TypedDataField.of("chainId", "uint256"));
        if (domain.verifyingContract() != null) fields.add(TypedDataField.of("verifyingContract", "address"));
        if (domain.salt() != null) fields.add(TypedDataField.of("salt", "bytes32"));
        return Map.of("EIP712Domain", fields);
    }

    private static Map<String, Object> buildDomainData(Eip712Domain domain) {
        var data = new LinkedHashMap<String, Object>();
        if (domain.name() != null) data.put("name", domain.name());
        if (domain.version() != null) data.put("version", domain.version());
        if (domain.chainId() != null) data.put("chainId", BigInteger.valueOf(domain.chainId()));
        if (domain.verifyingContract() != null) data.put("verifyingContract", domain.verifyingContract());
        if (domain.salt() != null) data.put("salt", domain.salt());
        return data;
    }

    // ═══════════════════════════════════════════════════════════════
    // PRIMITIVE ENCODING
    // ═══════════════════════════════════════════════════════════════

    /**
     * uint encoding: left-pad to 32 bytes, big-endian
     */
    private static byte[] encodeUint(Object value, int bits) {
        BigInteger bi = toBigInteger(value);
        if (bi.signum() < 0) {
            throw Eip712Exception.invalidValue("uint" + bits, "cannot be negative: " + bi);
        }
        if (bi.bitLength() > bits) {
            throw Eip712Exception.valueOutOfRange("uint" + bits, bi, "exceeds " + bits + " bits");
        }
        return padLeft(toUnsignedByteArray(bi), 32);
    }

    /**
     * int encoding: two's complement, left-pad to 32 bytes
     */
    private static byte[] encodeInt(Object value, int bits) {
        BigInteger bi = toBigInteger(value);
        BigInteger min = BigInteger.ONE.shiftLeft(bits - 1).negate();
        BigInteger max = BigInteger.ONE.shiftLeft(bits - 1).subtract(BigInteger.ONE);
        if (bi.compareTo(min) < 0 || bi.compareTo(max) > 0) {
            throw Eip712Exception.valueOutOfRange("int" + bits, bi, "must be in range [" + min + ", " + max + "]");
        }
        // For negative numbers, use two's complement representation
        if (bi.signum() < 0) {
            // Two's complement: add 2^256 to negative number
            bi = bi.add(BigInteger.ONE.shiftLeft(256));
        }
        return padLeft(toUnsignedByteArray(bi), 32);
    }

    /**
     * address encoding: 20 bytes left-padded to 32
     */
    private static byte[] encodeAddress(Object value) {
        Address addr = toAddress(value);
        byte[] bytes = addr.toBytes(); // 20 bytes
        return padLeft(bytes, 32);
    }

    /**
     * bool encoding: 0 or 1 left-padded to 32 bytes
     */
    private static byte[] encodeBool(Object value) {
        boolean b = toBoolean(value);
        byte[] result = new byte[32];
        result[31] = (byte) (b ? 1 : 0);
        return result;
    }

    /**
     * fixed bytes encoding: right-pad to 32 bytes
     */
    private static byte[] encodeFixedBytes(Object value, int length) {
        byte[] bytes = toBytes(value);
        if (bytes.length != length) {
            throw Eip712Exception.invalidValue("bytes" + length, "expected " + length + " bytes, got " + bytes.length);
        }
        return padRight(bytes, 32);
    }

    /**
     * string encoding: keccak256(value)
     */
    private static byte[] encodeString(Object value) {
        String str = (String) value;
        return Keccak256.hash(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * dynamic bytes encoding: keccak256(value)
     */
    private static byte[] encodeDynamicBytes(Object value) {
        byte[] bytes = toBytes(value);
        return Keccak256.hash(bytes);
    }

    /**
     * array encoding: keccak256(concat(encoded elements))
     */
    private static byte[] encodeArray(Eip712Type.Array arr, Object value, Map<String, List<TypedDataField>> types) {
        List<?> list = (List<?>) value;
        var encoded = new ByteArrayOutputStream();
        String elementType = arr.elementType() instanceof Eip712Type.Struct s
            ? s.name()
            : Eip712TypeParser.toSolidityType(arr.elementType());
        for (var item : list) {
            encoded.writeBytes(encodeField(elementType, item, types));
        }
        return Keccak256.hash(encoded.toByteArray());
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Converts various numeric types to BigInteger.
     */
    private static BigInteger toBigInteger(Object value) {
        if (value instanceof BigInteger bi) {
            return bi;
        } else if (value instanceof Long l) {
            return BigInteger.valueOf(l);
        } else if (value instanceof Integer i) {
            return BigInteger.valueOf(i);
        } else if (value instanceof String s) {
            // Support hex strings
            if (s.startsWith("0x") || s.startsWith("0X")) {
                return new BigInteger(s.substring(2), 16);
            }
            return new BigInteger(s);
        } else if (value instanceof Number n) {
            return BigInteger.valueOf(n.longValue());
        }
        throw Eip712Exception.invalidValue("integer", value);
    }

    /**
     * Converts various address representations to Address.
     */
    private static Address toAddress(Object value) {
        if (value instanceof Address addr) {
            return addr;
        } else if (value instanceof String s) {
            return new Address(s);
        }
        throw Eip712Exception.invalidValue("address", value);
    }

    /**
     * Converts various boolean representations to boolean.
     */
    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        throw Eip712Exception.invalidValue("bool", value);
    }

    /**
     * Converts various byte array representations to byte[].
     */
    private static byte[] toBytes(Object value) {
        if (value instanceof byte[] bytes) {
            return bytes;
        } else if (value instanceof HexData hex) {
            return hex.toBytes();
        } else if (value instanceof Hash hash) {
            return hash.toBytes();
        } else if (value instanceof String s) {
            // Assume hex string
            return new HexData(s).toBytes();
        }
        throw Eip712Exception.invalidValue("bytes", value);
    }

    /**
     * Casts value to Map for struct encoding.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw Eip712Exception.invalidValue("struct", value);
    }

    /**
     * Left-pads bytes to the specified length.
     */
    private static byte[] padLeft(byte[] bytes, int length) {
        if (bytes.length >= length) {
            return bytes;
        }
        byte[] result = new byte[length];
        System.arraycopy(bytes, 0, result, length - bytes.length, bytes.length);
        return result;
    }

    /**
     * Right-pads bytes to the specified length.
     */
    private static byte[] padRight(byte[] bytes, int length) {
        if (bytes.length >= length) {
            return bytes;
        }
        byte[] result = new byte[length];
        System.arraycopy(bytes, 0, result, 0, bytes.length);
        return result;
    }

    /**
     * Converts BigInteger to unsigned byte array (no sign byte).
     */
    private static byte[] toUnsignedByteArray(BigInteger bi) {
        byte[] bytes = bi.toByteArray();
        // Remove leading zero if present (sign byte for positive numbers)
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }
}
