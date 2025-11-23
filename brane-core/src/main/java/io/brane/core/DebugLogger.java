package io.brane.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized debug logger with built-in sanitization and global toggle.
 */
public final class DebugLogger {

    private static final Logger LOG = LoggerFactory.getLogger("io.brane.debug");

    private DebugLogger() {}

    public static void log(final String message, final Object... args) {
        if (!BraneDebug.isEnabled() || !LOG.isInfoEnabled()) {
            return;
        }

        final String formatted = (args == null || args.length == 0) ? message : message.formatted(args);
        LOG.info(LogSanitizer.sanitize(formatted));
    }
}
