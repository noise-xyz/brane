package io.brane.core.crypto.eip712;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.brane.core.error.Eip712Exception;

class Eip712TypeParserTest {

    private static final Map<String, List<TypedDataField>> EMPTY_TYPES = Map.of();

    private static final Map<String, List<TypedDataField>> TYPES_WITH_PERSON = Map.of(
        "Person", List.of(
            TypedDataField.of("name", "string"),
            TypedDataField.of("wallet", "address")
        )
    );

    // ═══════════════════════════════════════════════════════════════
    // parse() - uint types
    // ═══════════════════════════════════════════════════════════════

    static IntStream allValidUintBitWidths() {
        return IntStream.iterate(8, b -> b <= 256, b -> b + 8);
    }

    @ParameterizedTest
    @MethodSource("allValidUintBitWidths")
    void parse_uint_allValidBitWidths(int bits) {
        var type = Eip712TypeParser.parse("uint" + bits, EMPTY_TYPES);
        assertInstanceOf(Eip712Type.Uint.class, type);
        assertEquals(bits, ((Eip712Type.Uint) type).bits());
    }

    @Test
    void parse_uint256_explicit() {
        var type = Eip712TypeParser.parse("uint256", EMPTY_TYPES);
        assertInstanceOf(Eip712Type.Uint.class, type);
        assertEquals(256, ((Eip712Type.Uint) type).bits());
    }

    // ═══════════════════════════════════════════════════════════════
    // parse() - int types
    // ═══════════════════════════════════════════════════════════════

    static IntStream allValidIntBitWidths() {
        return IntStream.iterate(8, b -> b <= 256, b -> b + 8);
    }

    @ParameterizedTest
    @MethodSource("allValidIntBitWidths")
    void parse_int_allValidBitWidths(int bits) {
        var type = Eip712TypeParser.parse("int" + bits, EMPTY_TYPES);
        assertInstanceOf(Eip712Type.Int.class, type);
        assertEquals(bits, ((Eip712Type.Int) type).bits());
    }

    @Test
    void parse_int8_explicit() {
        var type = Eip712TypeParser.parse("int8", EMPTY_TYPES);
        assertInstanceOf(Eip712Type.Int.class, type);
        assertEquals(8, ((Eip712Type.Int) type).bits());
    }

    // ═══════════════════════════════════════════════════════════════
    // parse() - address
    // ═══════════════════════════════════════════════════════════════

    @Test
    void parse_address() {
        var type = Eip712TypeParser.parse("address", EMPTY_TYPES);
        assertInstanceOf(Eip712Type.Address.class, type);
    }

    // ═══════════════════════════════════════════════════════════════
    // parse() - bool
    // ═══════════════════════════════════════════════════════════════

    @Test
    void parse_bool() {
        var type = Eip712TypeParser.parse("bool", EMPTY_TYPES);
        assertInstanceOf(Eip712Type.Bool.class, type);
    }

    // ═══════════════════════════════════════════════════════════════
    // parse() - bytes (dynamic)
    // ═══════════════════════════════════════════════════════════════

    @Test
    void parse_bytes_dynamic() {
        var type = Eip712TypeParser.parse("bytes", EMPTY_TYPES);
        assertInstanceOf(Eip712Type.DynamicBytes.class, type);
    }

    // ═══════════════════════════════════════════════════════════════
    // parse() - string
    // ═══════════════════════════════════════════════════════════════

    @Test
    void parse_string() {
        var type = Eip712TypeParser.parse("string", EMPTY_TYPES);
        assertInstanceOf(Eip712Type.String.class, type);
    }

    // ═══════════════════════════════════════════════════════════════
    // parse() - bytesN (fixed bytes)
    // ═══════════════════════════════════════════════════════════════

    static IntStream allValidBytesNLengths() {
        return IntStream.rangeClosed(1, 32);
    }

    @ParameterizedTest
    @MethodSource("allValidBytesNLengths")
    void parse_bytesN_allValidLengths(int length) {
        var type = Eip712TypeParser.parse("bytes" + length, EMPTY_TYPES);
        assertInstanceOf(Eip712Type.FixedBytes.class, type);
        assertEquals(length, ((Eip712Type.FixedBytes) type).length());
    }

