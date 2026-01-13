// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.crypto.eip712;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import sh.brane.core.crypto.Keccak256;
import sh.brane.core.error.Eip712Exception;
import sh.brane.core.types.Address;
import sh.brane.core.types.Hash;
import sh.brane.core.types.HexData;

/**
 * Tests for TypedDataEncoder - the internal EIP-712 encoding implementation.
 *
 * <p>Test vectors are derived from the EIP-712 specification:
 * <a href="https://eips.ethereum.org/EIPS/eip-712">EIP-712</a>
 */
class TypedDataEncoderTest {

    // ═══════════════════════════════════════════════════════════════
    // Test fixtures from EIP-712 specification
    // ═══════════════════════════════════════════════════════════════

    /**
     * Mail example from EIP-712 spec.
     * Mail(Person from,Person to,string contents)
     * Person(string name,address wallet)
     */
    private static final Map<String, List<TypedDataField>> MAIL_TYPES = new LinkedHashMap<>();
    static {
        MAIL_TYPES.put("Mail", List.of(
            TypedDataField.of("from", "Person"),
            TypedDataField.of("to", "Person"),
            TypedDataField.of("contents", "string")
        ));
        MAIL_TYPES.put("Person", List.of(
            TypedDataField.of("name", "string"),
            TypedDataField.of("wallet", "address")
        ));
    }

    /**
     * Simple type with no dependencies.
     */
    private static final Map<String, List<TypedDataField>> SIMPLE_TYPE = Map.of(
        "SimpleStruct", List.of(
            TypedDataField.of("value", "uint256"),
            TypedDataField.of("flag", "bool")
        )
    );

    /**
     * Types with array fields.
     */
    private static final Map<String, List<TypedDataField>> ARRAY_TYPES = new LinkedHashMap<>();
    static {
        ARRAY_TYPES.put("BatchTransfer", List.of(
            TypedDataField.of("recipients", "address[]"),
            TypedDataField.of("amounts", "uint256[]")
        ));
    }

    /**
     * Complex nested types for dependency sorting tests.
     * Types should be sorted: primary type first, then dependencies alphabetically.
     */
    private static final Map<String, List<TypedDataField>> NESTED_TYPES = new LinkedHashMap<>();
    static {
        NESTED_TYPES.put("Root", List.of(
            TypedDataField.of("child", "Child"),
            TypedDataField.of("another", "Another")
        ));
        NESTED_TYPES.put("Child", List.of(
            TypedDataField.of("grandchild", "Grandchild")
        ));
        NESTED_TYPES.put("Another", List.of(
            TypedDataField.of("value", "uint256")
        ));
        NESTED_TYPES.put("Grandchild", List.of(
            TypedDataField.of("value", "string")
        ));
    }

