package io.brane.rpc;

/**
 * Represents block identifiers for Ethereum JSON-RPC calls.
 * <p>
 * Block tags can be either named tags (like "latest", "pending", "earliest")
 * or specific block numbers.
 * <p>
 * <strong>Usage:</strong>
 * <pre>{@code
 * // Use named tags
 * BlockTag latest = BlockTag.LATEST;
 * BlockTag pending = BlockTag.PENDING;
 *
 * // Use specific block number
 * BlockTag block = BlockTag.of(12345678L);
 *
 * // Convert to RPC string format
 * String rpcValue = latest.toRpcValue(); // "latest"
 * String rpcValue2 = block.toRpcValue(); // "0xbc614e"
 * }</pre>
 *
 * @since 0.1.0-alpha
 */
public sealed interface BlockTag permits BlockTag.Named, BlockTag.Number {

    /** The latest mined block. */
    BlockTag LATEST = new Named("latest");

    /** The pending state/transactions. */
    BlockTag PENDING = new Named("pending");

    /** The earliest/genesis block. */
    BlockTag EARLIEST = new Named("earliest");

    /** The safe head block (post-merge). */
    BlockTag SAFE = new Named("safe");

    /** The finalized block (post-merge). */
    BlockTag FINALIZED = new Named("finalized");

    /**
     * Creates a block tag for a specific block number.
     *
     * @param blockNumber the block number
     * @return a BlockTag representing the specific block
     */
    static BlockTag of(long blockNumber) {
        return new Number(blockNumber);
    }

    /**
     * Converts this block tag to its RPC string representation.
     * <p>
     * Named tags return their name (e.g., "latest"), while block numbers
     * are returned as hex strings (e.g., "0x1234").
     *
     * @return the RPC-compatible string representation
     */
    String toRpcValue();

    /**
     * Named block tag (e.g., "latest", "pending", "earliest").
     */
    record Named(String name) implements BlockTag {
        public Named {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Block tag name cannot be null or blank");
            }
        }

        @Override
        public String toRpcValue() {
            return name;
        }
    }

    /**
     * Block number tag.
     */
    record Number(long blockNumber) implements BlockTag {
        public Number {
            if (blockNumber < 0) {
                throw new IllegalArgumentException("Block number cannot be negative: " + blockNumber);
            }
        }

        @Override
        public String toRpcValue() {
            return "0x" + Long.toHexString(blockNumber);
        }
    }
}
