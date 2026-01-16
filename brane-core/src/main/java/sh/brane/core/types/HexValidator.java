// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.types;

import java.util.regex.Pattern;

/**
 * Utility for validating fixed-length hex strings.
 * <p>
 * Provides compiled regex patterns for validating hex-encoded data with a
 * specific byte length. Used by {@link Address} and {@link Hash} to ensure
 * consistent validation logic.
 *
 * @since 0.2.0
 */
public final class HexValidator {
    private HexValidator() {}

    /**
     * Creates a compiled pattern that matches hex strings of exactly the specified byte length.
     * <p>
     * The pattern requires:
     * <ul>
     *   <li>"0x" prefix</li>
     *   <li>Exactly {@code byteLength * 2} hex characters (0-9, a-f, A-F)</li>
     * </ul>
     *
     * @param byteLength the exact number of bytes the hex string must represent
     * @return a compiled pattern matching hex strings of the specified length
     */
    public static Pattern fixedLength(int byteLength) {
        int hexChars = byteLength * 2;
        return Pattern.compile("^0x[0-9a-fA-F]{" + hexChars + "}$");
    }
}
