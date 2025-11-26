package io.brane.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized debug logger with built-in sanitization and global toggle.
 */
public final class DebugLogger {

    private static final Logger LOG = LoggerFactory.getLogger("io.brane.debug");

    private DebugLogger() {
    }

    public static void logRpc(final String message, final Object... args) {
        if (!BraneDebug.isRpcLoggingEnabled() || !LOG.isInfoEnabled()) {
            return;
        }
        logInternal(message, args);
    }

    public static void logTx(final String message, final Object... args) {
        if (!BraneDebug.isTxLoggingEnabled() || !LOG.isInfoEnabled()) {
            return;
        }
        logInternal(message, args);
    }

    /**
     * Generic log method (respects global enabled check).
     */
    public static void log(final String message, final Object... args) {
        if (!BraneDebug.isEnabled() || !LOG.isInfoEnabled()) {
            return;
        }
        logInternal(message, args);
    }

    private static void logInternal(final String message, final Object... args) {
        final String formatted = (args == null || args.length == 0) ? message : message.formatted(args);
        LOG.info(LogSanitizer.sanitize(formatted));
    }
}
