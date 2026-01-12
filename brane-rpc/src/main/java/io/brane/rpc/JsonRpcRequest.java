// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a JSON-RPC 2.0 request to an Ethereum node.
 * <p>
 * This record encapsulates the standard JSON-RPC request format used for
 * communicating with Ethereum nodes. It is serialized to JSON when sent
 * over HTTP or WebSocket connections.
 * <p>
 * <strong>Example:</strong>
 * <pre>{@code
 * var request = new JsonRpcRequest("2.0", "eth_chainId", List.of(), "1");
 * }</pre>
 *
 * @param jsonrpc the JSON-RPC version (always "2.0")
 * @param method the RPC method name (e.g., "eth_chainId", "eth_call")
 * @param params the method parameters as a list of objects
 * @param id the unique request identifier for matching responses
 * @since 0.1.0-alpha
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcRequest(String jsonrpc, String method, List<?> params, String id) {}
