// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.builder;

import io.brane.core.error.TxnException;

/**
 * Runtime exception thrown when a transaction builder is in an invalid state.
 *
 * <p>
 * This exception extends {@link TxnException} to integrate with the Brane
 * exception hierarchy. Users catching {@link io.brane.core.error.BraneException}
 * will also catch builder validation errors.
 *
 * <p>
 * Thrown when:
 * <ul>
 * <li>Required fields are missing (e.g., no chainId specified)</li>
 * <li>Conflicting fields are set (e.g., both legacy gasPrice and EIP-1559 fees)</li>
 * <li>Invalid field values (e.g., negative gas limit)</li>
 * </ul>
 * @since 0.1.0-alpha
 */
public final class BraneTxBuilderException extends TxnException {

    public BraneTxBuilderException(final String message) {
        super(message);
    }

    public BraneTxBuilderException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
