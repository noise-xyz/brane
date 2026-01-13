// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core;

import java.util.regex.Pattern;

/**
 * Utility that removes sensitive data from debug log payloads.
 *
 * <p>
 * Performs two sanitization operations:
 * <ul>
 * <li>Redacts private key values to prevent credential leakage</li>
 * <li>Truncates excessively long logs to prevent memory issues</li>
 * </ul>
 */
public final class LogSanitizer {

    /**
     * Maximum length for sanitized log output. Logs exceeding this will be truncated.
     * Set to a reasonable size that captures enough context without overwhelming logs.
     */
    private static final int MAX_LOG_LENGTH = 2000;

    /** Suffix appended to truncated logs. */
    private static final String TRUNCATION_SUFFIX = "...(truncated)";

    /** Precompiled pattern for matching "privateKey":"0x..." JSON values. */
    private static final Pattern PRIVATE_KEY_PATTERN =
            Pattern.compile("\"privateKey\"\\s*:\\s*\"0x[^\"]+\"");

    /** Replacement string for redacted private keys. */
    private static final String PRIVATE_KEY_REPLACEMENT = "\"privateKey\":\"0x***[REDACTED]***\"";

    /** Precompiled pattern for matching "raw":"0x..." JSON values (often contains signed tx). */
    private static final Pattern RAW_PATTERN =
            Pattern.compile("\"raw\"\\s*:\\s*\"0x[^\"]+\"");

    /** Replacement string for redacted raw data. */
    private static final String RAW_REPLACEMENT = "\"raw\":\"0x***[REDACTED]***\"";

    private LogSanitizer() {}

    public static String sanitize(final String input) {
        if (input == null) {
            return "null";
        }

        String sanitized = input;

        if (sanitized.contains("\"privateKey\"")) {
            sanitized = PRIVATE_KEY_PATTERN.matcher(sanitized).replaceAll(PRIVATE_KEY_REPLACEMENT);
        }

        if (sanitized.contains("\"raw\"")) {
            sanitized = RAW_PATTERN.matcher(sanitized).replaceAll(RAW_REPLACEMENT);
        }

        if (sanitized.length() > MAX_LOG_LENGTH) {
            int truncateAt = Math.max(0, MAX_LOG_LENGTH - TRUNCATION_SUFFIX.length());
            sanitized = sanitized.substring(0, truncateAt) + TRUNCATION_SUFFIX;
        }

        return sanitized;
    }
}
