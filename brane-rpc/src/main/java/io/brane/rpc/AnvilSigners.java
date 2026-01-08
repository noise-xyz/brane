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
 * <h2>Default Addresses</h2>
 * <p>The 10 pre-funded addresses (in order of index 0-9):
 * <ol start="0">
 *   <li>{@code 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266}</li>
 *   <li>{@code 0x70997970C51812dc3A010C7d01b50e0d17dc79C8}</li>
 *   <li>{@code 0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC}</li>
 *   <li>{@code 0x90F79bf6EB2c4f870365E785982E1f101E93b906}</li>
 *   <li>{@code 0x15d34AAf54267DB7D7c367839AAf71A00a2C6A65}</li>
 *   <li>{@code 0x9965507D1a55bcC2695C58ba16FB37d819B0A4dc}</li>
 *   <li>{@code 0x976EA74026E726554dB657fA54763abd0C3a0aa9}</li>
 *   <li>{@code 0x14dC79964da2C08b23698B3D3cc7Ca32193d9955}</li>
 *   <li>{@code 0x23618e81E3f5cdF7f54C3d65f7FBc0aBf5B21E8f}</li>
 *   <li>{@code 0xa0Ee7A142d267C1f36714E4a8F75612F20a79720}</li>
 * </ol>
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
 * <h2>SECURITY WARNING</h2>
 * <p><strong>These keys are publicly known and must NEVER be used on mainnet
 * or any network with real value.</strong> They are intended solely for local
 * development and testing with Anvil. Any funds sent to these addresses on
 * public networks can be stolen by anyone.
 *
 * <h2>Memory Management</h2>
 * <p>Each call to {@link #defaultKey()} or {@link #keyAt(int)} creates a new
 * {@link PrivateKeySigner} instance backed by a {@link io.brane.core.crypto.PrivateKey}.
 * While test keys are not sensitive, if you need to clear key material from memory,
 * the underlying {@code PrivateKey} implements {@link javax.security.auth.Destroyable}.
 * Note that the {@link Signer} interface does not expose the {@code destroy()} method
 * directly; cast to the underlying type if explicit cleanup is needed.
 *
 * @see Brane.Tester
 * @see io.brane.core.crypto.PrivateKey
 * @since 0.2.0
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
