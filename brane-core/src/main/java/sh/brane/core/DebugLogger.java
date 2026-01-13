// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized debug logger with modern colored formatting.
 */
public final class DebugLogger {

    private static final Logger LOG = LoggerFactory.getLogger("sh.brane.debug");

    private DebugLogger() {
    }

    public static void logRpc(final String message, final Object... args) {
        if (!BraneDebug.isRpcLoggingEnabled()) {
            return;
        }
        logDirect(message, args);
    }

    public static void logTx(final String message, final Object... args) {
        if (!BraneDebug.isTxLoggingEnabled()) {
            return;
        }
        logDirect(message, args);
    }

    /**
     * Generic log method (respects global enabled check).
     */
    public static void log(final String message, final Object... args) {
        if (!BraneDebug.isEnabled()) {
            return;
        }
        logDirect(message, args);
    }

    /**
     * Direct output to stdout for colored logs in TTY environments.
     * Falls back to SLF4J for non-TTY environments.
     * CRITICAL: Always sanitizes logs to prevent credential leakage.
     */
    private static void logDirect(final String message, final Object... args) {
        final String formatted = (args == null || args.length == 0) ? message : message.formatted(args);

        // SECURITY: Always sanitize, even for colored output
        final String sanitized = LogSanitizer.sanitize(formatted);

        if (AnsiColors.IS_TTY) {
            System.out.println(sanitized);
        } else {
            // Fall back to SLF4J for non-TTY environments
            LOG.info(sanitized);
        }
    }
}
