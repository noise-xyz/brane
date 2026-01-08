package io.brane.core.crypto.eip712;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import io.brane.core.error.Eip712Exception;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;

/**
 * Coerces JSON-parsed values to the appropriate Java types for EIP-712 encoding.
 * Called during encoding to ensure values match expected types.
 */
final class ValueCoercer {
    private ValueCoercer() {}

    static Object coerce(Object value, Eip712Type type) {
        if (value == null) {
            throw Eip712Exception.invalidValue(Eip712TypeParser.toSolidityType(type), null);
        }

        return switch (type) {
            case Eip712Type.Address a -> coerceAddress(value);
            case Eip712Type.Uint u -> coerceUint(value, u.bits());
            case Eip712Type.Int i -> coerceInt(value, i.bits());
            case Eip712Type.Bool b -> coerceBool(value);
            case Eip712Type.FixedBytes fb -> coerceFixedBytes(value, fb.length());
            case Eip712Type.DynamicBytes db -> coerceDynamicBytes(value);
            case Eip712Type.String s -> coerceString(value);
            case Eip712Type.Array arr -> coerceArray(value, arr);
            case Eip712Type.Struct struct -> coerceStruct(value);
        };
    }

    private static Address coerceAddress(Object value) {
        if (value instanceof Address addr) return addr;
        if (value instanceof String s) return new Address(s);
        throw Eip712Exception.invalidValue("address", value);
    }

    private static BigInteger coerceUint(Object value, int bits) {
        BigInteger bi = toBigInteger(value);
        if (bi.signum() < 0) {
            throw Eip712Exception.valueOutOfRange("uint" + bits, bi, "must be non-negative");
        }
        if (bi.bitLength() > bits) {
            throw Eip712Exception.valueOutOfRange("uint" + bits, bi, "exceeds bit width");
        }
        return bi;
    }

    private static BigInteger coerceInt(Object value, int bits) {
        BigInteger bi = toBigInteger(value);
        BigInteger max = BigInteger.ONE.shiftLeft(bits - 1).subtract(BigInteger.ONE);
        BigInteger min = max.negate().subtract(BigInteger.ONE);
        if (bi.compareTo(min) < 0 || bi.compareTo(max) > 0) {
            throw Eip712Exception.valueOutOfRange("int" + bits, bi, "out of signed range");
        }
        return bi;
    }

    private static BigInteger toBigInteger(Object value) {
        if (value instanceof BigInteger bi) return bi;
        if (value instanceof Number n) return BigInteger.valueOf(n.longValue());
        if (value instanceof String s) {
            // Handle both decimal and hex strings
            if (s.startsWith("0x") || s.startsWith("0X")) {
                return new BigInteger(s.substring(2), 16);
            }
            return new BigInteger(s);
        }
        throw Eip712Exception.invalidValue("integer", value);
    }

    private static Boolean coerceBool(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        throw Eip712Exception.invalidValue("bool", value);
    }

    private static HexData coerceFixedBytes(Object value, int length) {
        HexData data = toHexData(value);
        if (data.toBytes().length != length) {
            throw Eip712Exception.valueOutOfRange(
                "bytes" + length, value,
                "expected " + length + " bytes, got " + data.toBytes().length);
        }
        return data;
    }

    private static HexData coerceDynamicBytes(Object value) {
        return toHexData(value);
    }

    private static HexData toHexData(Object value) {
        if (value instanceof HexData hd) return hd;
        if (value instanceof byte[] bytes) return HexData.fromBytes(bytes);
        if (value instanceof String s) return new HexData(s);
        throw Eip712Exception.invalidValue("bytes", value);
    }

    private static String coerceString(Object value) {
        if (value instanceof String s) return s;
        throw Eip712Exception.invalidValue("string", value);
    }

    @SuppressWarnings("unchecked")
    private static List<?> coerceArray(Object value, Eip712Type.Array arr) {
        if (!(value instanceof List<?> list)) {
            throw Eip712Exception.invalidValue("array", value);
        }
        if (arr.fixedLength() != null && list.size() != arr.fixedLength()) {
            throw Eip712Exception.valueOutOfRange(
                "array[" + arr.fixedLength() + "]", value,
                "expected " + arr.fixedLength() + " elements, got " + list.size());
        }
        // Elements are coerced during encoding
        return list;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> coerceStruct(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw Eip712Exception.invalidValue("struct", value);
    }
}
