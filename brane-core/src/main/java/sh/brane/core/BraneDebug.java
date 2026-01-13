// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core;

/**
 * Global toggle for enabling verbose debug logging across Brane modules.
 *
 * <p>Thread safety: The individual boolean fields are volatile, ensuring visibility
 * across threads. The compound check in {@link #isEnabled()} is not atomic, but this
 * is acceptable for best-effort logging purposes - a brief inconsistency between
 * flags has no correctness impact.
 */
public final class BraneDebug {

    private static volatile boolean rpcLogging = false;
    private static volatile boolean txLogging = false;

    private BraneDebug() {
    }

    /**
     * Checks if any debug logging is enabled.
     *
     * <p>Note: This check reads two volatile fields non-atomically, which is acceptable
     * for best-effort logging. The result may briefly reflect an inconsistent state
     * during concurrent flag updates, but this has no correctness impact.
     *
     * @return true if either RPC or transaction logging is enabled
     */
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
