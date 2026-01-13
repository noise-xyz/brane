// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.rpc;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents a snapshot ID returned by test node snapshot methods.
 * <p>
 * Test nodes like Anvil, Hardhat, and Ganache support state snapshots via
 * {@code evm_snapshot}/{@code anvil_snapshot}/{@code hardhat_snapshot}.
 * The returned ID can be used to revert the chain state to that snapshot.
 * <p>
 * <strong>Validation:</strong>
 * <ul>
 *   <li>Must not be null</li>
 *   <li>Must start with "0x" prefix</li>
 * </ul>
 * <p>
 * <strong>Usage:</strong>
 * <pre>{@code
 * // Create from hex string
 * SnapshotId snapshot = SnapshotId.from("0x1");
 *
 * // Revert using a tester client
 * snapshot.revertUsing(tester);
 * }</pre>
 *
 * @param value the hex-encoded snapshot ID (must start with "0x")
 * @since 0.2.0
 */
public record SnapshotId(@com.fasterxml.jackson.annotation.JsonValue String value) {

    private static final Pattern HEX_PREFIX = Pattern.compile("^0x[0-9a-fA-F]*$");

    /**
     * Creates a new SnapshotId with validation.
     *
     * @param value the hex-encoded snapshot ID
     * @throws NullPointerException if value is null
     * @throws IllegalArgumentException if value does not start with "0x"
     */
    public SnapshotId {
        Objects.requireNonNull(value, "snapshot ID must not be null");
        if (!HEX_PREFIX.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "snapshot ID must start with 0x and contain only hex characters: " + value);
        }
    }

    /**
     * Creates a SnapshotId from a hex string.
     *
     * @param hex the hex-encoded snapshot ID (must start with "0x")
     * @return a new SnapshotId
     * @throws NullPointerException if hex is null
     * @throws IllegalArgumentException if hex does not start with "0x"
     */
    public static SnapshotId from(String hex) {
        return new SnapshotId(hex);
    }

    /**
     * Reverts the chain state to this snapshot using the provided tester client.
     * <p>
     * This is a convenience method that calls the appropriate revert method
     * ({@code evm_revert}, {@code anvil_revert}, etc.) on the tester client.
     *
     * @param tester the tester client to use for reverting
     * @return true if the revert succeeded, false otherwise
     * @throws NullPointerException if tester is null
     */
    public boolean revertUsing(Brane.Tester tester) {
        Objects.requireNonNull(tester, "tester must not be null");
        return tester.revert(this);
    }
}
