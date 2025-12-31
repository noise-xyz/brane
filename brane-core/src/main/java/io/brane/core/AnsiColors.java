package io.brane.core;

/**
 * Modern, curated ANSI color palette for terminal output with automatic TTY
 * detection.
 * 
 * <p>
 * This class provides a sophisticated color scheme designed for modern terminal
 * logging,
 * moving away from traditional primary colors to a more refined palette. Colors
 * are automatically
 * disabled when not running in a TTY environment unless
 * {@code FORCE_COLOR=true} is set.
 * 
 * <h2>Color Philosophy</h2>
 * <ul>
 * <li><b>TEAL</b> - Success indicators (instead of traditional green)
 * <li><b>CORAL</b> - Error indicators (instead of traditional red)
 * <li><b>INDIGO</b> - Informational messages (instead of traditional blue)
 * <li><b>AMBER</b> - Warnings and gas-related operations (instead of yellow)
 * <li><b>LAVENDER</b> - Transaction operations
 * <li><b>SLATE</b> - Metadata and secondary information
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * // Manual coloring
 * System.out.println(AnsiColors.TEAL + "Success!" + AnsiColors.RESET);
 * 
 * // Using helper methods
 * System.out.println(AnsiColors.success("Operation completed"));
 * System.out.println(AnsiColors.error("Operation failed"));
 * }</pre>
 * 
 * <p>
 * All public constants in this class are safe to concatenate with strings for
 * terminal output. Colors automatically no-op when not in a TTY environment.
 * 
 * @since 0.1.0-alpha
 * @see LogFormatter
 */
public final class AnsiColors {

    private static final boolean IS_TTY = System.console() != null
            || "true".equals(System.getenv("FORCE_COLOR"));

    // Reset
    /** ANSI reset code - clears all formatting */
    public static final String RESET = ansi("0");

    // Modern curated palette (not your typical green/red/blue/yellow)
    /**
     * Modern teal color - used for success indicators (replaces traditional green)
     */
    public static final String TEAL = ansi("38;5;44");

    /** Soft coral color - used for error indicators (replaces traditional red) */
    public static final String CORAL = ansi("38;5;204");

    /**
     * Indigo color - used for informational messages (replaces traditional blue)
     */
    public static final String INDIGO = ansi("38;5;99");

    /**
     * Amber color - used for warnings and gas operations (replaces traditional
     * yellow)
     */
    public static final String AMBER = ansi("38;5;214");

    /** Sky blue color - used for accent elements like IDs */
    public static final String SKY = ansi("38;5;117");

    /** Balanced slate gray - used for metadata and secondary information */
    public static final String SLATE = ansi("38;5;247");

    /** Soft lavender color - used for transaction-related operations */
    public static final String LAVENDER = ansi("38;5;183");

    /** Fresh mint color - used for highlights */
    public static final String MINT = ansi("38;5;121");

    // Monochrome scale
    /** Dim text formatting */
    public static final String DIM = ansi("2");

    /** Bold text formatting */
    public static final String BOLD = ansi("1");

    private AnsiColors() {
    }

    private static String ansi(final String code) {
        return IS_TTY ? "\u001B[" + code + "m" : "";
    }

    // Helper methods for formatted output
    /**
     * Shortens a hash or address value for readable log display.
     *
     * <p>Values longer than 16 characters are shortened to show the first 10
     * characters (including "0x" prefix) followed by "..." and the last 4 characters.
     * For example, a 66-character hash becomes: {@code 0x12345678...abcd}
     *
     * @param value the hash or address value to format
     * @return the shortened value, or "null" if value is null
     */
    public static String hash(final String value) {
        if (value == null) {
            return "null";
        }
        // Shorten hashes longer than 16 chars: "0x12345678...abcd"
        if (value.length() > 16) {
            return value.substring(0, 10) + "..." + value.substring(value.length() - 4);
        }
        return value;
    }

    /**
     * Formats a duration in microseconds as a human-readable string.
     * 
     * @param micros duration in microseconds
     * @return formatted duration (e.g., "1.5ms" or "2.3s")
     */
    public static String duration(final long micros) {
        if (micros < 1000)
            return micros + "μs";
        if (micros < 1_000_000)
            return String.format("%.1fms", micros / 1000.0);
        return String.format("%.2fs", micros / 1_000_000.0);
    }

    /**
     * Converts a numeric value to string.
     * 
     * @param value the value to convert
     * @return string representation of the value
     */
    public static String num(final Object value) {
        return String.valueOf(value);
    }

    /**
     * Formats a key-value pair with colored key.
     * 
     * @param key   the key name
     * @param value the value
     * @return formatted "{@code key value}" with colored key
     */
    public static String kv(final String key, final String value) {
        return SLATE + key + RESET + " " + value;
    }

    /**
     * Formats a success message with a teal checkmark prefix.
     * 
     * @param message the success message
     * @return formatted message with "✓ {message}" in teal
     */
    public static String success(final String message) {
        return TEAL + "✓" + RESET + " " + message;
    }

    /**
     * Formats an error message with a coral cross prefix.
     * 
     * @param message the error message
     * @return formatted message with "✗ {message}" in coral
     */
    public static String error(final String message) {
        return CORAL + "✗" + RESET + " " + message;
    }
}
