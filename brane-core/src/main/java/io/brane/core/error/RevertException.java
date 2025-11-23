package io.brane.core.error;

/**
 * Exception describing an EVM revert.
 */
public final class RevertException extends BraneException {

    private final io.brane.core.RevertDecoder.RevertKind kind;
    private final String revertReason;
    private final String rawDataHex;

    public RevertException(
            final io.brane.core.RevertDecoder.RevertKind kind,
            final String revertReason,
            final String rawDataHex,
            final Throwable cause) {
        super(messageFor(kind, revertReason, rawDataHex), cause);
        this.kind = kind;
        this.revertReason = revertReason;
        this.rawDataHex = rawDataHex;
    }

    private static String messageFor(
            final io.brane.core.RevertDecoder.RevertKind kind,
            final String reason,
            final String raw) {
        final String kindSuffix = kind != null && kind != io.brane.core.RevertDecoder.RevertKind.UNKNOWN
                ? " [" + kind + "]"
                : "";
        return reason != null
                ? "EVM revert" + kindSuffix + ": " + reason
                : "EVM revert" + kindSuffix + " (no reason), rawData=" + raw;
    }

    public io.brane.core.RevertDecoder.RevertKind kind() {
        return kind;
    }

    public String revertReason() {
        return revertReason;
    }

    public String rawDataHex() {
        return rawDataHex;
    }
}
