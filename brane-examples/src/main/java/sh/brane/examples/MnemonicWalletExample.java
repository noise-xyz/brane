// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.examples;

import sh.brane.core.crypto.PrivateKeySigner;
import sh.brane.core.crypto.Signer;
import sh.brane.core.crypto.hd.DerivationPath;
import sh.brane.core.crypto.hd.MnemonicWallet;

/**
 * Example demonstrating BIP-39/BIP-44 HD wallet functionality with {@link MnemonicWallet}.
 *
 * <p>This example shows:
 * <ul>
 *   <li>Generating a new mnemonic phrase</li>
 *   <li>Creating a wallet from an existing phrase</li>
 *   <li>Deriving multiple addresses using different indices</li>
 *   <li>Using custom derivation paths for different accounts</li>
 *   <li>Proper lifecycle management with destroy()</li>
 * </ul>
 *
 * <p><strong>Security Note:</strong> The mnemonic phrase provides complete access to all
 * derived keys. Never log, display, or transmit phrases over insecure channels in production.
 *
 * <p>Usage:
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *     -PmainClass=sh.brane.examples.MnemonicWalletExample
 * </pre>
 *
 * @see MnemonicWallet
 * @see DerivationPath
 */
public final class MnemonicWalletExample {

    /** Anvil's default test mnemonic (DO NOT use in production!). */
    private static final String TEST_PHRASE =
            "test test test test test test test test test test test junk";

    private MnemonicWalletExample() {
        // Prevent instantiation
    }

    public static void main(final String[] args) {
        System.out.println("=== Brane HD Wallet (BIP-39/BIP-44) Example ===\n");

        // 1. Generate a new mnemonic phrase
        demonstrateGeneratePhrase();

        // 2. Restore wallet from existing phrase
        demonstrateRestoreFromPhrase();

        // 3. Derive multiple addresses
        demonstrateDeriveMultipleAddresses();

        // 4. Use custom derivation paths
        demonstrateCustomDerivationPaths();

        // 5. Proper lifecycle management
        demonstrateLifecycleManagement();

        System.out.println("\n=== All HD Wallet Examples Completed ===");
    }

    /**
     * Demonstrates generating a new mnemonic phrase.
     */
    private static void demonstrateGeneratePhrase() {
        System.out.println("[1] Generate New Mnemonic Phrase");

        // Generate a 12-word phrase (default)
        MnemonicWallet wallet12 = MnemonicWallet.generatePhrase();
        System.out.println("    12-word phrase generated");
        System.out.println("    Word count: " + wallet12.phrase().split(" ").length);
        System.out.println("    First address: " + wallet12.derive(0).address().value());

        // Generate a 24-word phrase (more entropy)
        MnemonicWallet wallet24 = MnemonicWallet.generatePhrase(24);
        System.out.println("\n    24-word phrase generated");
        System.out.println("    Word count: " + wallet24.phrase().split(" ").length);
        System.out.println("    First address: " + wallet24.derive(0).address().value());

        // Validate phrase format
        boolean isValid = MnemonicWallet.isValidPhrase(wallet12.phrase());
        System.out.println("\n    Phrase validation: " + (isValid ? "VALID" : "INVALID"));
        System.out.println();
    }

    /**
     * Demonstrates restoring a wallet from an existing phrase.
     */
    private static void demonstrateRestoreFromPhrase() {
        System.out.println("[2] Restore Wallet from Existing Phrase");

        // Restore without passphrase
        MnemonicWallet wallet = MnemonicWallet.fromPhrase(TEST_PHRASE);
        System.out.println("    Restored wallet from phrase");
        System.out.println("    Phrase word count: " + wallet.phrase().split(" ").length);

        Signer signer = wallet.derive(0);
        System.out.println("    Address[0]: " + signer.address().value());

        // Restore with passphrase (creates different keys from same phrase)
        MnemonicWallet walletWithPass = MnemonicWallet.fromPhrase(TEST_PHRASE, "my-secret-passphrase");
        Signer signerWithPass = walletWithPass.derive(0);
        System.out.println("\n    With passphrase - Address[0]: " + signerWithPass.address().value());
        System.out.println("    (Different address due to passphrase!)");
        System.out.println();
    }

