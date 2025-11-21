package io.brane.core.error;

/**
 * JSON-RPC level failure when interacting with an Ethereum node.
 */
public final class RpcException extends BraneException {

    private final int code;
    private final String data;

    public RpcException(
            final int code, final String message, final String data, final Throwable cause) {
        super(message, cause);
        this.code = code;
        this.data = data;
    }

    public int code() {
        return code;
    }

    public String data() {
        return data;
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
        return "RpcException{code=" + code + ", message=" + getMessage() + ", data=" + data + "}";
    }
}
