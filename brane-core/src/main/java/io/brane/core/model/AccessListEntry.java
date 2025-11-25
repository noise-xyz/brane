package io.brane.core.model;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import java.util.List;
import java.util.Objects;

/**
 * Entry in a transaction's access list, as defined by EIP-2930.
 * 
 * <p>
 * Access lists allow transactions to pre-declare which accounts and storage
 * slots
 * they will access. This can reduce gas costs by "warming up" storage access,
 * since
 * the first access to a storage slot is more expensive than subsequent
 * accesses.
 * 
 * <p>
 * <strong>Use cases:</strong>
 * <ul>
 * <li>Complex contract interactions touching multiple accounts</li>
 * <li>Gas optimization for contracts with known storage access patterns</li>
 * <li>Required by some EIP-1559 transactions</li>
 * </ul>
 * 
 * <p>
 * <strong>Example:</strong> A transaction accessing USDC contract storage would
 * have
 * an AccessListEntry with the USDC contract address and the specific storage
 * slots being read/written.
 * 
 * @see <a href="https://eips.ethereum.org/EIPS/eip-2930">EIP-2930</a>
 */
public final class AccessListEntry {

    private final Address address;
    private final List<Hash> storageKeys;

    /**
     * Creates a new access list entry.
     * 
     * @param address     the contract address to access
     * @param storageKeys the list of storage slot keys (as 32-byte hashes) to
     *                    access
     * @throws NullPointerException if address or storageKeys is null
     */
    public AccessListEntry(final Address address, final List<Hash> storageKeys) {
        this.address = Objects.requireNonNull(address, "address");
        this.storageKeys = List.copyOf(Objects.requireNonNull(storageKeys, "storageKeys"));
    }

    public Address address() {
        return address;
    }

    public List<Hash> storageKeys() {
        return storageKeys;
    }

    @Override
    public String toString() {
        return "AccessListEntry{" + "address=" + address + ", storageKeys=" + storageKeys + '}';
    }
}
