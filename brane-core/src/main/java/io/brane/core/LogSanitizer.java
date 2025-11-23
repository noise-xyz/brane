package io.brane.core;

/**
 * Utility that removes sensitive data from debug log payloads.
 */
public final class LogSanitizer {

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

        if (sanitized.length() > 2000) {
            sanitized = sanitized.substring(0, 200) + "...(truncated)";
        }

        return sanitized;
    }
}
