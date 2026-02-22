// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.contract.erc8004;

/**
 * ABI JSON constants for ERC-8004 registry contracts.
 *
 * <p>These are minimal ABIs containing only the functions and events used by
 * {@link TrustlessAgents}. Derived from the ERC-8004 specification.
 */
final class Erc8004AbiConstants {

    /** Identity Registry ABI (functions and events used by TrustlessAgents). */
    static final String IDENTITY_REGISTRY_ABI = """
        [
          {
            "inputs": [{"internalType": "string", "name": "agentURI", "type": "string"}],
            "name": "register",
            "outputs": [{"internalType": "uint256", "name": "", "type": "uint256"}],
            "stateMutability": "nonpayable",
            "type": "function"
          },
          {
            "inputs": [
              {"internalType": "uint256", "name": "agentId", "type": "uint256"},
              {"internalType": "string", "name": "newURI", "type": "string"}
            ],
            "name": "setAgentURI",
            "outputs": [],
            "stateMutability": "nonpayable",
            "type": "function"
          },
          {
            "inputs": [
              {"internalType": "uint256", "name": "agentId", "type": "uint256"},
              {"internalType": "string", "name": "metadataKey", "type": "string"},
              {"internalType": "bytes", "name": "metadataValue", "type": "bytes"}
            ],
            "name": "setMetadata",
            "outputs": [],
            "stateMutability": "nonpayable",
            "type": "function"
          },
          {
            "inputs": [
              {"internalType": "uint256", "name": "agentId", "type": "uint256"},
              {"internalType": "string", "name": "metadataKey", "type": "string"}
            ],
            "name": "getMetadata",
            "outputs": [{"internalType": "bytes", "name": "", "type": "bytes"}],
            "stateMutability": "view",
            "type": "function"
          },
          {
            "inputs": [
              {"internalType": "uint256", "name": "agentId", "type": "uint256"},
              {"internalType": "address", "name": "newWallet", "type": "address"},
              {"internalType": "uint256", "name": "deadline", "type": "uint256"},
              {"internalType": "bytes", "name": "signature", "type": "bytes"}
            ],
            "name": "setAgentWallet",
            "outputs": [],
            "stateMutability": "nonpayable",
            "type": "function"
          },
          {
            "inputs": [{"internalType": "uint256", "name": "agentId", "type": "uint256"}],
            "name": "getAgentWallet",
            "outputs": [{"internalType": "address", "name": "", "type": "address"}],
            "stateMutability": "view",
            "type": "function"
          },
          {
            "inputs": [{"internalType": "uint256", "name": "agentId", "type": "uint256"}],
            "name": "unsetAgentWallet",
            "outputs": [],
            "stateMutability": "nonpayable",
            "type": "function"
          },
          {
            "inputs": [{"internalType": "uint256", "name": "tokenId", "type": "uint256"}],
            "name": "tokenURI",
            "outputs": [{"internalType": "string", "name": "", "type": "string"}],
            "stateMutability": "view",
            "type": "function"
          },
          {
            "anonymous": false,
            "inputs": [
              {"indexed": true, "internalType": "uint256", "name": "agentId", "type": "uint256"},
              {"indexed": false, "internalType": "string", "name": "agentURI", "type": "string"},
              {"indexed": true, "internalType": "address", "name": "owner", "type": "address"}
            ],
            "name": "Registered",
            "type": "event"
          },
          {
            "anonymous": false,
            "inputs": [
              {"indexed": true, "internalType": "uint256", "name": "agentId", "type": "uint256"},
              {"indexed": false, "internalType": "string", "name": "newURI", "type": "string"},
              {"indexed": true, "internalType": "address", "name": "updatedBy", "type": "address"}
            ],
            "name": "URIUpdated",
            "type": "event"
          }
        ]
        """;