    // ═══════════════════════════════════════════════════════════════
    // encodeType() - output format tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void encodeType_simpleType_noParenthesisInDeps() {
        // Simple type with no struct dependencies
        String encoded = TypedDataEncoder.encodeType("SimpleStruct", SIMPLE_TYPE);
        assertEquals("SimpleStruct(uint256 value,bool flag)", encoded);
    }

    @Test
    void encodeType_mailExample_eip712Spec() {
        // From EIP-712: "Mail(Person from,Person to,string contents)Person(string name,address wallet)"
        // Primary type first, then dependencies alphabetically sorted
        String encoded = TypedDataEncoder.encodeType("Mail", MAIL_TYPES);
        assertEquals("Mail(Person from,Person to,string contents)Person(string name,address wallet)", encoded);
    }

    @Test
    void encodeType_personType_standalone() {
        // Person type when used as primary type
        String encoded = TypedDataEncoder.encodeType("Person", MAIL_TYPES);
        assertEquals("Person(string name,address wallet)", encoded);
    }

    @Test
    void encodeType_arrayFields_notIncludedInDeps() {
        // Array types like address[] and uint256[] are not struct types
        String encoded = TypedDataEncoder.encodeType("BatchTransfer", ARRAY_TYPES);
        assertEquals("BatchTransfer(address[] recipients,uint256[] amounts)", encoded);
    }

    // ═══════════════════════════════════════════════════════════════
    // encodeType() - dependency sorting tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void encodeType_dependenciesSortedAlphabetically() {
        // Root depends on Child and Another
        // Child depends on Grandchild
        // Expected order: Root, Another, Child, Grandchild (alphabetical after primary)
        String encoded = TypedDataEncoder.encodeType("Root", NESTED_TYPES);

        // Verify primary type is first
        assertTrue(encoded.startsWith("Root("));

        // Verify alphabetical order of dependencies
        int anotherIdx = encoded.indexOf("Another(");
        int childIdx = encoded.indexOf("Child(");
        int grandchildIdx = encoded.indexOf("Grandchild(");

        assertTrue(anotherIdx > 0, "Another should be present");
        assertTrue(childIdx > 0, "Child should be present");
        assertTrue(grandchildIdx > 0, "Grandchild should be present");
        assertTrue(anotherIdx < childIdx, "Another should come before Child (alphabetical)");
        assertTrue(childIdx < grandchildIdx, "Child should come before Grandchild (alphabetical)");
    }

    @Test
    void encodeType_structArrayDependency_included() {
        // Types with struct array fields should include the struct type in dependencies
        var types = new LinkedHashMap<String, List<TypedDataField>>();
        types.put("Order", List.of(
            TypedDataField.of("items", "Item[]"),
            TypedDataField.of("total", "uint256")
        ));
        types.put("Item", List.of(
            TypedDataField.of("name", "string"),
            TypedDataField.of("price", "uint256")
        ));

        String encoded = TypedDataEncoder.encodeType("Order", types);

        // Should include Item as dependency
        assertTrue(encoded.contains("Item(string name,uint256 price)"));
        assertTrue(encoded.startsWith("Order("));
    }

    @Test
    void encodeType_deepNesting_allDependenciesIncluded() {
        // Test A -> B -> C -> D chain
        var types = new LinkedHashMap<String, List<TypedDataField>>();
        types.put("A", List.of(TypedDataField.of("b", "B")));
        types.put("B", List.of(TypedDataField.of("c", "C")));
        types.put("C", List.of(TypedDataField.of("d", "D")));
        types.put("D", List.of(TypedDataField.of("value", "uint256")));

        String encoded = TypedDataEncoder.encodeType("A", types);

        // All types should be present, alphabetically sorted after A
        assertTrue(encoded.startsWith("A(B b)"));
        assertTrue(encoded.contains("B(C c)"));
        assertTrue(encoded.contains("C(D d)"));
        assertTrue(encoded.contains("D(uint256 value)"));
    }

    // ═══════════════════════════════════════════════════════════════
    // encodeType() - cycle detection tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void encodeType_selfReferential_throwsCyclicDependency() {
        // Type that references itself directly
        var types = Map.of(
            "Node", List.of(
                TypedDataField.of("next", "Node"),
                TypedDataField.of("value", "uint256")
            )
        );

        Eip712Exception ex = assertThrows(Eip712Exception.class,
            () -> TypedDataEncoder.encodeType("Node", types));
        assertTrue(ex.getMessage().contains("Cyclic"));
    }

    @Test
    void encodeType_mutuallyRecursive_throwsCyclicDependency() {
        // A -> B -> A cycle
        var types = new LinkedHashMap<String, List<TypedDataField>>();
        types.put("TypeA", List.of(TypedDataField.of("b", "TypeB")));
        types.put("TypeB", List.of(TypedDataField.of("a", "TypeA")));

        Eip712Exception ex = assertThrows(Eip712Exception.class,
            () -> TypedDataEncoder.encodeType("TypeA", types));
        assertTrue(ex.getMessage().contains("Cyclic"));
    }

    @Test
    void encodeType_longCycle_throwsCyclicDependency() {
        // A -> B -> C -> A cycle
        var types = new LinkedHashMap<String, List<TypedDataField>>();
        types.put("TypeA", List.of(TypedDataField.of("b", "TypeB")));
        types.put("TypeB", List.of(TypedDataField.of("c", "TypeC")));
        types.put("TypeC", List.of(TypedDataField.of("a", "TypeA")));

        Eip712Exception ex = assertThrows(Eip712Exception.class,
            () -> TypedDataEncoder.encodeType("TypeA", types));
        assertTrue(ex.getMessage().contains("Cyclic"));
    }

    // ═══════════════════════════════════════════════════════════════
    // typeHash() tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void typeHash_isKeccak256OfEncodeType() {
        // typeHash = keccak256(encodeType(typeName))
        String encoded = TypedDataEncoder.encodeType("Mail", MAIL_TYPES);
        byte[] expectedHash = Keccak256.hash(encoded.getBytes(StandardCharsets.UTF_8));

        byte[] actualHash = TypedDataEncoder.typeHash("Mail", MAIL_TYPES);

        assertArrayEquals(expectedHash, actualHash);
    }

    @Test
    void typeHash_mailExample_eip712SpecVector() {
        // From EIP-712 spec:
        // typeHash("Mail") = keccak256("Mail(Person from,Person to,string contents)Person(string name,address wallet)")
        // = 0xa0cedeb2dc280ba39b857546d74f5549c3a1d7bdc2dd96bf881f76108e23dac2
        byte[] typeHash = TypedDataEncoder.typeHash("Mail", MAIL_TYPES);
        String hexHash = HexData.fromBytes(typeHash).value();

        assertEquals("0xa0cedeb2dc280ba39b857546d74f5549c3a1d7bdc2dd96bf881f76108e23dac2", hexHash);
    }

    @Test
    void typeHash_personType_eip712SpecVector() {
        // typeHash("Person") = keccak256("Person(string name,address wallet)")
        // = 0xb9d8c78acf9b987311de6c7b45bb6a9c8e1bf361fa7fd3467a2163f994c79500
        byte[] typeHash = TypedDataEncoder.typeHash("Person", MAIL_TYPES);
        String hexHash = HexData.fromBytes(typeHash).value();

        assertEquals("0xb9d8c78acf9b987311de6c7b45bb6a9c8e1bf361fa7fd3467a2163f994c79500", hexHash);
    }

    @Test
    void typeHash_eip712Domain_specVector() {
        // EIP712Domain typeHash for full domain:
        // keccak256("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)")
        // = 0x8b73c3c69bb8fe3d512ecc4cf759cc79239f7b179b0ffacaa9a75d522b39400f
        var domainTypes = Map.of(
            "EIP712Domain", List.of(
                TypedDataField.of("name", "string"),
                TypedDataField.of("version", "string"),
                TypedDataField.of("chainId", "uint256"),
                TypedDataField.of("verifyingContract", "address")
            )
        );

        byte[] typeHash = TypedDataEncoder.typeHash("EIP712Domain", domainTypes);
        String hexHash = HexData.fromBytes(typeHash).value();

        assertEquals("0x8b73c3c69bb8fe3d512ecc4cf759cc79239f7b179b0ffacaa9a75d522b39400f", hexHash);
    }

    @Test
    void typeHash_simpleType_32Bytes() {
        byte[] typeHash = TypedDataEncoder.typeHash("SimpleStruct", SIMPLE_TYPE);
        assertEquals(32, typeHash.length);
    }

    // ═══════════════════════════════════════════════════════════════
    // hashStruct() tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void hashStruct_personExample_eip712SpecVector() {
        // From EIP-712 spec:
        // Person { name: "Cow", wallet: 0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826 }
        // hashStruct(person) = 0xfc71e5fa27ff56c350aa531bc129ebdf613b772b6604664f5d8dbe21b85eb0c8
        var data = new LinkedHashMap<String, Object>();
        data.put("name", "Cow");
        data.put("wallet", new Address("0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"));

        byte[] hash = TypedDataEncoder.hashStruct("Person", MAIL_TYPES, data);
        String hexHash = HexData.fromBytes(hash).value();

        assertEquals("0xfc71e5fa27ff56c350aa531bc129ebdf613b772b6604664f5d8dbe21b85eb0c8", hexHash);
    }

    @Test
    void hashStruct_mailExample_eip712SpecVector() {
        // From EIP-712 spec:
        // Mail {
        //   from: Person { name: "Cow", wallet: 0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826 },
        //   to: Person { name: "Bob", wallet: 0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB },
        //   contents: "Hello, Bob!"
        // }
        // hashStruct(mail) = 0xc52c0ee5d84264471806290a3f2c4cecfc5490626bf912d01f240d7a274b371e
        var from = new LinkedHashMap<String, Object>();
        from.put("name", "Cow");
        from.put("wallet", new Address("0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"));

        var to = new LinkedHashMap<String, Object>();
        to.put("name", "Bob");
        to.put("wallet", new Address("0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB"));

        var mail = new LinkedHashMap<String, Object>();
        mail.put("from", from);
        mail.put("to", to);
        mail.put("contents", "Hello, Bob!");

        byte[] hash = TypedDataEncoder.hashStruct("Mail", MAIL_TYPES, mail);
        String hexHash = HexData.fromBytes(hash).value();

        assertEquals("0xc52c0ee5d84264471806290a3f2c4cecfc5490626bf912d01f240d7a274b371e", hexHash);
    }

    @Test
    void hashStruct_32ByteOutput() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("value", BigInteger.valueOf(42));
        data.put("flag", true);
        byte[] hash = TypedDataEncoder.hashStruct("SimpleStruct", SIMPLE_TYPE, data);
        assertEquals(32, hash.length);
    }

    @Test
    void hashStruct_differentData_differentHash() {
        Map<String, Object> data1 = new LinkedHashMap<>();
        data1.put("value", BigInteger.valueOf(100));
        data1.put("flag", true);
        Map<String, Object> data2 = new LinkedHashMap<>();
        data2.put("value", BigInteger.valueOf(200));
        data2.put("flag", true);

        byte[] hash1 = TypedDataEncoder.hashStruct("SimpleStruct", SIMPLE_TYPE, data1);
        byte[] hash2 = TypedDataEncoder.hashStruct("SimpleStruct", SIMPLE_TYPE, data2);

        assertFalse(java.util.Arrays.equals(hash1, hash2));
    }

    @Test
    void hashStruct_sameData_sameHash() {
        Map<String, Object> data1 = new LinkedHashMap<>();
        data1.put("value", BigInteger.valueOf(100));
        data1.put("flag", true);
        Map<String, Object> data2 = new LinkedHashMap<>();
        data2.put("value", BigInteger.valueOf(100));
        data2.put("flag", true);

        byte[] hash1 = TypedDataEncoder.hashStruct("SimpleStruct", SIMPLE_TYPE, data1);
        byte[] hash2 = TypedDataEncoder.hashStruct("SimpleStruct", SIMPLE_TYPE, data2);

        assertArrayEquals(hash1, hash2);
    }

    // ═══════════════════════════════════════════════════════════════
    // hashDomain() tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void hashDomain_fullDomain_eip712SpecVector() {
        // From EIP-712 spec example:
        // domain = { name: "Ether Mail", version: "1", chainId: 1, verifyingContract: 0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC }
        // domainSeparator = 0xf2cee375fa42b42143804025fc449deafd50cc031ca257e0b194a650a912090f
        var domain = Eip712Domain.builder()
            .name("Ether Mail")
            .version("1")
            .chainId(1)
            .verifyingContract(new Address("0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"))
            .build();

        Hash domainSeparator = TypedDataEncoder.hashDomain(domain);

        assertEquals("0xf2cee375fa42b42143804025fc449deafd50cc031ca257e0b194a650a912090f",
            domainSeparator.value());
    }

    @Test
    void hashDomain_minimalDomain_consistentHash() {
        var domain = Eip712Domain.builder()
            .name("Test")
            .version("1")
            .build();

        Hash hash1 = TypedDataEncoder.hashDomain(domain);
        Hash hash2 = TypedDataEncoder.hashDomain(domain);

        assertEquals(hash1, hash2);
    }

    @Test
    void hashDomain_differentNames_differentHash() {
        var domain1 = Eip712Domain.builder().name("App1").version("1").build();
        var domain2 = Eip712Domain.builder().name("App2").version("1").build();

        assertNotEquals(
            TypedDataEncoder.hashDomain(domain1),
            TypedDataEncoder.hashDomain(domain2)
        );
    }

    @Test
    void hashDomain_differentChainIds_differentHash() {
        var domain1 = Eip712Domain.builder().name("Test").version("1").chainId(1).build();
        var domain2 = Eip712Domain.builder().name("Test").version("1").chainId(137).build();

        assertNotEquals(
            TypedDataEncoder.hashDomain(domain1),
            TypedDataEncoder.hashDomain(domain2)
        );
    }

    @Test
    void hashDomain_withSalt_includedInHash() {
        var domainNoSalt = Eip712Domain.builder()
            .name("Test")
            .version("1")
            .build();

        var domainWithSalt = Eip712Domain.builder()
            .name("Test")
            .version("1")
            .salt(new Hash("0x0000000000000000000000000000000000000000000000000000000000000001"))
            .build();

        assertNotEquals(
            TypedDataEncoder.hashDomain(domainNoSalt),
            TypedDataEncoder.hashDomain(domainWithSalt)
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // encodeField() tests for primitive types
    // ═══════════════════════════════════════════════════════════════

    @Test
    void encodeField_uint256_leftPaddedTo32Bytes() {
        byte[] encoded = TypedDataEncoder.encodeField("uint256", BigInteger.valueOf(256), Map.of());
        assertEquals(32, encoded.length);
        // 256 = 0x100, should be at the end
        assertEquals(1, encoded[30]);
        assertEquals(0, encoded[31]);
    }

    @Test
    void encodeField_address_leftPaddedTo32Bytes() {
        byte[] encoded = TypedDataEncoder.encodeField("address",
            new Address("0x1234567890123456789012345678901234567890"), Map.of());
        assertEquals(32, encoded.length);
        // First 12 bytes should be zeros (padding)
        for (int i = 0; i < 12; i++) {
            assertEquals(0, encoded[i], "Byte " + i + " should be 0");
        }
    }

    @Test
    void encodeField_bool_true() {
        byte[] encoded = TypedDataEncoder.encodeField("bool", true, Map.of());
        assertEquals(32, encoded.length);
        assertEquals(1, encoded[31]);
        for (int i = 0; i < 31; i++) {
            assertEquals(0, encoded[i]);
        }
    }

    @Test
    void encodeField_bool_false() {
        byte[] encoded = TypedDataEncoder.encodeField("bool", false, Map.of());
        assertEquals(32, encoded.length);
        assertEquals(0, encoded[31]);
    }

    @Test
    void encodeField_bytes32_rightPaddedTo32Bytes() {
        byte[] input = new byte[32];
        input[0] = (byte) 0xAB;
        byte[] encoded = TypedDataEncoder.encodeField("bytes32", input, Map.of());
        assertEquals(32, encoded.length);
        assertEquals((byte) 0xAB, encoded[0]);
    }

    @Test
    void encodeField_string_keccak256Hashed() {
        // String encoding should return keccak256(string)
        byte[] encoded = TypedDataEncoder.encodeField("string", "Hello, Bob!", Map.of());
        byte[] expected = Keccak256.hash("Hello, Bob!".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(expected, encoded);
    }

    @Test
    void encodeField_bytes_keccak256Hashed() {
        byte[] data = new byte[]{1, 2, 3, 4};
        byte[] encoded = TypedDataEncoder.encodeField("bytes", data, Map.of());
        byte[] expected = Keccak256.hash(data);
        assertArrayEquals(expected, encoded);
    }

    @Test
    void encodeField_int256_negative_twosComplement() {
        // -1 in two's complement is all 1s
        byte[] encoded = TypedDataEncoder.encodeField("int256", BigInteger.valueOf(-1), Map.of());
        assertEquals(32, encoded.length);
        for (byte b : encoded) {
            assertEquals((byte) 0xFF, b);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // encodeField() - array encoding tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void encodeField_array_keccak256OfEncodedElements() {
        // Array encoding: keccak256(concat(encoded elements))
        List<BigInteger> values = List.of(BigInteger.ONE, BigInteger.TWO);
        byte[] encoded = TypedDataEncoder.encodeField("uint256[]", values, Map.of());
        assertEquals(32, encoded.length);
    }

    @Test
    void encodeField_emptyArray_hashedEmpty() {
        List<BigInteger> values = List.of();
        byte[] encoded = TypedDataEncoder.encodeField("uint256[]", values, Map.of());
        // Empty array hash = keccak256("")
        byte[] expected = Keccak256.hash(new byte[0]);
        assertArrayEquals(expected, encoded);
    }

    // ═══════════════════════════════════════════════════════════════
    // encodeData() tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void encodeData_concatenatesEncodedFields() {
        var data = new LinkedHashMap<String, Object>();
        data.put("value", BigInteger.valueOf(42));
        data.put("flag", true);

        byte[] encoded = TypedDataEncoder.encodeData("SimpleStruct", SIMPLE_TYPE, data);

        // Should be 64 bytes: 32 for uint256 + 32 for bool
        assertEquals(64, encoded.length);
    }

    @Test
    void encodeData_fieldOrder_matchesTypeDefinition() {
        // Even if data map has different order, encoding should follow type definition order
        var data = new LinkedHashMap<String, Object>();
        data.put("flag", true);  // Put flag first
        data.put("value", BigInteger.ZERO);  // Then value

        byte[] encoded = TypedDataEncoder.encodeData("SimpleStruct", SIMPLE_TYPE, data);

        // Value (uint256) should be encoded first based on type definition order
        // Value=0 means first 32 bytes should be all zeros
        for (int i = 0; i < 32; i++) {
            assertEquals(0, encoded[i], "Value encoding at byte " + i);
        }
        // Flag=true means last byte of second 32-byte chunk should be 1
        assertEquals(1, encoded[63]);
    }
}
