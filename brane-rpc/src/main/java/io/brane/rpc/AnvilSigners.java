package io.brane.rpc;

import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.crypto.Signer;

/**
 * Utility class providing access to Anvil's 10 default funded test accounts.
 *
 * <p>These keys are derived from the well-known test mnemonic:
 * {@code "test test test test test test test test test test test junk"}
 * using derivation path {@code m/44'/60'/0'/0/i} where i is the account index.
 *
 * <p>Each account is pre-funded with 10,000 ETH when Anvil starts.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Get the default funded account (index 0)
 * Signer defaultSigner = AnvilSigners.defaultKey();
 *
 * // Get a specific account by index
 * Signer account3 = AnvilSigners.keyAt(3);
 *
 * // Iterate over all accounts
 * for (int i = 0; i < AnvilSigners.count(); i++) {
 *     Signer signer = AnvilSigners.keyAt(i);
 *     System.out.println("Account " + i + ": " + signer.address());
 * }
 * }</pre>
 *
 * <p><strong>Security Warning:</strong> These keys are publicly known and must
 * NEVER be used on mainnet or any network with real value. They are intended
 * solely for local development and testing with Anvil.
 *
 * @since 0.3.0
 */
public final class AnvilSigners {

    /**
     * The 10 default Anvil private keys in hex format.
     * Derived from mnemonic: "test test test test test test test test test test test junk"
     */
    private static final String[] PRIVATE_KEYS = {
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80", // Account 0
            "0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d", // Account 1
            "0x5de4111afa1a4b94908f83103eb1f1706367c2e68ca870fc3fb9a804cdab365a", // Account 2
            "0x7c852118294e51e653712a81e05800f419141751be58f605c371e15141b007a6", // Account 3
            "0x47e179ec197488593b187f80a00eb0da91f1b9d0b13f8733639f19c30a34926a", // Account 4
            "0x8b3a350cf5c34c9194ca85829a2df0ec3153be0318b5e2d3348e872092edffba", // Account 5
            "0x92db14e403b83dfe3df233f83dfa3a0d7096f21ca9b0d6d6b8d88b2b4ec1564e", // Account 6
            "0x4bbbf85ce3377467afe5d46f804f221813b2bb87f24d81f60f1fcdbf7cbf4356", // Account 7
            "0xdbda1821b80551c9d65939329250298aa3472ba22feea921c0cf5d620ea67b97", // Account 8
            "0x2a871d0798f97d79848a013d4936a73bf4cc922c825d33c1cf7073dff6d409c6"  // Account 9
    };

    private AnvilSigners() {
        // Utility class - prevent instantiation
    }

    /**
     * Returns the default Anvil test key (account index 0).
     *
     * <p>This is the most commonly used test account, equivalent to {@code keyAt(0)}.
     *
     * @return a new signer instance for the default Anvil account
     */
    public static Signer defaultKey() {
        return keyAt(0);
    }

    /**
     * Returns the Anvil test key at the specified index.
     *
     * @param index account index (0-9)
     * @return a new signer instance for the specified Anvil account
     * @throws IndexOutOfBoundsException if index is not in range [0, 9]
     */
    public static Signer keyAt(int index) {
        if (index < 0 || index >= PRIVATE_KEYS.length) {
            throw new IndexOutOfBoundsException(
                    "Anvil account index must be 0-9, got: " + index);
        }
        return new PrivateKeySigner(PRIVATE_KEYS[index]);
    }

    /**
     * Returns the number of default Anvil accounts.
     *
     * @return 10 (the number of default Anvil accounts)
     */
    public static int count() {
        return PRIVATE_KEYS.length;
    }
}
