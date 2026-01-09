package io.brane.core.crypto.hd;

import java.security.SecureRandom;
import java.util.Objects;

import javax.security.auth.Destroyable;

import io.brane.core.crypto.PrivateKey;
import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.crypto.Signer;

/**
 * BIP-39/BIP-44 hierarchical deterministic (HD) wallet for Ethereum.
 *
 * <p>
 * This class provides a secure, standards-compliant implementation of HD wallets
 * following BIP-39 for mnemonic phrases and BIP-44 for Ethereum key derivation.
 *
 * <h2>Security Considerations</h2>
 *
 * <ul>
 *   <li><b>Mnemonic phrases are highly sensitive.</b> The phrase returned by {@link #phrase()}
 *       provides complete access to all derived keys. Store securely and never log or transmit
 *       over insecure channels.</li>
 *   <li><b>Passphrase adds security layer.</b> Using a passphrase with {@link #fromPhrase(String, String)}
 *       provides plausible deniability and protection against mnemonic theft. However, a forgotten
 *       passphrase means permanent loss of access.</li>
 *   <li><b>Derived keys inherit wallet security.</b> All {@link Signer} instances returned by
 *       {@link #derive(int)} are backed by private keys derived from the mnemonic. Compromise of
 *       the mnemonic compromises all derived keys.</li>
 *   <li><b>No key material in logs.</b> The {@link #toString()} method never includes sensitive
 *       data. Debug output is safe to log.</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <pre>{@code
 * // Generate a new wallet with 12-word phrase
 * MnemonicWallet wallet = MnemonicWallet.generatePhrase();
 * String phrase = wallet.phrase(); // Save this securely!
 *
 * // Restore wallet from existing phrase
 * MnemonicWallet restored = MnemonicWallet.fromPhrase(phrase);
 *
 * // Derive signers for different addresses
 * Signer account0 = wallet.derive(0); // m/44'/60'/0'/0/0
 * Signer account1 = wallet.derive(1); // m/44'/60'/0'/0/1
 *
 * // Use custom derivation path
 * Signer custom = wallet.derive(DerivationPath.of(1, 5)); // m/44'/60'/1'/0/5
 * }</pre>
 *
 * @see Bip39
 * @see DerivationPath
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP-39</a>
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki">BIP-44</a>
 */
public final class MnemonicWallet implements Destroyable {

    private static final int DEFAULT_WORD_COUNT = 12;

    private volatile boolean destroyed = false;

    private final String phrase;
    private final Bip32.ExtendedKey masterKey;

    private MnemonicWallet(String phrase, String passphrase) {
        this.phrase = phrase;
        byte[] seed = Bip39.toSeed(phrase, passphrase);
        try {
            this.masterKey = Bip32.masterKey(seed);
        } finally {
            java.util.Arrays.fill(seed, (byte) 0);
        }
    }

    /**
     * Validates a BIP-39 mnemonic phrase.
     *
     * <p>
     * A valid phrase must have 12, 15, 18, 21, or 24 words from the BIP-39
     * English wordlist with a valid checksum.
     *
     * @param phrase the phrase to validate
     * @return {@code true} if valid, {@code false} otherwise
     */
    public static boolean isValidPhrase(String phrase) {
        return Bip39.isValid(phrase);
    }

    /**
     * Generates a new wallet with a 12-word mnemonic phrase.
     *
     * <p>
     * Uses {@link SecureRandom} for cryptographically secure randomness.
     *
     * @return a new wallet with a freshly generated 12-word phrase
     */
    public static MnemonicWallet generatePhrase() {
        return generatePhrase(DEFAULT_WORD_COUNT);
    }

    /**
     * Generates a new wallet with the specified word count.
     *
     * <p>
     * Uses {@link SecureRandom} for cryptographically secure randomness.
     *
     * @param wordCount number of words (12, 15, 18, 21, or 24)
     * @return a new wallet with a freshly generated phrase
     * @throws IllegalArgumentException if wordCount is not valid
     */
    public static MnemonicWallet generatePhrase(int wordCount) {
        var random = new SecureRandom();
        String phrase = Bip39.generate(wordCount, random);
        return new MnemonicWallet(phrase, "");
    }

