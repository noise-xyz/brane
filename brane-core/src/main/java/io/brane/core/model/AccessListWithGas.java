package io.brane.core.model;

import java.math.BigInteger;
import java.util.List;

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
 * @param accessList the list of access list entries (addresses and storage
 *                   keys)
 * @param gasUsed    the gas that would be consumed by the transaction
 * 
 * @see AccessListEntry
 * @see <a href="https://eips.ethereum.org/EIPS/eip-2930">EIP-2930</a>
 */
public record AccessListWithGas(
        List<AccessListEntry> accessList,
        BigInteger gasUsed) {
}
