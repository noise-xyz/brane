// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.error;

/**
 * Thrown when ABI inputs cannot be encoded.
 *
 * @since 0.1.0-alpha
 */
public final class AbiEncodingException extends BraneException {

    public AbiEncodingException(final String message) {
        super(message);
    }

    public AbiEncodingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