    @Test
    void parse_bytes32_explicit() {
        var type = Eip712TypeParser.parse("bytes32", EMPTY_TYPES);
        assertInstanceOf(Eip712Type.FixedBytes.class, type);
        assertEquals(32, ((Eip712Type.FixedBytes) type).length());
    }

    @Test
    void parse_bytes1_explicit() {
        var type = Eip712TypeParser.parse("bytes1", EMPTY_TYPES);
        assertInstanceOf(Eip712Type.FixedBytes.class, type);
        assertEquals(1, ((Eip712Type.FixedBytes) type).length());
    }

    // ═══════════════════════════════════════════════════════════════
    // parse() - dynamic arrays
    // ═══════════════════════════════════════════════════════════════

    @Test
    void parse_dynamicArray_uint256() {
        var type = Eip712TypeParser.parse("uint256[]", EMPTY_TYPES);
        assertInstanceOf(Eip712Type.Array.class, type);
        var array = (Eip712Type.Array) type;
        assertNull(array.fixedLength());
        assertInstanceOf(Eip712Type.Uint.class, array.elementType());
        assertEquals(256, ((Eip712Type.Uint) array.elementType()).bits());
    }

    @Test
    void parse_dynamicArray_address() {
        var type = Eip712TypeParser.parse("address[]", EMPTY_TYPES);
        assertInstanceOf(Eip712Type.Array.class, type);
        var array = (Eip712Type.Array) type;
        assertNull(array.fixedLength());
        assertInstanceOf(Eip712Type.Address.class, array.elementType());
    }

    @Test
    void parse_dynamicArray_bytes32() {
        var type = Eip712TypeParser.parse("bytes32[]", EMPTY_TYPES);
        assertInstanceOf(Eip712Type.Array.class, type);
        var array = (Eip712Type.Array) type;
        assertNull(array.fixedLength());
        assertInstanceOf(Eip712Type.FixedBytes.class, array.elementType());
        assertEquals(32, ((Eip712Type.FixedBytes) array.elementType()).length());
    }

    // ═══════════════════════════════════════════════════════════════
    // parse() - fixed-size arrays
    // ═══════════════════════════════════════════════════════════════

    @Test
    void parse_fixedArray_uint256_10() {
        var type = Eip712TypeParser.parse("uint256[10]", EMPTY_TYPES);
        assertInstanceOf(Eip712Type.Array.class, type);
        var array = (Eip712Type.Array) type;
        assertEquals(10, array.fixedLength());
        assertInstanceOf(Eip712Type.Uint.class, array.elementType());
    }

    @Test
    void parse_fixedArray_bytes32_5() {
        var type = Eip712TypeParser.parse("bytes32[5]", EMPTY_TYPES);
        assertInstanceOf(Eip712Type.Array.class, type);
        var array = (Eip712Type.Array) type;
        assertEquals(5, array.fixedLength());
        assertInstanceOf(Eip712Type.FixedBytes.class, array.elementType());
    }

    @Test
    void parse_fixedArray_zeroSize_throws() {
        var ex = assertThrows(Eip712Exception.class,
            () -> Eip712TypeParser.parse("uint256[0]", EMPTY_TYPES));
        assertTrue(ex.getMessage().contains("array size must be positive"));
    }

    // ═══════════════════════════════════════════════════════════════
    // parse() - nested arrays
    // ═══════════════════════════════════════════════════════════════

    @Test
    void parse_nestedArray_dynamicOfDynamic() {
        var type = Eip712TypeParser.parse("uint256[][]", EMPTY_TYPES);
        assertInstanceOf(Eip712Type.Array.class, type);
        var outer = (Eip712Type.Array) type;
        assertNull(outer.fixedLength());
        assertInstanceOf(Eip712Type.Array.class, outer.elementType());
        var inner = (Eip712Type.Array) outer.elementType();
        assertNull(inner.fixedLength());
        assertInstanceOf(Eip712Type.Uint.class, inner.elementType());
    }

