// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.abi;

/**
 * Represents variable-size ABI types that require offset-based indirection in encoding.
 * <p>
 * Dynamic types have variable-length encoding where the head contains an offset pointer
 * to the actual data in the tail section. This is in contrast to static types which
 * always occupy exactly 32 bytes and are encoded directly in place.
 * <p>
 * Variable-size types include:
 * <ul>
 *   <li>{@link Bytes} - dynamic bytes (note: bytesN types are static)</li>
 *   <li>{@link Utf8String} - UTF-8 encoded strings</li>
 *   <li>{@link Array} - arrays (T[] is always dynamic, T[N] is dynamic if T is dynamic)</li>
 *   <li>{@link Tuple} - tuples containing any dynamic component</li>
 * </ul>
 * <p>
 * Note that some permitted types may be static in certain configurations (e.g., bytesN,
 * static arrays with static elements, tuples with all static components). The default
 * implementation returns true, but implementations may override this based on their
 * actual content.
 *
 * @see AbiType
 * @see StaticAbiType
 * @see AbiEncoder
 * @see AbiDecoder
 * @since 0.1.0-alpha
 */
public sealed interface DynamicAbiType extends AbiType permits Bytes, Utf8String, Array, Tuple {

    /**
     * Returns whether this type is dynamic.
     * <p>
     * Dynamic types require offset-based encoding in ABI. By default, types implementing
     * this interface are considered dynamic, but specific implementations may override
     * this based on their configuration (e.g., bytesN is static, static arrays of static
     * elements are static).
     *
     * @return true by default, implementations may override
     */
    @Override
    default boolean isDynamic() {
        return true;
    }
}
