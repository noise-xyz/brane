package io.brane.core;

/**
 * Global toggle for enabling verbose debug logging across Brane modules.
 */
public final class BraneDebug {

    private static volatile boolean rpcLogging = false;
    private static volatile boolean txLogging = false;

    private BraneDebug() {
    }

    public static boolean isEnabled() {
        return rpcLogging || txLogging;
    }

    public static void setEnabled(final boolean enabled) {
        rpcLogging = enabled;
        txLogging = enabled;
    }

    public static void setRpcLogging(final boolean enabled) {
        rpcLogging = enabled;
    }

    public static boolean isRpcLoggingEnabled() {
        return rpcLogging;
    }

    public static void setTxLogging(final boolean enabled) {
        txLogging = enabled;
    }

    public static boolean isTxLoggingEnabled() {
        return txLogging;
    }
}