    @Test
    void parse_nestedArray_fixedOfDynamic() {
        var type = Eip712TypeParser.parse("address[][3]", EMPTY_TYPES);
        assertInstanceOf(Eip712Type.Array.class, type);
        var outer = (Eip712Type.Array) type;
        assertEquals(3, outer.fixedLength());
        assertInstanceOf(Eip712Type.Array.class, outer.elementType());
        var inner = (Eip712Type.Array) outer.elementType();
        assertNull(inner.fixedLength());
    }

    // ═══════════════════════════════════════════════════════════════
    // parse() - struct types
    // ═══════════════════════════════════════════════════════════════

    @Test
    void parse_struct_knownType() {
        var type = Eip712TypeParser.parse("Person", TYPES_WITH_PERSON);
        assertInstanceOf(Eip712Type.Struct.class, type);
        assertEquals("Person", ((Eip712Type.Struct) type).name());
    }

    @Test
    void parse_struct_unknownType_throws() {
        var ex = assertThrows(Eip712Exception.class,
            () -> Eip712TypeParser.parse("UnknownType", EMPTY_TYPES));
        assertTrue(ex.getMessage().contains("Unknown EIP-712 type"));
    }

    @Test
    void parse_struct_array() {
        var type = Eip712TypeParser.parse("Person[]", TYPES_WITH_PERSON);
        assertInstanceOf(Eip712Type.Array.class, type);
        var array = (Eip712Type.Array) type;
        assertNull(array.fixedLength());
        assertInstanceOf(Eip712Type.Struct.class, array.elementType());
        assertEquals("Person", ((Eip712Type.Struct) array.elementType()).name());
    }

    @Test
    void parse_struct_fixedArray() {
        var type = Eip712TypeParser.parse("Person[2]", TYPES_WITH_PERSON);
        assertInstanceOf(Eip712Type.Array.class, type);
        var array = (Eip712Type.Array) type;
        assertEquals(2, array.fixedLength());
        assertInstanceOf(Eip712Type.Struct.class, array.elementType());
    }

    // ═══════════════════════════════════════════════════════════════
    // parse() - null handling
    // ═══════════════════════════════════════════════════════════════

    @Test
    void parse_nullType_throws() {
        assertThrows(NullPointerException.class,
            () -> Eip712TypeParser.parse(null, EMPTY_TYPES));
    }

    @Test
    void parse_nullTypes_throws() {
        assertThrows(NullPointerException.class,
            () -> Eip712TypeParser.parse("uint256", null));
    }

    // ═══════════════════════════════════════════════════════════════
    // toSolidityType() - all types
    // ═══════════════════════════════════════════════════════════════

    @Test
    void toSolidityType_uint256() {
        assertEquals("uint256", Eip712TypeParser.toSolidityType(new Eip712Type.Uint(256)));
    }

    @Test
    void toSolidityType_uint8() {
        assertEquals("uint8", Eip712TypeParser.toSolidityType(new Eip712Type.Uint(8)));
    }

    @Test
    void toSolidityType_int256() {
        assertEquals("int256", Eip712TypeParser.toSolidityType(new Eip712Type.Int(256)));
    }

    @Test
    void toSolidityType_int8() {
        assertEquals("int8", Eip712TypeParser.toSolidityType(new Eip712Type.Int(8)));
    }

    @Test
    void toSolidityType_address() {
        assertEquals("address", Eip712TypeParser.toSolidityType(new Eip712Type.Address()));
    }

    @Test
    void toSolidityType_bool() {
        assertEquals("bool", Eip712TypeParser.toSolidityType(new Eip712Type.Bool()));
    }

    @Test
    void toSolidityType_bytes() {
        assertEquals("bytes", Eip712TypeParser.toSolidityType(new Eip712Type.DynamicBytes()));
    }

    @Test
    void toSolidityType_string() {
        assertEquals("string", Eip712TypeParser.toSolidityType(new Eip712Type.String()));
    }

    @Test
    void toSolidityType_bytes32() {
        assertEquals("bytes32", Eip712TypeParser.toSolidityType(new Eip712Type.FixedBytes(32)));
    }

