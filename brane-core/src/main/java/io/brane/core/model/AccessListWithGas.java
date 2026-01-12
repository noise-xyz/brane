// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.model;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/**
 * Result of an access list creation request, containing the generated access
 * list and the gas used.
 *
 * <p>
 * This type is returned by {@code eth_createAccessList} RPC method, which
 * simulates a transaction and returns the storage slots it would access along
 * with the gas that would be consumed.
 *
 * <p>
 * <strong>Use cases:</strong>
 * <ul>
 * <li>Gas optimization: Pre-generate access lists to reduce transaction costs
 * by 10-20%</li>
 * <li>Transaction preparation: Understand which storage slots a transaction
 * will touch before execution</li>
 * <li>EIP-2930 compliance: Generate access lists for transactions that require
 * them</li>
 * </ul>
 *
 * <p>
 * <strong>Example:</strong>
 *
 * <pre>{@code
 * AccessListWithGas result = client.createAccessList(request);
 * System.out.println("Gas used: " + result.gasUsed());
 * System.out.println("Access list entries: " + result.accessList().size());
 * }</pre>
 *
 * @param accessList the list of access list entries (required, may be empty)
 * @param gasUsed    the gas that would be consumed by the transaction (required)
 *
 * @see AccessListEntry
 * @see <a href="https://eips.ethereum.org/EIPS/eip-2930">EIP-2930</a>
 * @since 0.1.0-alpha
 */
public record AccessListWithGas(
        List<AccessListEntry> accessList,
        BigInteger gasUsed) {

    /**
     * Validates required fields and makes defensive copy of accessList.
     *
     * @throws NullPointerException if accessList or gasUsed is null
     */
    public AccessListWithGas {
        Objects.requireNonNull(accessList, "accessList cannot be null");
        Objects.requireNonNull(gasUsed, "gasUsed cannot be null");
        accessList = List.copyOf(accessList);  // Defensive copy for immutability
    }
}
