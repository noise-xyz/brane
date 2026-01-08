package io.brane.core.crypto.eip712;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TypedDataFieldTest {

    @Test
    void constructor_validField() {
        var field = new TypedDataField("amount", "uint256");
        assertEquals("amount", field.name());
        assertEquals("uint256", field.type());
    }

    @Test
    void of_factoryMethod() {
        var field = TypedDataField.of("recipient", "address");
        assertEquals("recipient", field.name());
        assertEquals("address", field.type());
    }

    @Test
    void constructor_nullName_throws() {
        var ex = assertThrows(NullPointerException.class, () -> new TypedDataField(null, "uint256"));
        assertEquals("name", ex.getMessage());
    }

    @Test
    void constructor_nullType_throws() {
        var ex = assertThrows(NullPointerException.class, () -> new TypedDataField("amount", null));
        assertEquals("type", ex.getMessage());
    }

    @Test
    void constructor_blankName_throws() {
        var ex = assertThrows(IllegalArgumentException.class, () -> new TypedDataField("", "uint256"));
        assertEquals("name cannot be blank", ex.getMessage());
    }

    @Test
    void constructor_whitespaceOnlyName_throws() {
        var ex = assertThrows(IllegalArgumentException.class, () -> new TypedDataField("   ", "uint256"));
        assertEquals("name cannot be blank", ex.getMessage());
    }

    @Test
    void constructor_blankType_throws() {
        var ex = assertThrows(IllegalArgumentException.class, () -> new TypedDataField("amount", ""));
        assertEquals("type cannot be blank", ex.getMessage());
    }

    @Test
    void constructor_whitespaceOnlyType_throws() {
        var ex = assertThrows(IllegalArgumentException.class, () -> new TypedDataField("amount", "   "));
        assertEquals("type cannot be blank", ex.getMessage());
    }

    @Test
    void of_withStructType() {
        var field = TypedDataField.of("person", "Person");
        assertEquals("person", field.name());
        assertEquals("Person", field.type());
    }

    @Test
    void of_withArrayType() {
        var field = TypedDataField.of("values", "uint256[]");
        assertEquals("values", field.name());
        assertEquals("uint256[]", field.type());
    }

    @Test
    void equality() {
        var field1 = TypedDataField.of("amount", "uint256");
        var field2 = new TypedDataField("amount", "uint256");
        assertEquals(field1, field2);
        assertEquals(field1.hashCode(), field2.hashCode());
    }
}
