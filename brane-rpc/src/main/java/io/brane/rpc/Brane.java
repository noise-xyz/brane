package io.brane.rpc;

import java.math.BigInteger;

import org.jspecify.annotations.Nullable;

import io.brane.core.model.BlockHeader;
import io.brane.core.types.Address;

/**
 * Unified entry point for interacting with Ethereum/EVM blockchains.
 *
 * <p>This sealed interface provides a type-safe hierarchy for blockchain clients:
 * <ul>
 *   <li>{@link Reader} - Read-only operations (queries, calls, subscriptions)</li>
 *   <li>{@link Signer} - Full operations including transaction signing and sending</li>
 * </ul>
 *
 * <p><strong>Usage with pattern matching:</strong>
 * <pre>{@code
 * Brane client = Brane.connect("https://eth.example.com");
 * switch (client) {
 *     case Brane.Reader r -> System.out.println("Read-only client");
 *     case Brane.Signer s -> System.out.println("Signing client");
 * }
 * }</pre>
 *
 * @since 0.1.0
 */
public sealed interface Brane extends AutoCloseable permits Brane.Reader, Brane.Signer {

    /**
     * Returns the chain ID of the connected network.
     *
     * @return the chain ID
     * @since 0.1.0
     */
    BigInteger chainId();

    /**
     * Retrieves the balance of an account.
     *
     * @param address the account address
     * @return the balance in Wei
     * @since 0.1.0
     */
    BigInteger getBalance(Address address);

    /**
     * Retrieves the latest block header.
     *
     * @return the latest block header, or {@code null} if not found
     * @since 0.1.0
     */
    @Nullable BlockHeader getLatestBlock();

    /**
     * Retrieves a block header by its number.
     *
     * @param blockNumber the block number
     * @return the block header, or {@code null} if not found
     * @since 0.1.0
     */
    @Nullable BlockHeader getBlockByNumber(long blockNumber);

    /**
     * Read-only client for blockchain queries.
     *
     * <p>Provides access to all read operations without transaction signing capability.
     *
     * @since 0.1.0
     */
    sealed interface Reader extends Brane permits DefaultReader {
    }

    /**
     * Full-featured client with transaction signing capability.
     *
     * <p>Extends all read operations with the ability to sign and send transactions.
     *
     * @since 0.1.0
     */
    sealed interface Signer extends Brane permits DefaultSigner {
    }
}
