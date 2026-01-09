package io.brane.core.crypto.hd;

/**
 * BIP-44 derivation path for Ethereum addresses.
 *
 * <p>
 * Represents the standard Ethereum derivation path: m/44'/60'/account'/0/addressIndex
 * where account and addressIndex are variable components.
 *
 * <p>
 * Example paths:
 * <ul>
 * <li>m/44'/60'/0'/0/0 - First address of first account</li>
 * <li>m/44'/60'/0'/0/1 - Second address of first account</li>
 * <li>m/44'/60'/1'/0/0 - First address of second account</li>
 * </ul>
 *
 * @param account      the account index (0 to MAX_INDEX)
 * @param addressIndex the address index within the account (0 to MAX_INDEX)
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki">BIP-44</a>
 */
public record DerivationPath(int account, int addressIndex) {

    /**
     * Maximum valid index value (2^31 - 1).
     * BIP-32 uses the high bit to indicate hardened derivation,
     * so unhardened indices must be in range [0, 2^31 - 1].
     */
    public static final int MAX_INDEX = 0x7FFFFFFF;

    /**
     * Constructs a derivation path with validation.
     *
     * @throws IllegalArgumentException if account or addressIndex is negative
     */
    public DerivationPath {
        if (account < 0) {
            throw new IllegalArgumentException("Account index cannot be negative: " + account);
        }
        if (addressIndex < 0) {
            throw new IllegalArgumentException("Address index cannot be negative: " + addressIndex);
        }
    }

    /**
     * Creates a derivation path with default account (0) and the specified address index.
     *
     * @param addressIndex the address index
     * @return a new DerivationPath
     * @throws IllegalArgumentException if addressIndex is negative or exceeds MAX_INDEX
     */
    public static DerivationPath of(int addressIndex) {
        return new DerivationPath(0, addressIndex);
    }

    /**
     * Creates a derivation path with the specified account and address index.
     *
     * @param account      the account index
     * @param addressIndex the address index
     * @return a new DerivationPath
     * @throws IllegalArgumentException if account or addressIndex is negative or exceeds MAX_INDEX
     */
    public static DerivationPath of(int account, int addressIndex) {
        return new DerivationPath(account, addressIndex);
    }

    /**
     * Parses a derivation path string in BIP-44 Ethereum format.
     *
     * <p>
     * Expected format: m/44'/60'/account'/0/addressIndex
     *
     * @param path the path string to parse
     * @return the parsed DerivationPath
     * @throws IllegalArgumentException if the path is null, empty, or not in the expected format
     */
    public static DerivationPath parse(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        String[] components = path.split("/");
        if (components.length != 6) {
            throw new IllegalArgumentException(
                    "Invalid path format. Expected m/44'/60'/account'/0/index, got: " + path);
        }

        if (!components[0].equals("m")) {
            throw new IllegalArgumentException("Path must start with 'm', got: " + path);
        }

        if (!isHardenedComponent(components[1], "44")) {
            throw new IllegalArgumentException(
                    "Path must have purpose 44' (BIP-44), got: " + components[1]);
        }

        if (!isHardenedComponent(components[2], "60")) {
            throw new IllegalArgumentException(
                    "Path must have coin type 60' (Ethereum), got: " + components[2]);
        }

        // Parse account (hardened)
        String accountStr = components[3];
        if (!accountStr.endsWith("'") && !accountStr.endsWith("h")) {
            throw new IllegalArgumentException(
                    "Account must be hardened (end with ' or h), got: " + accountStr);
        }
        int account = parseIndex(accountStr.substring(0, accountStr.length() - 1), "account");

        // Change must be 0 (external chain)
        if (!components[4].equals("0")) {
            throw new IllegalArgumentException(
                    "Change must be 0 (external chain), got: " + components[4]);
        }

        // Parse address index (non-hardened)
        int addressIndex = parseIndex(components[5], "address index");

        return new DerivationPath(account, addressIndex);
    }

    /**
     * Returns the full BIP-44 derivation path string.
     *
     * @return the path string in format m/44'/60'/account'/0/addressIndex
     */
    public String toPath() {
        return "m/44'/60'/" + account + "'/0/" + addressIndex;
    }

    private static int parseIndex(String str, String name) {
        long value;
        try {
            value = Long.parseLong(str);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + name + ": " + str);
        }
        if (value < 0) {
            throw new IllegalArgumentException(name + " cannot be negative: " + value);
        }
        if (value > MAX_INDEX) {
            throw new IllegalArgumentException(name + " exceeds maximum: " + value);
        }
        return (int) value;
    }

    private static boolean isHardenedComponent(String component, String expected) {
        return component.equals(expected + "'") || component.equals(expected + "h");
    }
}
