package io.brane.core.error;

/**
 * JSON-RPC level failure when interacting with an Ethereum node.
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