    /** Reputation Registry ABI (functions and events used by TrustlessAgents). */
    static final String REPUTATION_REGISTRY_ABI = """
        [
          {
            "inputs": [],
            "name": "getIdentityRegistry",
            "outputs": [{"internalType": "address", "name": "", "type": "address"}],
            "stateMutability": "view",
            "type": "function"
          },
          {
            "inputs": [
              {"internalType": "uint256", "name": "agentId", "type": "uint256"},
              {"internalType": "int128", "name": "value", "type": "int128"},
              {"internalType": "uint8", "name": "valueDecimals", "type": "uint8"},
              {"internalType": "string", "name": "tag1", "type": "string"},
              {"internalType": "string", "name": "tag2", "type": "string"},
              {"internalType": "string", "name": "endpoint", "type": "string"},
              {"internalType": "string", "name": "feedbackURI", "type": "string"},
              {"internalType": "bytes32", "name": "feedbackHash", "type": "bytes32"}
            ],
            "name": "giveFeedback",
            "outputs": [],
            "stateMutability": "nonpayable",
            "type": "function"
          },
          {
            "inputs": [
              {"internalType": "uint256", "name": "agentId", "type": "uint256"},
              {"internalType": "uint64", "name": "feedbackIndex", "type": "uint64"}
            ],
            "name": "revokeFeedback",
            "outputs": [],
            "stateMutability": "nonpayable",
            "type": "function"
          },
          {
            "inputs": [
              {"internalType": "uint256", "name": "agentId", "type": "uint256"},
              {"internalType": "address", "name": "clientAddress", "type": "address"},
              {"internalType": "uint64", "name": "feedbackIndex", "type": "uint64"},
              {"internalType": "string", "name": "responseURI", "type": "string"},
              {"internalType": "bytes32", "name": "responseHash", "type": "bytes32"}
            ],
            "name": "appendResponse",
            "outputs": [],
            "stateMutability": "nonpayable",
            "type": "function"
          },
          {
            "inputs": [
              {"internalType": "uint256", "name": "agentId", "type": "uint256"},
              {"internalType": "address[]", "name": "clientAddresses", "type": "address[]"},
              {"internalType": "string", "name": "tag1", "type": "string"},
              {"internalType": "string", "name": "tag2", "type": "string"}
            ],
            "name": "getSummary",
            "outputs": [
              {"internalType": "uint64", "name": "count", "type": "uint64"},
              {"internalType": "int128", "name": "summaryValue", "type": "int128"},
              {"internalType": "uint8", "name": "summaryValueDecimals", "type": "uint8"}
            ],
            "stateMutability": "view",
            "type": "function"
          },
          {
            "anonymous": false,
            "inputs": [
              {"indexed": true, "internalType": "uint256", "name": "agentId", "type": "uint256"},
              {"indexed": true, "internalType": "address", "name": "clientAddress", "type": "address"},
              {"indexed": false, "internalType": "uint64", "name": "feedbackIndex", "type": "uint64"},
              {"indexed": false, "internalType": "int128", "name": "value", "type": "int128"},
              {"indexed": false, "internalType": "uint8", "name": "valueDecimals", "type": "uint8"},
              {"indexed": true, "internalType": "string", "name": "indexedTag1", "type": "string"},
              {"indexed": false, "internalType": "string", "name": "tag1", "type": "string"},
              {"indexed": false, "internalType": "string", "name": "tag2", "type": "string"},
              {"indexed": false, "internalType": "string", "name": "endpoint", "type": "string"},
              {"indexed": false, "internalType": "string", "name": "feedbackURI", "type": "string"},
              {"indexed": false, "internalType": "bytes32", "name": "feedbackHash", "type": "bytes32"}
            ],
            "name": "NewFeedback",
            "type": "event"
          },
          {
            "anonymous": false,
            "inputs": [
              {"indexed": true, "internalType": "uint256", "name": "agentId", "type": "uint256"},
              {"indexed": true, "internalType": "address", "name": "clientAddress", "type": "address"},
              {"indexed": true, "internalType": "uint64", "name": "feedbackIndex", "type": "uint64"}
            ],
            "name": "FeedbackRevoked",
            "type": "event"
          }
        ]
        """;

    private Erc8004AbiConstants() {}
}