    @Test
    void toSolidityType_bytes1() {
        assertEquals("bytes1", Eip712TypeParser.toSolidityType(new Eip712Type.FixedBytes(1)));
    }

    @Test
    void toSolidityType_dynamicArray() {
        var array = new Eip712Type.Array(new Eip712Type.Uint(256), null);
        assertEquals("uint256[]", Eip712TypeParser.toSolidityType(array));
    }

    @Test
    void toSolidityType_fixedArray() {
        var array = new Eip712Type.Array(new Eip712Type.Address(), 10);
        assertEquals("address[10]", Eip712TypeParser.toSolidityType(array));
    }

    @Test
    void toSolidityType_nestedArray() {
        var inner = new Eip712Type.Array(new Eip712Type.Uint(8), 5);
        var outer = new Eip712Type.Array(inner, null);
        assertEquals("uint8[5][]", Eip712TypeParser.toSolidityType(outer));
    }

    @Test
    void toSolidityType_struct() {
        assertEquals("Person", Eip712TypeParser.toSolidityType(new Eip712Type.Struct("Person")));
    }

    @Test
    void toSolidityType_structArray() {
        var array = new Eip712Type.Array(new Eip712Type.Struct("Mail"), null);
        assertEquals("Mail[]", Eip712TypeParser.toSolidityType(array));
    }

    // ═══════════════════════════════════════════════════════════════
    // Round-trip: parse() -> toSolidityType() -> parse()
    // ═══════════════════════════════════════════════════════════════

    static Stream<Arguments> roundTripTypes() {
        return Stream.of(
            Arguments.of("uint256"),
            Arguments.of("uint8"),
            Arguments.of("uint128"),
            Arguments.of("int256"),
            Arguments.of("int8"),
            Arguments.of("int64"),
            Arguments.of("address"),
            Arguments.of("bool"),
            Arguments.of("bytes"),
            Arguments.of("string"),
            Arguments.of("bytes1"),
            Arguments.of("bytes16"),
            Arguments.of("bytes32"),
            Arguments.of("uint256[]"),
            Arguments.of("address[]"),
            Arguments.of("bytes32[]"),
            Arguments.of("uint256[10]"),
            Arguments.of("address[5]"),
            Arguments.of("uint8[][]"),
            Arguments.of("int256[3][]")
        );
    }

    @ParameterizedTest
    @MethodSource("roundTripTypes")
    void roundTrip_parseAndToSolidityType(String typeStr) {
        var parsed = Eip712TypeParser.parse(typeStr, EMPTY_TYPES);
        var backToString = Eip712TypeParser.toSolidityType(parsed);
        assertEquals(typeStr, backToString);

        // Parse again to verify consistency
        var parsedAgain = Eip712TypeParser.parse(backToString, EMPTY_TYPES);
        assertEquals(parsed, parsedAgain);
    }

    @Test
    void roundTrip_struct() {
        var parsed = Eip712TypeParser.parse("Person", TYPES_WITH_PERSON);
        var backToString = Eip712TypeParser.toSolidityType(parsed);
        assertEquals("Person", backToString);

        var parsedAgain = Eip712TypeParser.parse(backToString, TYPES_WITH_PERSON);
        assertEquals(parsed, parsedAgain);
    }

    @Test
    void roundTrip_structArray() {
        var parsed = Eip712TypeParser.parse("Person[]", TYPES_WITH_PERSON);
        var backToString = Eip712TypeParser.toSolidityType(parsed);
        assertEquals("Person[]", backToString);

        var parsedAgain = Eip712TypeParser.parse(backToString, TYPES_WITH_PERSON);
        assertEquals(parsed, parsedAgain);
    }

    @Test
    void roundTrip_structFixedArray() {
        var parsed = Eip712TypeParser.parse("Person[3]", TYPES_WITH_PERSON);
        var backToString = Eip712TypeParser.toSolidityType(parsed);
        assertEquals("Person[3]", backToString);

        var parsedAgain = Eip712TypeParser.parse(backToString, TYPES_WITH_PERSON);
        assertEquals(parsed, parsedAgain);
    }
}
