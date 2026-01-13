// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core;

import static sh.brane.core.AnsiColors.*;

/**
 * Modern log formatter for Brane SDK with colored, structured output.
 *
 * <p>
 * This class provides formatted logging methods that produce consistent,
 * visually
 * clear output for RPC calls, transactions, and other SDK operations. All
 * formatters
 * use the modern color palette from {@link AnsiColors} and status symbols (✓ ✗
 * ○)
 * to indicate success, failure, and pending states.
 *
 * <h2>Design Philosophy</h2>
 * <ul>
 * <li><b>Status symbols only</b> - Symbols (✓ ✗ ○) indicate status, not
 * operation type
 * <li><b>Consistent bracketed format</b> - All logs use {@code [OPERATION]}
 * style
 * <li><b>Shortened hashes</b> - Long hex values truncated to
 * {@code 0x1234...5678} format
 * <li><b>Human-readable time</b> - Durations shown as {@code 1.5ms} or
 * {@code 10.0s}
 * <li><b>Multi-line for complexity</b> - Complex data (like CALL requests)
 * formatted across lines
 * </ul>
 *
 * <h2>Log Types</h2>
 * <table border="1">
 * <tr>
 * <th>Method</th>
 * <th>Format</th>
 * <th>Color</th>
 * <th>Use Case</th>
 * </tr>
 * <tr>
 * <td>formatCall</td>
 * <td>[CALL]</td>
 * <td>Indigo</td>
 * <td>RPC call requests</td>
 * </tr>
 * <tr>
 * <td>formatCallResult</td>
 * <td>✓ [CALL-RESULT]</td>
 * <td>Teal</td>
 * <td>Successful RPC responses</td>
 * </tr>
 * <tr>
 * <td>formatRpcError</td>
 * <td>✗ [RPC-ERROR]</td>
 * <td>Coral</td>
 * <td>RPC failures</td>
 * </tr>
 * <tr>
 * <td>formatEstimateGas</td>
 * <td>[ESTIMATE-GAS]</td>
 * <td>Amber</td>
 * <td>Gas estimation calls</td>
 * </tr>
 * <tr>
 * <td>formatTxSend</td>
 * <td>[TX-SEND]</td>
 * <td>Lavender</td>
 * <td>Transaction submissions</td>
 * </tr>
 * <tr>
 * <td>formatTxReceipt</td>
 * <td>✓/✗ [TX-RECEIPT]</td>
 * <td>Teal/Coral</td>
 * <td>Transaction confirmations</td>
 * </tr>
 * <tr>
 * <td>formatTxRevert</td>
 * <td>✗ [TX-REVERT]</td>
 * <td>Coral</td>
 * <td>Transaction reverts</td>
 * </tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // RPC call logging
 * DebugLogger.log(LogFormatter.formatCall("latest", callObject));
 * // Output: [CALL] tag=latest
 * // to=0x1234...5678
 * // data=0xabcd...ef01
 *
 * // Transaction logging
 * DebugLogger.logTx(LogFormatter.formatTxSend(from, to, nonce, gasLimit, value));
 * // Output: [TX-SEND] from=0x1234...5678 to=0xabcd...ef01 nonce=0
 * // gasLimit=25200 value=100
 *
 * // Error logging
 * DebugLogger.logRpc(LogFormatter.formatRpcError(method, code, message, duration));
 * // Output: ✗ [RPC-ERROR] method=eth_call code=-32000 message=execution
 * // reverted duration=1.5ms
 * }</pre>
 *
 * <p>
 * All methods in this class are thread-safe and purely functional (no side
 * effects).
 * The formatted strings are ready to be passed to any logging system.
 *
 * @since 0.1.0-alpha
 * @see AnsiColors
 * @see DebugLogger
 */
public final class LogFormatter {

    /**
     * Number of characters to show at the start of shortened hashes (includes "0x" prefix).
     * For example, with value 6: "0xabcd...ef12" shows "0xabcd".
     */
    private static final int HASH_PREFIX_LENGTH = 6;

    /**
     * Number of characters to show at the end of shortened hashes.
     * For example, with value 4: "0xabcd...ef12" shows "ef12".
     */
    private static final int HASH_SUFFIX_LENGTH = 4;

