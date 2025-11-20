package io.brane.core.error;

/**
 * Exception describing an EVM revert.
 */
public final class RevertException extends BraneException {

    private final String revertReason;
    private final String rawDataHex;

    public RevertException(
            final String revertReason, final String rawDataHex, final Throwable cause) {
        super(messageFor(revertReason, rawDataHex), cause);
        this.revertReason = revertReason;
        this.rawDataHex = rawDataHex;
    }

    private static String messageFor(final String reason, final String raw) {
        return reason != null ? "EVM revert: " + reason : "EVM revert (no reason), rawData=" + raw;
    }

    public String revertReason() {
        return revertReason;
    }

    public String rawDataHex() {
        return rawDataHex;
    }
}
