// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.crypto.erc8004;

import sh.brane.core.crypto.Signature;
import sh.brane.core.crypto.Signer;
import sh.brane.core.crypto.eip712.Eip712Domain;
import sh.brane.core.crypto.eip712.TypeDefinition;
import sh.brane.core.crypto.eip712.TypedData;
import sh.brane.core.crypto.eip712.TypedDataField;
import sh.brane.core.types.Address;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ERC-8004 agent wallet binding utilities.
 *
 * <p>The Identity Registry's {@code setAgentWallet()} requires an EIP-712 signature
 * proving wallet ownership. This class provides the EIP-712 type definition and
 * signing helper for that operation.
 *
 * <p>Example — bind a wallet to an agent:
 * <pre>{@code
 * var domain = Eip712Domain.builder()
 *     .name("ERC8004IdentityRegistry")
 *     .version("1")
 *     .chainId(1L)
 *     .verifyingContract(identityRegistryAddress)
 *     .build();
 *
 * var binding = new Erc8004Wallet.AgentWalletBinding(
 *     agentId.value(), walletAddress, deadline);
 *
 * Signature sig = Erc8004Wallet.signWalletBinding(binding, domain, walletSigner);
 * }</pre>
 *
 * @see <a href="https://eips.ethereum.org/EIPS/eip-8004">EIP-8004</a>
 */
public final class Erc8004Wallet {

    private Erc8004Wallet() {}

    /**
     * EIP-712 typed data for agent wallet binding.
     *
     * <p>Signed by the wallet being bound to prove ownership. The signature is
     * passed to {@code setAgentWallet()} on the Identity Registry.
     *
     * @param agentId  the ERC-721 token ID of the agent
     * @param wallet   the wallet address being bound
     * @param deadline the unix timestamp after which the signature expires
     */
    public record AgentWalletBinding(BigInteger agentId, Address wallet, BigInteger deadline) {

        /** EIP-712 type definition for AgentWalletBinding. */
        public static final TypeDefinition<AgentWalletBinding> DEFINITION =
            TypeDefinition.forRecord(
                AgentWalletBinding.class,
                "AgentWalletBinding",
                Map.of("AgentWalletBinding", List.of(
                    TypedDataField.of("agentId", "uint256"),
                    TypedDataField.of("wallet", "address"),
                    TypedDataField.of("deadline", "uint256")
                ))
            );

        public AgentWalletBinding {
            Objects.requireNonNull(agentId, "agentId");
            Objects.requireNonNull(wallet, "wallet");
            Objects.requireNonNull(deadline, "deadline");
        }
    }

    /**
     * Signs an agent wallet binding message.
     *
     * @param binding the wallet binding message
     * @param domain  the Identity Registry's EIP-712 domain
     * @param signer  the wallet signer (must be the {@code wallet} address)
     * @return signature with v=27 or v=28
     * @throws NullPointerException if any argument is null
     */
    public static Signature signWalletBinding(
            AgentWalletBinding binding, Eip712Domain domain, Signer signer) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(signer, "signer");
        return TypedData.create(domain, AgentWalletBinding.DEFINITION, binding).sign(signer);
    }
}
