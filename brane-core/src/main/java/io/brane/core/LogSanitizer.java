package io.brane.core;

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

    private LogSanitizer() {}

    public static String sanitize(final String input) {
        if (input == null) {
            return "null";
        }

        String sanitized = input;

        if (sanitized.contains("\"privateKey\"")) {
            sanitized =
                    sanitized.replaceAll(
                            "\\\"privateKey\\\"\\s*:\\s*\\\"0x[^\\\"]+\\\"",
                            "\"privateKey\":\"0x***[REDACTED]***\"");
        }

        if (sanitized.contains("\"raw\"")) {
            sanitized =
                    sanitized.replaceAll(
                            "\\\"raw\\\"\\s*:\\s*\\\"0x[^\\\"]+\\\"",
                            "\"raw\":\"0x***[REDACTED]***\"");
        }

        if (sanitized.length() > MAX_LOG_LENGTH) {
            int truncateAt = Math.max(0, MAX_LOG_LENGTH - TRUNCATION_SUFFIX.length());
            sanitized = sanitized.substring(0, truncateAt) + TRUNCATION_SUFFIX;
        }

        return sanitized;
    }
}
