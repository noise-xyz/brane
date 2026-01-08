package io.brane.core.error;

/**
 * Exception for EIP-712 encoding and validation failures.
 *
 * <p>Extends BraneException directly as AbiEncodingException is final.
 * EIP-712 is semantically distinct from ABI encoding - it's a structured
 * data signing format rather than function call encoding.
 *
 * @since 0.1.0-alpha
 */
public final class Eip712Exception extends BraneException {

    public Eip712Exception(final String message) {
        super(message);
    }

    public Eip712Exception(final String message, final Throwable cause) {
        super(message, cause);
    }

    // ═══════════════════════════════════════════════════════════════
    // Factory methods for specific error conditions
    // ═══════════════════════════════════════════════════════════════

    /**
     * Type string references an unknown type not in the types map.
     */
    public static Eip712Exception unknownType(final String type) {
        return new Eip712Exception("Unknown EIP-712 type: " + type);
    }

    /**
     * Required field is missing from the message data.
     */
    public static Eip712Exception missingField(final String typeName, final String fieldName) {
        return new Eip712Exception(
            "Missing field '%s' in type '%s'".formatted(fieldName, typeName));
    }

    /**
     * Value cannot be encoded as the specified type.
     */
    public static Eip712Exception invalidValue(final String type, final Object value) {
        return new Eip712Exception(
            "Invalid value for type '%s': %s".formatted(type, value));
    }

    /**
     * Cyclic dependency detected in type definitions.
     */
    public static Eip712Exception cyclicDependency(final String typeName) {
        return new Eip712Exception("Cyclic type dependency at: " + typeName);
    }

    /**
     * Value exceeds the valid range for the type.
     */
    public static Eip712Exception valueOutOfRange(
            final String type, final Object value, final String reason) {
        return new Eip712Exception(
            "Value out of range for '%s': %s (%s)".formatted(type, value, reason));
    }

    /**
     * Primary type not found in types map.
     */
    public static Eip712Exception primaryTypeNotFound(final String primaryType) {
        return new Eip712Exception("Primary type not found: " + primaryType);
    }
}
