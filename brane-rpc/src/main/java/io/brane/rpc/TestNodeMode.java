package io.brane.rpc;

/**
 * Represents different test node implementations and their RPC method prefixes.
 * <p>
 * Each test node (Anvil, Hardhat, Ganache) uses a different prefix for its
 * custom RPC methods. For example, to mine a block:
 * <ul>
 *   <li>Anvil: {@code anvil_mine}</li>
 *   <li>Hardhat: {@code hardhat_mine}</li>
 *   <li>Ganache: {@code evm_mine}</li>
 * </ul>
 * <p>
 * <strong>Usage:</strong>
 * <pre>{@code
 * TestNodeMode mode = TestNodeMode.ANVIL;
 * String method = mode.prefix() + "mine"; // "anvil_mine"
 * }</pre>
 *
 * @since 0.1.0-alpha
 */
public enum TestNodeMode {

    /** Foundry's Anvil test node. */
    ANVIL("anvil_"),

    /** Hardhat Network test node. */
    HARDHAT("hardhat_"),

    /** Ganache test node. */
    GANACHE("evm_");

    private final String prefix;

    TestNodeMode(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Returns the RPC method prefix for this test node.
     *
     * @return the method prefix (e.g., "anvil_", "hardhat_", "evm_")
     */
    public String prefix() {
        return prefix;
    }
}
