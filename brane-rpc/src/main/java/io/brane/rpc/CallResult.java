// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.brane.core.model.LogEntry;
import io.brane.core.types.HexData;
import io.brane.rpc.internal.LogParser;
import io.brane.rpc.internal.RpcUtils;

/**
 * Represents the result of a single call in a transaction simulation.
 * <p>
 * This is a sealed interface with two implementations:
 * <ul>
 *   <li>{@link Success} - The call completed successfully</li>
 *   <li>{@link Failure} - The call failed (reverted or encountered an error)</li>
 * </ul>
 * <p>
 * Both success and failure cases include gas used and logs emitted up to the point of completion/failure.
 *
 * @see SimulateResult
 * @since 0.2.0
 */
public sealed interface CallResult {

    /**
     * Parses a call result from a JSON-RPC response map.
     *
     * @param map the raw map from the JSON-RPC response
     * @return a Success or Failure record
     */
    @SuppressWarnings("unchecked")
    static CallResult fromMap(Map<String, Object> map) {
        Object gasUsedRaw = map.get("gasUsed");
        if (gasUsedRaw == null) {
            throw new IllegalArgumentException("Malformed eth_simulateV1 response: missing required field 'gasUsed'");
        }
        BigInteger gasUsed = RpcUtils.decodeHexBigInteger(String.valueOf(gasUsedRaw));
        List<LogEntry> logs = LogParser.parseLogs(map.get("logs"));

        // Check for failure: either 'error' field is present, or 'status' field is 0.
        boolean isFailure = map.containsKey("error") && map.get("error") != null;
        if (!isFailure && map.containsKey("status")) {
            Object status = map.get("status");
            if (status instanceof Number n) {
                isFailure = n.intValue() == 0;
            } else if (status instanceof String s) {
                isFailure = RpcUtils.decodeHexBigInteger(s).intValue() == 0;
            }
        }

        if (isFailure) {
            Map<String, Object> error = (Map<String, Object>) map.get("error");
            Object rawMessage = error != null ? error.get("message") : null;
            String message = rawMessage != null ? String.valueOf(rawMessage) : "execution failed";
            String returnData = (String) map.get("returnData");
            return new Failure(gasUsed, logs, message, returnData != null ? new HexData(returnData) : null);
        } else {
            String returnData = (String) map.get("returnData");
            return new Success(gasUsed, logs, returnData != null ? new HexData(returnData) : null);
        }
    }

    /**
     * The gas consumed by this call.
     *
     * @return gas used
     */
    BigInteger gasUsed();

    /**
     * The logs emitted by this call.
     * For successful calls, these are all logs emitted.
     * For failed calls, these are logs emitted before the failure occurred.
     *
     * @return an immutable list of log entries
     */
    List<LogEntry> logs();

    /**
     * Represents a successful call result.
     *
     * @param gasUsed gas consumed by the call
     * @param logs logs emitted by the call
     * @param returnData the return data from the call (can be null or empty for state-changing functions)
     */
    record Success(
            BigInteger gasUsed,
            List<LogEntry> logs,
            @Nullable HexData returnData  // For view functions
    ) implements CallResult {

        /**
         * Compact constructor with validation and defensive copy.
         */
        public Success {
            Objects.requireNonNull(gasUsed, "gasUsed cannot be null");
            Objects.requireNonNull(logs, "logs cannot be null");
            logs = List.copyOf(logs);
        }
    }

    /**
     * Represents a failed call result (revert or execution error).
     *
     * @param gasUsed gas consumed before the call failed
     * @param logs logs emitted before the call failed
     * @param errorMessage human-readable error message from the error.message field in the JSON-RPC response
     * @param revertData the revert data from the returnData field (may be null or empty "0x")
     */
    record Failure(
            BigInteger gasUsed,
            List<LogEntry> logs,
            String errorMessage,           // From error.message in JSON response
            @Nullable HexData revertData   // From returnData in JSON response (revert data)
    ) implements CallResult {

        /**
         * Compact constructor with validation and defensive copy.
         */
        public Failure {
            Objects.requireNonNull(gasUsed, "gasUsed cannot be null");
            Objects.requireNonNull(logs, "logs cannot be null");
            Objects.requireNonNull(errorMessage, "errorMessage cannot be null");
            logs = List.copyOf(logs);
        }
    }
}
