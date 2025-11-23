package io.brane.primitives.rlp;

/**
 * Base sealed interface for all RLP items.
 */
public sealed interface RlpItem permits RlpString, RlpList {
    /**
     * Encodes this item into RLP byte representation.
     *
     * @return encoded bytes
     */
    byte[] encode();
}
