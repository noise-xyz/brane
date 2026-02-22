// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.contract.erc8004;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import sh.brane.contract.BraneContract;
import sh.brane.core.abi.Abi;
import sh.brane.core.crypto.Signature;
import sh.brane.core.crypto.Signer;
import sh.brane.core.crypto.eip712.Eip712Domain;
import sh.brane.core.crypto.erc8004.Erc8004Wallet;
import sh.brane.core.crypto.erc8004.Erc8004Wallet.AgentWalletBinding;
import sh.brane.core.erc8004.AgentId;
import sh.brane.core.erc8004.AgentRegistered;
import sh.brane.core.erc8004.Erc8004Addresses;
import sh.brane.core.erc8004.FeedbackSubmitted;
import sh.brane.core.erc8004.FeedbackValue;
import sh.brane.core.erc8004.MetadataEntry;
import sh.brane.core.erc8004.registration.AgentRegistration;
import sh.brane.core.model.LogEntry;
import sh.brane.core.model.TransactionReceipt;
import sh.brane.core.types.Address;
import sh.brane.core.types.Hash;
import sh.brane.core.types.HexData;
import sh.brane.primitives.Hex;
import sh.brane.rpc.Brane;
import sh.brane.rpc.CallRequest;
import sh.brane.rpc.LogFilter;

/**
 * High-level client for ERC-8004 Trustless Agents registries.
 *
 * <p>Wraps the Identity, Reputation, and Validation registries behind a single
 * ergonomic API. Uses {@link BraneContract#bind} for proxy-bindable functions
 * and raw {@code eth_call} for tuple-returning view functions.
 *
 * <p>Example — register an agent:
 * <pre>{@code
 * var agents = TrustlessAgents.connectMainnet(signerClient);
 * AgentId myAgent = agents.register("https://myagent.example.com/registration.json");
 * agents.setMetadata(myAgent, "version", "1.0".getBytes());
 * }</pre>
 *
 * @see <a href="https://eips.ethereum.org/EIPS/eip-8004">EIP-8004</a>
 */
public final class TrustlessAgents {

    private final Brane.Signer client;
    private final Address identityAddress;
    private final Address reputationAddress;
    private final IdentityRegistryContract identity;
    private final ReputationRegistryContract reputation;
    private final Abi identityAbi;
    private final Abi reputationAbi;

    private TrustlessAgents(Brane.Signer client,
                            Address identityAddress, Address reputationAddress) {
        this.client = client;
        this.identityAddress = identityAddress;
        this.reputationAddress = reputationAddress;
        this.identityAbi = Abi.fromJson(Erc8004AbiConstants.IDENTITY_REGISTRY_ABI);
        this.reputationAbi = Abi.fromJson(Erc8004AbiConstants.REPUTATION_REGISTRY_ABI);
        this.identity = BraneContract.bind(
            identityAddress, Erc8004AbiConstants.IDENTITY_REGISTRY_ABI,
            client, IdentityRegistryContract.class);
        this.reputation = BraneContract.bind(
            reputationAddress, Erc8004AbiConstants.REPUTATION_REGISTRY_ABI,
            client, ReputationRegistryContract.class);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Factory methods
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Connects to ERC-8004 registries at explicit addresses.
     *
     * @param client     a signing client
     * @param identity   the Identity Registry address
     * @param reputation the Reputation Registry address
     * @return the connected client
     * @throws NullPointerException if any argument is null
     */
    public static TrustlessAgents connect(Brane.Signer client,
                                          Address identity, Address reputation) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(reputation, "reputation");
        return new TrustlessAgents(client, identity, reputation);
    }

    /**
     * Connects to mainnet ERC-8004 registries (all EVM chains share the same addresses).
     *
     * @param client a signing client
     * @return the connected client
     * @throws NullPointerException if any argument is null
     */
    public static TrustlessAgents connectMainnet(Brane.Signer client) {
        return connect(client,
            Erc8004Addresses.MAINNET_IDENTITY,
            Erc8004Addresses.MAINNET_REPUTATION);
    }

