package io.brane.rpc.exception;

import io.brane.core.error.RpcException;

/**
 * Exception thrown when {@code eth_simulateV1} is not supported by the RPC node.
 * <p>
 * This exception is a specialized {@link RpcException} that indicates the RPC node
 * does not support the {@code eth_simulateV1} JSON-RPC method. This method is
 * relatively new and not available on all Ethereum nodes.
 * <p>
 * This exception is thrown when the RPC node returns error code {@code -32601}
 * ("Method not found") in response to an {@code eth_simulateV1} call.
 * <p>
 * <strong>Compatible RPC Providers:</strong>
 * <ul>
 *   <li>Recent versions of Geth</li>
 *   <li>Recent versions of Erigon</li>
 *   <li>Some managed RPC providers (check provider documentation)</li>
 * </ul>
 * <p>
 * <strong>Example Usage:</strong>
 * <pre>{@code
 * try {
 *     SimulateResult result = publicClient.simulateCalls(request);
 * } catch (SimulateNotSupportedException e) {
 *     // Handle unsupported method - perhaps fall back to eth_call
 *     logger.warn("eth_simulateV1 not supported by this node: {}", e.getMessage());
 * } catch (RpcException e) {
 *     // Handle other RPC errors
 * }
 * }</pre>
 *
 * @see RpcException
 * @see <a href="https://github.com/ethereum/execution-apis/pull/484">eth_simulateV1 Specification</a>
 */
public final class SimulateNotSupportedException extends RpcException {

    private static final int METHOD_NOT_FOUND = -32601;

    /**
     * Creates a new {@code SimulateNotSupportedException} with the original error message
     * from the RPC node.
     * <p>
     * The exception message will be augmented with helpful guidance about
     * {@code eth_simulateV1} support requirements.
     *
     * @param nodeMessage the original error message from the RPC node
     */
    public SimulateNotSupportedException(final String nodeMessage) {
        super(
                METHOD_NOT_FOUND,
                buildHelpfulMessage(nodeMessage),
                null, // No additional data for method not found errors
                (Long) null, // No request ID
                null // No cause
        );
    }

    /**
     * Builds a helpful error message that includes guidance for the developer.
     *
     * @param nodeMessage the original error message from the node
     * @return a comprehensive error message
     */
    private static String buildHelpfulMessage(final String nodeMessage) {
        String originalError = (nodeMessage != null && !nodeMessage.isBlank())
                ? nodeMessage
                : "Method not found";

        return String.format(
                "eth_simulateV1 is not supported by this RPC node. " +
                        "This method requires a node that supports the eth_simulateV1 JSON-RPC method " +
                        "(e.g., recent versions of Geth, Erigon, or compatible RPC providers). " +
                        "Original error: %s",
                originalError
        );
    }
}
