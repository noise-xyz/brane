package io.brane.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized debug logger with modern colored formatting.
 */
public final class DebugLogger {

    private static final Logger LOG = LoggerFactory.getLogger("io.brane.debug");

    // Use modern colored output when enabled
    private static final boolean USE_COLORS = System.console() != null
            || "true".equals(System.getenv("FORCE_COLOR"));

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
     * Direct colored output to console (bypasses SLF4J for colors).
     */
    private static void logDirect(final String message, final Object... args) {
        final String formatted = (args == null || args.length == 0) ? message : message.formatted(args);

        // Use colored console output when available
        if (USE_COLORS) {
            System.out.println(formatted);
        } else {
            // Fall back to SLF4J for non-TTY environments
            LOG.info(LogSanitizer.sanitize(formatted));
        }
    }
}
