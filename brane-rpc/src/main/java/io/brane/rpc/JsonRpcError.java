package io.brane.rpc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * JSON-RPC error object as defined in the JSON-RPC 2.0 specification.
 *
 * <p>
 * <strong>Note on error codes:</strong> The JSON-RPC specification defines error codes
 * as integers in the range -32768 to -32000 for predefined errors, with application-specific
 * codes outside this range. While the specification theoretically allows any JSON number,
 * all standard Ethereum JSON-RPC error codes fit well within Java's {@code int} range
 * (-2,147,483,648 to 2,147,483,647). This implementation uses {@code int} for compatibility
 * with common usage patterns and existing codebases.
 *
 * @param code    the error code (negative values indicate server/protocol errors)
 * @param message a human-readable error message
 * @param data    optional additional error data (may be {@code null})
 * @since 0.2.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcError(int code, String message, @Nullable Object data) {}