    /**
     * Connects to Sepolia testnet ERC-8004 registries.
     *
     * @param client a signing client
     * @return the connected client
     * @throws NullPointerException if any argument is null
     */
    public static TrustlessAgents connectSepolia(Brane.Signer client) {
        return connect(client,
            Erc8004Addresses.SEPOLIA_IDENTITY,
            Erc8004Addresses.SEPOLIA_REPUTATION);
    }

    /**
     * Read-only variant for querying registries without signing capability.
     *
     * @param client     a read-only client
     * @param identity   the Identity Registry address
     * @param reputation the Reputation Registry address
     * @return the read-only client
     * @throws NullPointerException if any argument is null
     */
    public static ReadOnly connectReadOnly(Brane client,
                                           Address identity, Address reputation) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(reputation, "reputation");
        return new ReadOnly(client, identity, reputation);
    }

    /**
     * Read-only variant for mainnet.
     *
     * @param client a read-only client
     * @return the read-only client
     * @throws NullPointerException if any argument is null
     */
    public static ReadOnly connectMainnetReadOnly(Brane client) {
        return connectReadOnly(client,
            Erc8004Addresses.MAINNET_IDENTITY,
            Erc8004Addresses.MAINNET_REPUTATION);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Identity Registry — write operations
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Registers a new agent with a URI pointing to the Agent Card.
     *
     * @param agentURI the agent registration file URI
     * @return the assigned agent token ID
     * @throws NullPointerException if any required argument is null
     */
    public AgentId register(String agentURI) {
        Objects.requireNonNull(agentURI, "agentURI");
        TransactionReceipt receipt = identity.register(agentURI);
        List<AgentRegistered> events = identityAbi.decodeEvents(
            "Registered", receipt.logs(), AgentRegistered.class);
        if (events.isEmpty()) {
            throw new IllegalStateException("No Registered event in receipt");
        }
        return events.getFirst().toAgentId();
    }

    /**
     * Registers a new agent with a URI and initial metadata.
     *
     * <p>Registers the agent first, then sets metadata entries individually.
     *
     * @param agentURI the agent registration file URI
     * @param metadata initial metadata entries
     * @return the assigned agent token ID
     * @throws NullPointerException if any required argument is null
     */
    public AgentId register(String agentURI, List<MetadataEntry> metadata) {
        Objects.requireNonNull(metadata, "metadata");
        AgentId id = register(agentURI);
        for (MetadataEntry entry : metadata) {
            setMetadata(id, entry.key(), entry.value());
        }
        return id;
    }

    /**
     * Updates the agent's URI.
     *
     * @param agentId the agent token ID
     * @param newURI  the new URI
     * @throws NullPointerException if any required argument is null
     */
    public void setAgentURI(AgentId agentId, String newURI) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(newURI, "newURI");
        identity.setAgentURI(agentId.value(), newURI);
    }

    /**
     * Sets a metadata entry on the agent.
     *
     * @param agentId the agent token ID
     * @param key     the metadata key
     * @param value   the metadata value
     * @throws NullPointerException if any required argument is null
     */
    public void setMetadata(AgentId agentId, String key, byte[] value) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        identity.setMetadata(agentId.value(), key, value);
    }

    /**
     * Reads a metadata entry from the agent.
     *
     * @param agentId the agent token ID
     * @param key     the metadata key
     * @return the metadata value bytes (empty if key not set)
     */
    public byte[] getMetadata(AgentId agentId, String key) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(key, "key");
        return identity.getMetadata(agentId.value(), key);
    }

    /**
     * Binds a payment wallet to the agent using EIP-712 signed proof.
     *
     * @param agentId      the agent token ID
     * @param wallet       the wallet address to bind
     * @param walletSigner the wallet's signer (proves ownership)
     * @throws NullPointerException if any required argument is null
     */
    public void bindWallet(AgentId agentId, Address wallet, Signer walletSigner) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(wallet, "wallet");
        Objects.requireNonNull(walletSigner, "walletSigner");

        long deadline = System.currentTimeMillis() / 1000 + 3600; // 1 hour
        var binding = new AgentWalletBinding(
            agentId.value(), wallet, BigInteger.valueOf(deadline));

        Eip712Domain domain = Eip712Domain.builder()
            .name("ERC8004IdentityRegistry")
            .version("1")
            .chainId(client.chainId().longValue())
            .verifyingContract(identityAddress)
            .build();

        Signature sig = Erc8004Wallet.signWalletBinding(binding, domain, walletSigner);
        byte[] sigBytes = packSignature(sig);
        identity.setAgentWallet(agentId.value(), wallet, BigInteger.valueOf(deadline), sigBytes);
    }

    /**
     * Gets the payment wallet address bound to the agent.
     *
     * @param agentId the agent token ID
     * @return the wallet address (zero address if not bound)
     */
    public Address getAgentWallet(AgentId agentId) {
        Objects.requireNonNull(agentId, "agentId");
        return identity.getAgentWallet(agentId.value());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Reputation Registry
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Submits feedback for an agent.
     *
     * @param agentId the agent token ID
     * @param score   the feedback score
     * @param tag1    primary tag ({@code null} accepted, defaults to empty)
     * @param tag2    secondary tag ({@code null} accepted, defaults to empty)
     */
    public void giveFeedback(AgentId agentId, FeedbackValue score,
                             String tag1, String tag2) {
        giveFeedback(agentId, score, tag1, tag2, "", "", null);
    }

    /**
     * Submits feedback for an agent with full details.
     *
     * @param agentId      the agent token ID
     * @param score        the feedback score
     * @param tag1         primary tag ({@code null} accepted, defaults to empty)
     * @param tag2         secondary tag ({@code null} accepted, defaults to empty)
     * @param endpoint     the endpoint being rated ({@code null} accepted, defaults to empty)
     * @param feedbackURI  URI to off-chain feedback details ({@code null} accepted, defaults to empty)
     * @param feedbackHash hash of off-chain feedback content ({@code null} for no hash)
     * @throws NullPointerException if any required argument is null
     */
    public void giveFeedback(AgentId agentId, FeedbackValue score,
                             String tag1, String tag2,
                             String endpoint, String feedbackURI, Hash feedbackHash) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(score, "score");
        reputation.giveFeedback(
            agentId.value(),
            score.value(),
            BigInteger.valueOf(score.decimals()),
            Objects.requireNonNullElse(tag1, ""),
            Objects.requireNonNullElse(tag2, ""),
            Objects.requireNonNullElse(endpoint, ""),
            Objects.requireNonNullElse(feedbackURI, ""),
            feedbackHash != null ? feedbackHash.toBytes() : new byte[32]);
    }

    /**
     * Revokes previously submitted feedback.
     *
     * @param agentId       the agent token ID
     * @param feedbackIndex the index of the feedback to revoke
     * @throws NullPointerException if any required argument is null
     */
    public void revokeFeedback(AgentId agentId, long feedbackIndex) {
        Objects.requireNonNull(agentId, "agentId");
        reputation.revokeFeedback(agentId.value(), BigInteger.valueOf(feedbackIndex));
    }

    /**
     * Appends a response to existing feedback.
     *
     * @param agentId       the agent token ID
     * @param clientAddress the original feedback submitter
     * @param feedbackIndex the feedback index
     * @param responseURI   URI to the response content ({@code null} accepted, defaults to empty)
     * @param responseHash  hash of the response content ({@code null} for no hash)
     * @throws NullPointerException if any required argument is null
     */
    public void appendResponse(AgentId agentId, Address clientAddress,
                               long feedbackIndex, String responseURI, Hash responseHash) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(clientAddress, "clientAddress");
        reputation.appendResponse(
            agentId.value(), clientAddress,
            BigInteger.valueOf(feedbackIndex),
            Objects.requireNonNullElse(responseURI, ""),
            responseHash != null ? responseHash.toBytes() : new byte[32]);
    }

    /**
     * Gets the feedback summary for an agent (all clients, no tag filter).
     *
     * @param agentId the agent token ID
     * @return the aggregated feedback summary
     * @throws NullPointerException if agentId is null
     */
    public FeedbackSummary getSummary(AgentId agentId) {
        return getSummary(agentId, List.of(), "", "");
    }

    /**
     * Gets the feedback summary for an agent with filters.
     *
     * @param agentId the agent token ID
     * @param clients client addresses to include (empty for all)
     * @param tag1    primary tag filter (empty for all; {@code null} accepted, defaults to empty)
     * @param tag2    secondary tag filter (empty for all; {@code null} accepted, defaults to empty)
     * @return the aggregated feedback summary
     * @throws NullPointerException if agentId or clients is null
     */
    public FeedbackSummary getSummary(AgentId agentId, List<Address> clients,
                                      String tag1, String tag2) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(clients, "clients");
        var call = reputationAbi.encodeFunction("getSummary",
            agentId.value(), clients.toArray(Address[]::new),
            Objects.requireNonNullElse(tag1, ""),
            Objects.requireNonNullElse(tag2, ""));
        HexData result = client.call(CallRequest.builder()
            .to(reputationAddress)
            .data(new HexData(call.data()))
            .build());
        return decodeFeedbackSummary(result.value());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Events
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets agent registration events in a block range.
     *
     * @param fromBlock start block (inclusive)
     * @param toBlock   end block (inclusive)
     * @return the registration events (empty list if none found)
     */
    public List<AgentRegistered> getRegistrations(long fromBlock, long toBlock) {
        Hash topic = Abi.eventTopic("Registered(uint256,string,address)");
        var filter = new LogFilter(
            Optional.of(fromBlock), Optional.of(toBlock),
            Optional.of(List.of(identityAddress)),
            Optional.of(List.of(topic)));
        List<LogEntry> logs = client.getLogs(filter);
        return identityAbi.decodeEvents("Registered", logs, AgentRegistered.class);
    }

    /**
     * Gets feedback events for a specific agent in a block range.
     *
     * @param agentId   the agent token ID
     * @param fromBlock start block (inclusive)
     * @param toBlock   end block (inclusive)
     * @return the feedback events (empty list if none found)
     */
    public List<FeedbackSubmitted> getFeedbackEvents(AgentId agentId,
                                                     long fromBlock, long toBlock) {
        Objects.requireNonNull(agentId, "agentId");
        Hash topic0 = Abi.eventTopic(
            "NewFeedback(uint256,address,uint64,int128,uint8,string,string,string,string,string,bytes32)");
        // topic1 = agentId as left-padded 32-byte uint256
        Hash topic1 = new Hash("0x" + String.format("%064x", agentId.value()));
        var filter = new LogFilter(
            Optional.of(fromBlock), Optional.of(toBlock),
            Optional.of(List.of(reputationAddress)),
            Optional.of(List.of(topic0, topic1)));
        List<LogEntry> logs = client.getLogs(filter);
        return reputationAbi.decodeEvents("NewFeedback", logs, FeedbackSubmitted.class);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Agent Discovery
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the agent's registration URI from on-chain tokenURI.
     *
     * @param agentId the agent token ID
     * @return the URI string
     * @throws NullPointerException if agentId is null
     */
    public String getAgentURI(AgentId agentId) {
        Objects.requireNonNull(agentId, "agentId");
        return identity.tokenURI(agentId.value());
    }

    /**
     * Parses an Agent Registration File (Agent Card) from JSON.
     *
     * <p>This is a static convenience — the caller fetches the JSON from the
     * URI returned by {@link #getAgentURI(AgentId)}.
     *
     * @param json the JSON string
     * @return the parsed registration
     * @throws NullPointerException     if json is null
     * @throws IllegalArgumentException if the JSON cannot be parsed
     */
    public static AgentRegistration parseRegistration(String json) {
        return AgentRegistration.fromJson(json);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Read-Only Client
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Read-only variant of TrustlessAgents for querying without signing.
     */
    public static final class ReadOnly {
        private final Brane client;
        private final Address identityAddress;
        private final Address reputationAddress;
        private final IdentityRegistryReadOnlyContract identity;
        private final Abi identityAbi;
        private final Abi reputationAbi;

        private ReadOnly(Brane client, Address identityAddress, Address reputationAddress) {
            this.client = client;
            this.identityAddress = identityAddress;
            this.reputationAddress = reputationAddress;
            this.identityAbi = Abi.fromJson(Erc8004AbiConstants.IDENTITY_REGISTRY_ABI);
            this.reputationAbi = Abi.fromJson(Erc8004AbiConstants.REPUTATION_REGISTRY_ABI);
            this.identity = BraneContract.bindReadOnly(
                identityAddress, Erc8004AbiConstants.IDENTITY_REGISTRY_ABI,
                client, IdentityRegistryReadOnlyContract.class);
        }

        /**
         * Reads a metadata entry.
         *
         * @param agentId the agent token ID
         * @param key     the metadata key
         * @return the metadata value bytes (empty if key not set)
         * @throws NullPointerException if any argument is null
         */
        public byte[] getMetadata(AgentId agentId, String key) {
            Objects.requireNonNull(agentId, "agentId");
            Objects.requireNonNull(key, "key");
            return identity.getMetadata(agentId.value(), key);
        }

        /**
         * Gets the agent's bound wallet address.
         *
         * @param agentId the agent token ID
         * @return the wallet address (zero address if not bound)
         * @throws NullPointerException if agentId is null
         */
        public Address getAgentWallet(AgentId agentId) {
            Objects.requireNonNull(agentId, "agentId");
            return identity.getAgentWallet(agentId.value());
        }

        /**
         * Gets the agent's registration URI.
         *
         * @param agentId the agent token ID
         * @return the URI string
         * @throws NullPointerException if agentId is null
         */
        public String getAgentURI(AgentId agentId) {
            Objects.requireNonNull(agentId, "agentId");
            return identity.tokenURI(agentId.value());
        }

        /**
         * Gets the feedback summary for an agent.
         *
         * @param agentId the agent token ID
         * @return the aggregated feedback summary
         * @throws NullPointerException if agentId is null
         */
        public FeedbackSummary getSummary(AgentId agentId) {
            return getSummary(agentId, List.of(), "", "");
        }

        /**
         * Gets the feedback summary with filters.
         *
         * @param agentId the agent token ID
         * @param clients client addresses to include (empty for all)
         * @param tag1    primary tag filter (empty for all; {@code null} accepted, defaults to empty)
         * @param tag2    secondary tag filter (empty for all; {@code null} accepted, defaults to empty)
         * @return the aggregated feedback summary
         * @throws NullPointerException if agentId or clients is null
         */
        public FeedbackSummary getSummary(AgentId agentId, List<Address> clients,
                                          String tag1, String tag2) {
            Objects.requireNonNull(agentId, "agentId");
            Objects.requireNonNull(clients, "clients");
            var call = reputationAbi.encodeFunction("getSummary",
                agentId.value(), clients.toArray(Address[]::new),
                Objects.requireNonNullElse(tag1, ""),
                Objects.requireNonNullElse(tag2, ""));
            HexData result = client.call(CallRequest.builder()
                .to(reputationAddress)
                .data(new HexData(call.data()))
                .build());
            return decodeFeedbackSummary(result.value());
        }

        /**
         * Gets registration events in a block range.
         *
         * @param fromBlock start block (inclusive)
         * @param toBlock   end block (inclusive)
         * @return the registration events (empty list if none found)
         */
        public List<AgentRegistered> getRegistrations(long fromBlock, long toBlock) {
            Hash topic = Abi.eventTopic("Registered(uint256,string,address)");
            var filter = new LogFilter(
                Optional.of(fromBlock), Optional.of(toBlock),
                Optional.of(List.of(identityAddress)),
                Optional.of(List.of(topic)));
            List<LogEntry> logs = client.getLogs(filter);
            return identityAbi.decodeEvents("Registered", logs, AgentRegistered.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Internal: proxy-bindable interfaces
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Identity Registry contract interface for proxy binding.
     * Write functions return TransactionReceipt; single-return view functions
     * return their native types.
     */
    interface IdentityRegistryContract {
        TransactionReceipt register(String agentURI);
        TransactionReceipt setAgentURI(BigInteger agentId, String newURI);
        TransactionReceipt setMetadata(BigInteger agentId, String metadataKey, byte[] metadataValue);
        TransactionReceipt setAgentWallet(BigInteger agentId, Address newWallet,
                                          BigInteger deadline, byte[] signature);
        TransactionReceipt unsetAgentWallet(BigInteger agentId);
        byte[] getMetadata(BigInteger agentId, String metadataKey);
        Address getAgentWallet(BigInteger agentId);
        String tokenURI(BigInteger agentId);
    }

    /** Read-only variant — view functions only. */
    interface IdentityRegistryReadOnlyContract {
        byte[] getMetadata(BigInteger agentId, String metadataKey);
        Address getAgentWallet(BigInteger agentId);
        String tokenURI(BigInteger agentId);
    }

    /**
     * Reputation Registry contract interface for proxy binding.
     */
    interface ReputationRegistryContract {
        Address getIdentityRegistry();
        TransactionReceipt giveFeedback(BigInteger agentId, BigInteger value,
                                        BigInteger valueDecimals, String tag1, String tag2,
                                        String endpoint, String feedbackURI, byte[] feedbackHash);
        TransactionReceipt revokeFeedback(BigInteger agentId, BigInteger feedbackIndex);
        TransactionReceipt appendResponse(BigInteger agentId, Address clientAddress,
                                          BigInteger feedbackIndex, String responseURI,
                                          byte[] responseHash);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Internal: tuple decoding helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Packs an ECDSA signature into 65 bytes: {@code r || s || v}.
     */
    private static byte[] packSignature(Signature sig) {
        var packed = new byte[65];
        System.arraycopy(sig.r(), 0, packed, 0, 32);
        System.arraycopy(sig.s(), 0, packed, 32, 32);
        packed[64] = (byte) sig.v();
        return packed;
    }

    /**
     * Decodes a getSummary() return value: (uint64 count, int128 summaryValue, uint8 decimals).
     * ABI encoding: 3 contiguous 32-byte slots. The int128 slot is sign-extended to 256 bits
     * per the Solidity ABI specification.
     */
    static FeedbackSummary decodeFeedbackSummary(String hexOutput) {
        String hex = hexOutput.startsWith("0x") ? hexOutput.substring(2) : hexOutput;
        if (hex.length() < 192) {
            throw new IllegalArgumentException("Unexpected output length for getSummary: " + hex.length());
        }
        long count = new BigInteger(hex.substring(0, 64), 16).longValue();
        // int128 is signed — decode as signed two's complement (ABI sign-extends to 256 bits)
        byte[] slot1Bytes = Hex.decode(hex.substring(64, 128));
        BigInteger summaryValue = new BigInteger(slot1Bytes);
        int decimals = new BigInteger(hex.substring(128, 192), 16).intValue();
        return new FeedbackSummary(count, new FeedbackValue(summaryValue, decimals));
    }
}
