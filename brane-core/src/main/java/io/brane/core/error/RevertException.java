// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.error;

import java.util.Objects;

/**
 * Exception thrown when an Ethereum Virtual Machine (EVM) transaction reverts.
 *
 * <p>
 * A revert occurs when a smart contract explicitly rejects a transaction using
 * {@code revert()}, {@code require()}, or {@code assert()} in Solidity, or when
 * the EVM encounters an error condition during execution.
 *
 * <p>
 * <strong>Revert Types
 * ({@link io.brane.core.RevertDecoder.RevertKind}):</strong>
 * <ul>
 * <li><strong>ERROR</strong>: Standard {@code Error(string)} revert with reason
 * string</li>
 * <li><strong>PANIC</strong>: Solidity {@code Panic(uint256)} - assertion
 * failures, division by zero, etc.</li>
 * <li><strong>CUSTOM</strong>: Custom error (Solidity custom errors)</li>
 * <li><strong>UNKNOWN</strong>: Unrecognized revert data format</li>
 * </ul>
 *
 * <p>
 * <strong>Usage:</strong>
 *
 * <pre>{@code
 * try {
 *     receipt = client.sendTransactionAndWait(request);
 * } catch (RevertException e) {
 *     System.err.println("Contract reverted: " + e.revertReason());
 *     System.err.println("Revert type: " + e.kind());
 *     System.err.println("Raw data: " + e.rawDataHex());
 * }
 * }</pre>
 *
 * @see io.brane.core.RevertDecoder
 * @since 0.1.0-alpha
 */
public final class RevertException extends BraneException {

    private final io.brane.core.RevertDecoder.RevertKind kind;
    private final String revertReason;
    private final String rawDataHex;

    /**
     * Creates a new RevertException with the specified details.
     *
     * @param kind        the type of revert (required)
     * @param revertReason the decoded revert reason, or {@code null} if unavailable
     * @param rawDataHex   the raw hex-encoded revert data, or {@code null} if unavailable
     * @param cause        the underlying cause, or {@code null} if none
     * @throws NullPointerException if kind is null
     */
    public RevertException(
            final io.brane.core.RevertDecoder.RevertKind kind,
            final String revertReason,
            final String rawDataHex,
            final Throwable cause) {
        super(messageFor(Objects.requireNonNull(kind, "kind cannot be null"), revertReason, rawDataHex), cause);
        this.kind = kind;
        this.revertReason = revertReason;
        this.rawDataHex = rawDataHex;
    }

    private static String messageFor(
            final io.brane.core.RevertDecoder.RevertKind kind,
            final String reason,
            final String raw) {
        // kind is guaranteed non-null by caller
        final String kindSuffix = kind != io.brane.core.RevertDecoder.RevertKind.UNKNOWN
                ? " [" + kind + "]"
                : "";
        return reason != null
                ? "EVM revert" + kindSuffix + ": " + reason
                : "EVM revert" + kindSuffix + " (no reason), rawData=" + raw;
    }

    public io.brane.core.RevertDecoder.RevertKind kind() {
        return kind;
    }

    public String revertReason() {
        return revertReason;
    }

    public String rawDataHex() {
        return rawDataHex;
    }
}
