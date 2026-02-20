// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004;

import java.util.Arrays;
import java.util.Objects;

/**
 * ERC-8004 metadata key-value pair.
 *
 * <p>Represents an entry in the Identity Registry's metadata mapping. Keys are
 * arbitrary strings; values are arbitrary bytes.
 *
 * @param key   the metadata key
 * @param value the metadata value (arbitrary bytes)
 * @see <a href="https://eips.ethereum.org/EIPS/eip-8004">EIP-8004</a>
 */
public record MetadataEntry(String key, byte[] value) {

    public MetadataEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        value = value.clone();
    }

    /**
     * Returns the metadata value bytes.
     * <p>A defensive copy is returned to preserve immutability.
     *
     * @return a copy of the value bytes
     */
    @Override
    public byte[] value() {
        return value.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MetadataEntry other)) return false;
        return key.equals(other.key) && Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, Arrays.hashCode(value));
    }

    @Override
    public String toString() {
        return "MetadataEntry[key=" + key + ", value=(" + value.length + " bytes)]";
    }
}