    /**
     * Minimum hash length before shortening is applied.
     * Hashes shorter than this are displayed in full.
     */
    private static final int HASH_SHORTEN_THRESHOLD = HASH_PREFIX_LENGTH + HASH_SUFFIX_LENGTH;

    /**
     * Indentation for multi-line log continuation.
     *
     * <p>This 10-space indent provides a consistent left margin for continuation
     * lines in multi-line logs, visually grouping related fields. For example:
     * <pre>
     * [CALL] tag=latest
     *           to=0x1234...
     *           data=0xabcd...
     * </pre>
     *
     * <p>The indent is chosen to roughly align with typical log prefix lengths
     * (e.g., "[CALL] " is 7 chars) while providing readable visual separation.
     */
    private static final String CONTINUATION_INDENT = "          ";

    private LogFormatter() {
    }

    /**
     * Format: [CALL] tag=latest
     * to=0x1234...5678
     * data=0xabcd...ef01
     */
    public static String formatCall(String tag, Object request) {
        String requestStr = String.valueOf(request);

        // Try to parse structured request and format on multiple lines
        if (requestStr.contains("to=") && requestStr.contains("data=")) {
            // Extract to and data values
            String toValue = extractValue(requestStr, "to=");
            String dataValue = extractValue(requestStr, "data=");

            return String.format(
                    "%s[CALL]%s tag=%s\n%sto=%s\n%sdata=%s",
                    INDIGO, RESET,
                    tag,
                    CONTINUATION_INDENT, toValue != null ? shortenHash(toValue) : "?",
                    CONTINUATION_INDENT, dataValue != null ? shortenHash(dataValue) : "?");
        }

        // Fallback: single line if request isn't structured
        return String.format(
                "%s[CALL]%s tag=%s request=%s",
                INDIGO, RESET,
                tag, requestStr);
    }

    /**
     * Extract value after a key in format "key=value"
     */
    private static String extractValue(String str, String key) {
        int startIdx = str.indexOf(key);
        if (startIdx == -1)
            return null;

        startIdx += key.length();
        int endIdx = str.indexOf(',', startIdx);
        if (endIdx == -1) {
            endIdx = str.indexOf('}', startIdx);
        }
        if (endIdx == -1) {
            endIdx = str.length();
        }

        return str.substring(startIdx, endIdx).trim();
    }

    /**
     * Format: ✓ [CALL-RESULT] tag=latest durationMicros=1005 (1.0ms)
     * result=0x1234...5678
     */
    public static String formatCallResult(String tag, long durationMicros, String result) {
        return String.format(
                "%s✓%s %s[CALL-RESULT]%s tag=%s %s result=%s",
                TEAL, RESET,
                TEAL, RESET,
                tag,
                duration(durationMicros),
                shortenHash(result));
    }

    /**
     * Format: ✗ [RPC-ERROR] method=eth_call code=-32000 message=error
     * duration=1.5ms
     */
    public static String formatRpcError(String method, Object code, String message, long durationMicros) {
        return String.format(
                "%s✗%s %s[RPC-ERROR]%s method=%s code=%s message=%s %s",
                CORAL, RESET,
                CORAL, RESET,
                method,
                code,
                CORAL + message + RESET,
                duration(durationMicros));
    }

    /**
     * Format: [RPC] method=eth_chainId duration=1.06ms
     */
    public static String formatRpc(String method, long durationMicros) {
        return String.format(
                "%s[RPC]%s method=%s %s",
                INDIGO, RESET,
                method,
                duration(durationMicros));
    }

    /**
     * Format: [ESTIMATE-GAS] from=0x1234...5678 to=0xabcd...ef01 data=0x1234...
     */
    public static String formatEstimateGas(String from, String to, String data) {
        return String.format(
                "%s[ESTIMATE-GAS]%s from=%s to=%s data=%s",
                AMBER, RESET,
                shortenHash(from), shortenHash(to), shortenHash(data));
    }

