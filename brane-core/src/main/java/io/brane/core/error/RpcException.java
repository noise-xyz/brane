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
}
