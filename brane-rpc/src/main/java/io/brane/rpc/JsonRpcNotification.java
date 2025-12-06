package io.brane.rpc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a JSON-RPC notification (a request without an ID).
 * Used for subscription events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcNotification(String jsonrpc, String method, Object params) {
}