    /**
     * Format: ✓ [ESTIMATE-GAS-RESULT] durationMicros=982 (982μs) gas=0xcc66
     */
    public static String formatEstimateGasResult(long durationMicros, String gas) {
        return String.format(
                "%s✓%s %s[ESTIMATE-GAS-RESULT]%s %s gas=%s",
                TEAL, RESET,
                AMBER, RESET,
                duration(durationMicros),
                gas);
    }

    /**
     * Format: [TX-SEND] from=0x1234...5678 to=0xabcd...ef01 nonce=0
     * gasLimit=25200 value=0
     */
    public static String formatTxSend(String from, String to, Object nonce, Object gasLimit, Object value) {
        return String.format(
                "%s[TX-SEND]%s from=%s to=%s nonce=%s gasLimit=%s value=%s",
                LAVENDER, RESET,
                shortenHash(String.valueOf(from)),
                to != null ? shortenHash(String.valueOf(to)) : "null",
                nonce, gasLimit, value);
    }

    /**
     * Format: [TX-HASH] hash=0x1234...5678 durationMicros=934 (934μs)
     */
    public static String formatTxHash(String hash, long durationMicros) {
        return String.format(
                "%s[TX-HASH]%s hash=%s %s",
                LAVENDER, RESET,
                shortenHash(hash),
                duration(durationMicros));
    }

    /**
     * Format: ○ [TX-WAIT] hash=0x1234...5678 timeout=10s
     */
    public static String formatTxWait(String hash, long timeoutMillis) {
        String timeout = timeoutMillis < 1000
                ? timeoutMillis + "ms"
                : String.format("%.1fs", timeoutMillis / 1000.0);

        return String.format(
                "%s○%s %s[TX-WAIT]%s hash=%s timeout=%s",
                SLATE, RESET,
                SLATE, RESET,
                shortenHash(hash),
                timeout);
    }

    /**
     * Format: ✓ [TX-RECEIPT] hash=0x1234...5678 block=100 status=SUCCESS
     * or: ✗ [TX-RECEIPT] hash=0x1234...5678 block=101 status=FAILED
     */
    public static String formatTxReceipt(String hash, Object block, boolean status) {
        String emoji = status ? "✓" : "✗";
        String color = status ? TEAL : CORAL;
        String statusText = status ? "SUCCESS" : "FAILED";

        return String.format(
                "%s%s%s %s[TX-RECEIPT]%s hash=%s block=%s status=%s%s%s",
                color, emoji, RESET,
                color, RESET,
                shortenHash(hash),
                block,
                color, statusText, RESET);
    }

    /**
     * Format: ✗ [TX-REVERT] hash=0x1234...5678 kind=ERROR reason=insufficient funds
     */
    public static String formatTxRevert(String hash, String kind, String reason) {
        return String.format(
                "%s✗%s %s[TX-REVERT]%s hash=%s kind=%s reason=%s%s%s",
                CORAL, RESET,
                CORAL, RESET,
                hash != null ? shortenHash(hash) : "unknown",
                kind,
                CORAL, reason, RESET);
    }

    /**
     * Helper: format duration in microseconds as human-readable string
     */
    private static String duration(long micros) {
        double ms = micros / 1000.0;
        String formatted;
        if (ms < 1000) {
            formatted = String.format("%.2fms", ms);
        } else {
            formatted = String.format("%.2fs", ms / 1000.0);
        }
        return SLATE + "duration=" + formatted + RESET;
    }

    /**
     * Shortens a hash to a readable format: {@code 0xabcd...ef12}.
     *
     * <p>Hashes longer than {@value #HASH_SHORTEN_THRESHOLD} characters are shortened
     * to show the first {@value #HASH_PREFIX_LENGTH} characters (including "0x" prefix)
     * and the last {@value #HASH_SUFFIX_LENGTH} characters, separated by "...".
     *
     * @param fullHash the full hash string to shorten
     * @return the shortened hash, or the original if null or already short enough
     */
    private static String shortenHash(String fullHash) {
        if (fullHash == null || fullHash.length() <= HASH_SHORTEN_THRESHOLD) {
            return fullHash;
        }
        return fullHash.substring(0, HASH_PREFIX_LENGTH)
                + "..."
                + fullHash.substring(fullHash.length() - HASH_SUFFIX_LENGTH);
    }
}
