package io.brane.core.error;

/**
 * Exception thrown when a JSON-RPC request to an Ethereum node fails.
 * 
 * <p>
 * This exception wraps JSON-RPC errors returned by the Ethereum node,
 * including both standard and node-specific error codes.
 * 
 * <p>
 * <strong>Common Standard Error Codes:</strong>
 * <ul>
 * <li><strong>-32700</strong>: Parse error (invalid JSON)</li>
 * <li><strong>-32600</strong>: Invalid JSON-RPC request</li>
 * <li><strong>-32601</strong>: Method not found</li>
 * <li><strong>-32602</strong>: Invalid method parameters</li>
 * <li><strong>-32603</strong>: Internal JSON-RPC error</li>
 * <li><strong>-32000 to -32099</strong>: Server/implementation-specific
 * errors</li>
 * </ul>
 * 
 * <p>
 * <strong>Common Ethereum Node Errors:</strong>
 * <ul>
 * <li><strong>-32000</strong>: Generic server error, insufficient funds, gas
 * too low</li>
 * <li><strong>-32001</strong>: Resource not found</li>
 * <li><strong>-32005</strong>: Request rate limit exceeded</li>
 * </ul>
 * 
 * <p>
 * The {@code data} field may contain additional error details, often including
 * revert data for failed transactions (use {@link RevertException} for typed
 * revert handling).
 * 
 * @see <a href="https://www.jsonrpc.org/specification#error_object">JSON-RPC
 *      Error Specification</a>
 */
public final class RpcException extends BraneException {

    private final int code;
    private final String data;
    private final Long requestId;

    public RpcException(
            final int code,
            final String message,
            final String data,
            final Long requestId,
            final Throwable cause) {
        super(augmentMessage(message, requestId), cause);
        this.code = code;
        this.data = data;
        this.requestId = requestId;
    }

    public RpcException(final int code, final String message, final String data, final Long requestId) {
        this(code, message, data, requestId, null);
    }

    public RpcException(final int code, final String message, final String data, final Throwable cause) {
        this(code, message, data, null, cause);
    }

    public int code() {
        return code;
    }

    public String data() {
        return data;
    }

    public Long requestId() {
        return requestId;
    }

    public boolean isBlockRangeTooLarge() {
        final String msg = getMessage();
        final String d = data;
        return (msg != null && msg.toLowerCase().contains("block range"))
                || (d != null && d.toLowerCase().contains("block range"));
    }

    public boolean isFilterNotFound() {
        final String msg = getMessage();
        final String d = data;
        return (msg != null && msg.toLowerCase().contains("filter not found"))
                || (d != null && d.toLowerCase().contains("filter not found"));
    }

    @Override
    public String toString() {
        return "RpcException{"
                + "code="
                + code
                + ", message="
                + getMessage()
                + ", data="
                + data
                + ", requestId="
                + requestId
                + "}";
    }

    private static String augmentMessage(final String message, final Long requestId) {
        if (requestId == null || message == null || message.isBlank()) {
            return message;
        }

        return "[requestId=" + requestId + "] " + message;
    }
}
