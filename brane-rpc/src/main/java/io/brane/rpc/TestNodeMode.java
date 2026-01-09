package io.brane.rpc;

/**
 * Represents different test node implementations and their RPC method prefixes.
 *
 * <p>Each test node (Anvil, Hardhat, Ganache) uses a different prefix for its
 * custom RPC methods. For example, to mine a block:
 * <ul>
 *   <li>Anvil: {@code anvil_mine}</li>
 *   <li>Hardhat: {@code hardhat_mine}</li>
 *   <li>Ganache: {@code evm_mine}</li>
 * </ul>
 *
 * <p>This enum is used internally by {@link Brane.Tester} to construct the correct
 * RPC method names when interacting with test nodes. Users typically don't need to
 * use this enum directly unless implementing custom test node interactions.
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * TestNodeMode mode = TestNodeMode.ANVIL;
 * String method = mode.prefix() + "mine"; // "anvil_mine"
 * }</pre>
 *
 * @see Brane.Tester
 * @since 0.2.0
 */
public enum TestNodeMode {

    /**
     * Foundry's Anvil test node.
     *
     * <p>Anvil is a fast, local Ethereum development node from the Foundry toolkit.
     * It uses the {@code anvil_} prefix for its custom RPC methods.
     *
     * @see <a href="https://book.getfoundry.sh/anvil/">Anvil Documentation</a>
     */
    ANVIL("anvil_"),

    /**
     * Hardhat Network test node.
     *
     * <p>Hardhat Network is the default network for Hardhat, a popular Ethereum
     * development environment. It uses the {@code hardhat_} prefix for its custom
     * RPC methods.
     *
     * @see <a href="https://hardhat.org/hardhat-network/docs/overview">Hardhat Network Documentation</a>
     */
    HARDHAT("hardhat_"),

    /**
     * Ganache test node.
     *
     * <p>Ganache is a personal blockchain for Ethereum development from Truffle Suite.
     * It uses the {@code evm_} prefix for its custom RPC methods.
     *
     * @see <a href="https://trufflesuite.com/ganache/">Ganache Documentation</a>
     */
    GANACHE("evm_");

    private final String prefix;

    TestNodeMode(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Returns the RPC method prefix for this test node.
     *
     * <p>The prefix is used to construct full RPC method names by appending
     * the method name. For example, {@code ANVIL.prefix() + "mine"} yields
     * {@code "anvil_mine"}.
     *
     * @return the method prefix including the trailing underscore
     *         (e.g., {@code "anvil_"}, {@code "hardhat_"}, {@code "evm_"})
     */
    public String prefix() {
        return prefix;
    }
}
