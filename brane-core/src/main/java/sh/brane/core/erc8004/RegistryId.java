// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004;

import java.util.Objects;

import sh.brane.core.types.Address;

/**
 * Identifies an ERC-8004 registry on a specific chain.
 *
 * <p>Format: {@code eip155:{chainId}:{address}} — structurally identical to
 * <a href="https://chainagnostic.org/CAIPs/caip-10">CAIP-10</a>.
 *
 * <p>Example: {@code eip155:1:0x8004a169fb4a3325136eb29fa0ceb6d2e539a432}
 *
 * @param chainId the EIP-155 chain ID (e.g., 1 for mainnet, 8453 for Base)
 * @param address the registry contract address on that chain
 * @see <a href="https://eips.ethereum.org/EIPS/eip-8004">EIP-8004</a>
 */
public record RegistryId(long chainId, Address address) {

    private static final String NAMESPACE = "eip155";

    public RegistryId {
        if (chainId <= 0) {
            throw new IllegalArgumentException("chainId must be positive, got " + chainId);
        }
        Objects.requireNonNull(address, "address");
    }

    /**
     * Parses a registry identifier string.
     *
     * @param id the identifier (e.g., "eip155:1:0x8004a169...")
     * @return the parsed RegistryId
     * @throws NullPointerException     if id is null
     * @throws IllegalArgumentException if the format is invalid or namespace is not "eip155"
     */
    public static RegistryId parse(String id) {
        Objects.requireNonNull(id, "id");
        String[] parts = id.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                "Expected format eip155:{chainId}:{address}, got: " + id);
        }
        if (!NAMESPACE.equals(parts[0])) {
            throw new IllegalArgumentException("Unsupported namespace: " + parts[0]);
        }
        long chainId = Long.parseLong(parts[1]);
        Address address = new Address(parts[2]);
        return new RegistryId(chainId, address);
    }

    @Override
    public String toString() {
        return NAMESPACE + ":" + chainId + ":" + address.value();
    }
}
