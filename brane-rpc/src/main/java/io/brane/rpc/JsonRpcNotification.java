package io.brane.rpc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a JSON-RPC notification (a request without an ID).
 *
 * <p>Notifications are server-initiated messages sent over WebSocket connections,
 * typically for subscription events like new blocks or pending transactions.
 *
 * <p>Example notification for a {@code newHeads} subscription:
 * <pre>{@code
 * {
 *   "jsonrpc": "2.0",
 *   "method": "eth_subscription",
 *   "params": {
 *     "subscription": "0x1234...",
 *     "result": { "number": "0x123", ... }
 *   }
 * }
 * }</pre>
 *
 * @param jsonrpc the JSON-RPC version, typically "2.0"
 * @param method the notification method, e.g., "eth_subscription"
 * @param params the notification payload containing subscription ID and result data
 * @see WebSocketProvider#subscribe(String, java.util.List, java.util.function.Consumer)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcNotification(String jsonrpc, String method, Object params) {
}