    /**
     * Demonstrates deriving multiple addresses from the same wallet.
     */
    private static void demonstrateDeriveMultipleAddresses() {
        System.out.println("[3] Derive Multiple Addresses");

        MnemonicWallet wallet = MnemonicWallet.fromPhrase(TEST_PHRASE);

        System.out.println("    Deriving first 5 addresses (m/44'/60'/0'/0/i):");
        for (int i = 0; i < 5; i++) {
            Signer signer = wallet.derive(i);
            System.out.println("    [" + i + "] " + signer.address().value());
        }
        System.out.println();
    }

    /**
     * Demonstrates using custom derivation paths for different accounts.
     */
    private static void demonstrateCustomDerivationPaths() {
        System.out.println("[4] Custom Derivation Paths");

        MnemonicWallet wallet = MnemonicWallet.fromPhrase(TEST_PHRASE);

        // Default path: m/44'/60'/0'/0/0
        DerivationPath defaultPath = DerivationPath.of(0);
        Signer defaultSigner = wallet.derive(defaultPath);
        System.out.println("    Path: " + defaultPath.toPath());
        System.out.println("    Address: " + defaultSigner.address().value());

        // Different account: m/44'/60'/1'/0/0
        DerivationPath account1Path = new DerivationPath(1, 0);
        Signer account1Signer = wallet.derive(account1Path);
        System.out.println("\n    Path: " + account1Path.toPath());
        System.out.println("    Address: " + account1Signer.address().value());

        // Parse path from string
        DerivationPath parsedPath = DerivationPath.parse("m/44'/60'/2'/0/3");
        Signer parsedSigner = wallet.derive(parsedPath);
        System.out.println("\n    Parsed path: " + parsedPath.toPath());
        System.out.println("    Address: " + parsedSigner.address().value());
        System.out.println();
    }

    /**
     * Demonstrates proper lifecycle management with destroy().
     */
    private static void demonstrateLifecycleManagement() {
        System.out.println("[5] Lifecycle Management (destroy)");

        MnemonicWallet wallet = MnemonicWallet.fromPhrase(TEST_PHRASE);

        // Derive signers
        Signer signer0 = wallet.derive(0);
        Signer signer1 = wallet.derive(1);

        System.out.println("    Created signers for indices 0 and 1");
        System.out.println("    Signer[0] address: " + signer0.address().value());
        System.out.println("    Signer[1] address: " + signer1.address().value());

        // Cast to PrivateKeySigner to access destroy()
        PrivateKeySigner pks0 = (PrivateKeySigner) signer0;
        PrivateKeySigner pks1 = (PrivateKeySigner) signer1;

        // Check destroyed status
        System.out.println("\n    Before destroy:");
        System.out.println("    Signer[0] destroyed: " + pks0.isDestroyed());
        System.out.println("    Signer[1] destroyed: " + pks1.isDestroyed());

        // Destroy signer0
        pks0.destroy();
        System.out.println("\n    After destroying Signer[0]:");
        System.out.println("    Signer[0] destroyed: " + pks0.isDestroyed());
        System.out.println("    Signer[1] destroyed: " + pks1.isDestroyed());

        // Signer1 still works
        byte[] message = "Hello, HD Wallet!".getBytes();
        var signature = signer1.signMessage(message);
        System.out.println("\n    Signer[1] can still sign: " + (signature != null ? "YES" : "NO"));

        // Attempting to use destroyed signer throws exception
        System.out.println("    Attempting to use destroyed Signer[0]...");
        try {
            signer0.signMessage(message);
            System.out.println("    ERROR: Should have thrown!");
        } catch (IllegalStateException e) {
            System.out.println("    Correctly threw IllegalStateException: " + e.getMessage());
        }

        // Clean up signer1 when done
        pks1.destroy();
        System.out.println("\n    Cleaned up: Both signers destroyed");
        System.out.println("    Signer[0] destroyed: " + pks0.isDestroyed());
        System.out.println("    Signer[1] destroyed: " + pks1.isDestroyed());

        // Wallet can still derive new signers even after old ones are destroyed
        Signer newSigner = wallet.derive(0);
        System.out.println("\n    Derived new Signer[0]: " + newSigner.address().value());
        System.out.println("    (Wallet remains usable after derived signers are destroyed)");
    }
}
