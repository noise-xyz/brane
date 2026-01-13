// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.error;

/**
 * Base class for transaction-related failures (signing, validation, send).
 * <p>
 * <strong>Design Note:</strong> This class is intentionally {@code non-sealed} to allow
 * users to define custom transaction exception types for application-specific error handling.
 * While this breaks exhaustive pattern matching at the {@code TxnException} level, it provides
 * an extension point for diverse transaction failure scenarios that the SDK cannot anticipate.
 * <p>
 * The sealed hierarchy on {@link BraneException} still provides exhaustiveness guarantees
 * at the top level, ensuring all Brane-specific errors can be caught and handled appropriately.
 * <p>
 * <strong>SDK-provided subclasses:</strong>
 * <ul>
 * <li>{@link sh.brane.core.builder.BraneTxBuilderException} - Transaction building failures</li>
 * <li>{@link ChainMismatchException} - Chain ID mismatch errors</li>
 * <li>{@link InvalidSenderException} - Invalid sender address errors</li>
 * </ul>
 *
 * @since 0.1.0-alpha
 */
public non-sealed class TxnException extends BraneException {

    public TxnException(final String message) {
        super(message);
    }

    public TxnException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public boolean isInvalidSender() {
        final String msg = getMessage();
        return msg != null && msg.toLowerCase().contains("invalid sender");
    }

    public boolean isChainIdMismatch() {
        final String msg = getMessage();
        return msg != null && msg.toLowerCase().contains("chain id mismatch");
    }
}
