package io.brane.core;

/**
 * Global toggle for enabling verbose debug logging across Brane modules.
 */
public final class BraneDebug {

    private static volatile boolean enabled = false;

    private BraneDebug() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(final boolean enabled) {
        BraneDebug.enabled = enabled;
    }
}
