package io.brane.core.error;

/**
 * Thrown when the connected chainId does not match the expected one.
 *
 * @since 0.1.0-alpha
 */
public final class ChainMismatchException extends TxnException {

    private final long expected;
    private final long actual;

    public ChainMismatchException(final long expected, final long actual) {
        super("Chain ID mismatch: expected " + expected + " but connected to " + actual);
        this.expected = expected;
        this.actual = actual;
    }

    public long expected() {
        return expected;
    }

    public long actual() {
        return actual;
    }
}
