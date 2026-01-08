package io.brane.core.crypto.eip712;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;

/**
 * EIP-712 domain separator fields.
 * <p>
 * All fields are optional (nullable) - include only what your protocol uses.
 * The domain separator uniquely identifies a signing context (dApp/protocol).
 *
 * @param name the dApp/protocol name, or null if not used
 * @param version the signing domain version, or null if not used
 * @param chainId the EIP-155 chain ID, or null if not used
 * @param verifyingContract the contract address that verifies the signature, or null if not used
 * @param salt disambiguation salt (rarely used), or null if not used
 * @see <a href="https://eips.ethereum.org/EIPS/eip-712">EIP-712</a>
 */
public record Eip712Domain(
        String name,
        String version,
        Long chainId,
        Address verifyingContract,
        Hash salt
) {

    /**
     * Creates a new builder for constructing an Eip712Domain.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Computes the domain separator hash.
     * <p>
     * The domain separator is computed as:
     * {@code keccak256(encodeType("EIP712Domain") || encodeData(domain))}
     * <p>
     * This value uniquely identifies the signing context and is used as
     * part of the EIP-712 hash computation to prevent cross-domain replay attacks.
     *
     * @return the 32-byte domain separator hash
     * @see <a href="https://eips.ethereum.org/EIPS/eip-712#definition-of-domainseparator">EIP-712 Domain Separator</a>
     */
    public Hash separator() {
        return TypedDataEncoder.hashDomain(this);
    }

    /**
     * Builder for constructing Eip712Domain instances.
     */
    public static final class Builder {
        private String name;
        private String version;
        private Long chainId;
        private Address verifyingContract;
        private Hash salt;

        Builder() {}

        /**
         * Sets the dApp/protocol name.
         *
         * @param name the name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the signing domain version.
         *
         * @param version the version
         * @return this builder
         */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        /**
         * Sets the EIP-155 chain ID.
         *
         * @param chainId the chain ID
         * @return this builder
         */
        public Builder chainId(long chainId) {
            this.chainId = chainId;
            return this;
        }

        /**
         * Sets the verifying contract address.
         *
         * @param verifyingContract the contract address
         * @return this builder
         */
        public Builder verifyingContract(Address verifyingContract) {
            this.verifyingContract = verifyingContract;
            return this;
        }

        /**
         * Sets the disambiguation salt.
         *
         * @param salt the salt
         * @return this builder
         */
        public Builder salt(Hash salt) {
            this.salt = salt;
            return this;
        }

        /**
         * Builds the Eip712Domain instance.
         *
         * @return the constructed Eip712Domain
         */
        public Eip712Domain build() {
            return new Eip712Domain(name, version, chainId, verifyingContract, salt);
        }
    }
}