    /**
     * Creates a wallet from an existing mnemonic phrase without a passphrase.
     *
     * @param phrase the BIP-39 mnemonic phrase
     * @return a wallet backed by the given phrase
     * @throws IllegalArgumentException if the phrase is invalid
     * @throws NullPointerException     if phrase is null
     */
    public static MnemonicWallet fromPhrase(String phrase) {
        return fromPhrase(phrase, "");
    }

    /**
     * Creates a wallet from an existing mnemonic phrase with a passphrase.
     *
     * <p>
     * The passphrase provides an additional security layer. Different passphrases
     * will derive completely different keys from the same mnemonic, enabling
     * plausible deniability.
     *
     * @param phrase     the BIP-39 mnemonic phrase
     * @param passphrase the passphrase (empty string for no passphrase)
     * @return a wallet backed by the given phrase and passphrase
     * @throws IllegalArgumentException if the phrase is invalid
     * @throws NullPointerException     if phrase or passphrase is null
     */
    public static MnemonicWallet fromPhrase(String phrase, String passphrase) {
        Objects.requireNonNull(phrase, "phrase cannot be null");
        Objects.requireNonNull(passphrase, "passphrase cannot be null");

        if (!Bip39.isValid(phrase)) {
            throw new IllegalArgumentException("Invalid mnemonic phrase");
        }

        return new MnemonicWallet(phrase, passphrase);
    }

    /**
     * Derives a signer at the specified address index using account 0.
     *
     * <p>
     * Uses the standard BIP-44 Ethereum path: m/44'/60'/0'/0/{addressIndex}
     *
     * @param addressIndex the address index (0 to {@link DerivationPath#MAX_INDEX})
     * @return a signer for the derived address
     * @throws IllegalArgumentException if addressIndex is negative or exceeds maximum
     * @throws IllegalStateException    if the wallet has been destroyed
     */
    public Signer derive(int addressIndex) {
        if (destroyed) {
            throw new IllegalStateException("MnemonicWallet has been destroyed");
        }
        return derive(DerivationPath.of(addressIndex));
    }

    /**
     * Derives a signer at the specified derivation path.
     *
     * @param path the full derivation path
     * @return a signer for the derived address
     * @throws NullPointerException  if path is null
     * @throws IllegalStateException if the wallet has been destroyed
     */
    public Signer derive(DerivationPath path) {
        if (destroyed) {
            throw new IllegalStateException("MnemonicWallet has been destroyed");
        }
        Objects.requireNonNull(path, "path cannot be null");

        String fullPath = path.toPath();
        Bip32.ExtendedKey derivedKey = Bip32.derivePath(masterKey, fullPath);

        // Clone bytes since fromBytes() zeros its input for security
        PrivateKey privateKey = PrivateKey.fromBytes(derivedKey.keyBytes().clone());
        return PrivateKeySigner.fromPrivateKey(privateKey);
    }

    /**
     * Returns the mnemonic phrase for this wallet.
     *
     * <p>
     * <b>Security warning:</b> This phrase provides complete access to all derived keys.
     * Handle with extreme care - never log, display in UI without user consent, or
     * transmit over insecure channels.
     *
     * @return the BIP-39 mnemonic phrase
     */
    public String phrase() {
        return phrase;
    }

    /**
     * Returns a string representation of this wallet.
     *
     * <p>
     * For security, the mnemonic phrase is never included in the output.
     *
     * @return a safe string representation
     */
    @Override
    public String toString() {
        int wordCount = phrase.split("\\s+").length;
        return "MnemonicWallet[" + wordCount + " words]";
    }

    /**
     * Destroys the wallet by zeroing sensitive key material.
     *
     * <p>
     * This method zeros the master private key bytes and chain code. After calling
     * this method, the wallet can no longer derive keys and {@link #isDestroyed()}
     * will return {@code true}.
     *
     * <p>
     * <b>Note:</b> The mnemonic phrase cannot be zeroed due to Java String immutability.
     * For maximum security, minimize the lifetime of MnemonicWallet instances and
     * ensure phrases are not retained elsewhere in memory.
     */
    @Override
    public void destroy() {
        java.util.Arrays.fill(masterKey.keyBytes(), (byte) 0);
        java.util.Arrays.fill(masterKey.chainCode(), (byte) 0);
        destroyed = true;
    }

    /**
     * Returns whether this wallet has been destroyed.
     *
     * @return {@code true} if {@link #destroy()} has been called, {@code false} otherwise
     */
    @Override
    public boolean isDestroyed() {
        return destroyed;
    }
}
