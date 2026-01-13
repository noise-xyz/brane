// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.crypto.eip712;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TypeDefinitionTest {

    // Test record for ERC-2612 Permit
    record Permit(String owner, String spender, BigInteger value, BigInteger nonce, BigInteger deadline) {}

    // Test record for simple mail example
    record Mail(String from, String to, String contents) {}

    private static final Map<String, List<TypedDataField>> PERMIT_TYPES = Map.of(
        "Permit", List.of(
            TypedDataField.of("owner", "address"),
            TypedDataField.of("spender", "address"),
            TypedDataField.of("value", "uint256"),
            TypedDataField.of("nonce", "uint256"),
            TypedDataField.of("deadline", "uint256")
        )
    );

    private static final Map<String, List<TypedDataField>> MAIL_TYPES = Map.of(
        "Mail", List.of(
            TypedDataField.of("from", "address"),
            TypedDataField.of("to", "address"),
            TypedDataField.of("contents", "string")
        )
    );

    @Test
    void forRecord_createsDefinition() {
        var definition = TypeDefinition.forRecord(Permit.class, "Permit", PERMIT_TYPES);

        assertEquals("Permit", definition.primaryType());
        assertEquals(PERMIT_TYPES, definition.types());
        assertNotNull(definition.extractor());
    }

    @Test
    void forRecord_extractorExtractsFields() {
        var definition = TypeDefinition.forRecord(Permit.class, "Permit", PERMIT_TYPES);
        var permit = new Permit(
            "0x1234567890123456789012345678901234567890",
            "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd",
            BigInteger.valueOf(1000),
            BigInteger.ZERO,
            BigInteger.valueOf(1234567890)
        );

        var extracted = definition.extractor().apply(permit);

        assertEquals("0x1234567890123456789012345678901234567890", extracted.get("owner"));
        assertEquals("0xabcdefabcdefabcdefabcdefabcdefabcdefabcd", extracted.get("spender"));
        assertEquals(BigInteger.valueOf(1000), extracted.get("value"));
        assertEquals(BigInteger.ZERO, extracted.get("nonce"));
        assertEquals(BigInteger.valueOf(1234567890), extracted.get("deadline"));
        assertEquals(5, extracted.size());
    }

    @Test
    void forRecord_mailExample() {
        var definition = TypeDefinition.forRecord(Mail.class, "Mail", MAIL_TYPES);
        var mail = new Mail("alice@example.com", "bob@example.com", "Hello, World!");

        var extracted = definition.extractor().apply(mail);

        assertEquals("alice@example.com", extracted.get("from"));
        assertEquals("bob@example.com", extracted.get("to"));
        assertEquals("Hello, World!", extracted.get("contents"));
    }

    @Test
    void constructor_nullPrimaryType_throws() {
        assertThrows(NullPointerException.class, () ->
            new TypeDefinition<>(null, PERMIT_TYPES, p -> Map.of()));
    }

    @Test
    void constructor_nullTypes_throws() {
        assertThrows(NullPointerException.class, () ->
            new TypeDefinition<>("Permit", null, p -> Map.of()));
    }

    @Test
    void constructor_nullExtractor_throws() {
        assertThrows(NullPointerException.class, () ->
            new TypeDefinition<>("Permit", PERMIT_TYPES, null));
    }

    @Test
    void constructor_blankPrimaryType_throws() {
        var ex = assertThrows(IllegalArgumentException.class, () ->
            new TypeDefinition<>("", PERMIT_TYPES, p -> Map.of()));
        assertEquals("primaryType cannot be blank", ex.getMessage());
    }

    @Test
    void constructor_whitespaceOnlyPrimaryType_throws() {
        var ex = assertThrows(IllegalArgumentException.class, () ->
            new TypeDefinition<>("   ", PERMIT_TYPES, p -> Map.of()));
        assertEquals("primaryType cannot be blank", ex.getMessage());
    }

    @Test
    void constructor_primaryTypeNotInTypes_throws() {
        var ex = assertThrows(IllegalArgumentException.class, () ->
            new TypeDefinition<>("Unknown", PERMIT_TYPES, p -> Map.of()));
        assertTrue(ex.getMessage().contains("Unknown"));
        assertTrue(ex.getMessage().contains("types must contain primaryType"));
    }

    @Test
    void forRecord_nullRecordClass_throws() {
        assertThrows(NullPointerException.class, () ->
            TypeDefinition.forRecord(null, "Test", Map.of("Test", List.of())));
    }

    @Test
    void forRecord_nonRecordClass_throws() {
        @SuppressWarnings("unchecked")
        Class<Record> stringClass = (Class<Record>) (Class<?>) String.class;
        var ex = assertThrows(IllegalArgumentException.class, () ->
            TypeDefinition.forRecord(stringClass, "String", Map.of("String", List.of())));
        assertTrue(ex.getMessage().contains("must be a record"));
    }

    @Test
    void equality() {
        var definition1 = TypeDefinition.forRecord(Mail.class, "Mail", MAIL_TYPES);
        var definition2 = TypeDefinition.forRecord(Mail.class, "Mail", MAIL_TYPES);

        // Different extractor instances mean they won't be equal
        // But we can check that primaryType and types are equal
        assertEquals(definition1.primaryType(), definition2.primaryType());
        assertEquals(definition1.types(), definition2.types());
    }

    @Test
    void extractedMapPreservesOrder() {
        var definition = TypeDefinition.forRecord(Permit.class, "Permit", PERMIT_TYPES);
        var permit = new Permit("owner", "spender", BigInteger.ONE, BigInteger.TWO, BigInteger.TEN);

        var extracted = definition.extractor().apply(permit);
        var keys = extracted.keySet().toArray(String[]::new);

        // Record components should be in declaration order
        assertArrayEquals(new String[]{"owner", "spender", "value", "nonce", "deadline"}, keys);
    }
}
