// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.primitives.rlp;

/**
 * Base sealed interface for all RLP items.
 *
 * @since 1.0
 */
public sealed interface RlpItem permits RlpString, RlpList {
    /**
     * Encodes this item into RLP byte representation.
     *
     * @return encoded bytes
     */
    byte[] encode();
}
