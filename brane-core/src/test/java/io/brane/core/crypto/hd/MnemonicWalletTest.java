package io.brane.core.crypto.hd;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.brane.core.crypto.Signer;

/**
 * Tests for MnemonicWallet HD wallet functionality.
 */
class MnemonicWalletTest {

    // Standard BIP-39 test vector
    private static final String TEST_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";

    // Expected address for m/44'/60'/0'/0/0 from test mnemonic (no passphrase)
    // This is a well-known test vector address
    private static final String EXPECTED_ADDRESS_0 = "0x9858effd232b4033e47d90003d41ec34ecaeda94";

    @Test
    void testIsValidPhraseWithValidMnemonic() {
        assertTrue(MnemonicWallet.isValidPhrase(TEST_MNEMONIC));
    }

    @Test
    void testIsValidPhraseWithNull() {
        assertFalse(MnemonicWallet.isValidPhrase(null));
    }

    @Test
    void testIsValidPhraseWithInvalidMnemonic() {
        assertFalse(MnemonicWallet.isValidPhrase("invalid mnemonic phrase"));
    }

    @Test
    void testIsValidPhraseWithInvalidChecksum() {
        // Valid words but invalid checksum
        assertFalse(MnemonicWallet.isValidPhrase(
                "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon"));
    }

    @Test
    void testGeneratePhraseDefault() {
        MnemonicWallet wallet = MnemonicWallet.generatePhrase();

        assertNotNull(wallet);
        assertNotNull(wallet.phrase());
        assertEquals(12, wallet.phrase().split(" ").length);
        assertTrue(MnemonicWallet.isValidPhrase(wallet.phrase()));
    }

    @ParameterizedTest
    @ValueSource(ints = {12, 15, 18, 21, 24})
    void testGeneratePhraseWithWordCount(int wordCount) {
        MnemonicWallet wallet = MnemonicWallet.generatePhrase(wordCount);

        assertNotNull(wallet);
        assertEquals(wordCount, wallet.phrase().split(" ").length);
        assertTrue(MnemonicWallet.isValidPhrase(wallet.phrase()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 11, 13, 25})
    void testGeneratePhraseWithInvalidWordCount(int wordCount) {
        assertThrows(IllegalArgumentException.class, () -> MnemonicWallet.generatePhrase(wordCount));
    }

    @Test
    void testGeneratePhraseProducesDifferentPhrases() {
        MnemonicWallet wallet1 = MnemonicWallet.generatePhrase();
        MnemonicWallet wallet2 = MnemonicWallet.generatePhrase();

        assertNotEquals(wallet1.phrase(), wallet2.phrase());
    }

    @Test
    void testFromPhraseWithValidMnemonic() {
        MnemonicWallet wallet = MnemonicWallet.fromPhrase(TEST_MNEMONIC);

        assertNotNull(wallet);
        assertEquals(TEST_MNEMONIC, wallet.phrase());
    }

    @Test
    void testFromPhraseWithPassphrase() {
        MnemonicWallet wallet = MnemonicWallet.fromPhrase(TEST_MNEMONIC, "TREZOR");

        assertNotNull(wallet);
        assertEquals(TEST_MNEMONIC, wallet.phrase());

        // Different passphrase should produce different derived address
        MnemonicWallet walletNoPass = MnemonicWallet.fromPhrase(TEST_MNEMONIC, "");
        assertNotEquals(
                wallet.derive(0).address().value(),
                walletNoPass.derive(0).address().value());
    }

    @Test
    void testFromPhraseWithNullPhrase() {
        assertThrows(NullPointerException.class, () -> MnemonicWallet.fromPhrase(null));
    }

    @Test
    void testFromPhraseWithNullPassphrase() {
        assertThrows(NullPointerException.class, () -> MnemonicWallet.fromPhrase(TEST_MNEMONIC, null));
    }

    @Test
    void testFromPhraseWithInvalidMnemonic() {
        assertThrows(IllegalArgumentException.class, () -> MnemonicWallet.fromPhrase("invalid mnemonic"));
    }

    @Test
    void testDeriveByAddressIndex() {
        MnemonicWallet wallet = MnemonicWallet.fromPhrase(TEST_MNEMONIC);

        Signer signer0 = wallet.derive(0);
        Signer signer1 = wallet.derive(1);

        assertNotNull(signer0);
        assertNotNull(signer1);
        assertNotNull(signer0.address());
        assertNotNull(signer1.address());
        assertNotEquals(signer0.address(), signer1.address());
    }

    @Test
    void testDeriveByDerivationPath() {
        MnemonicWallet wallet = MnemonicWallet.fromPhrase(TEST_MNEMONIC);

        DerivationPath path = DerivationPath.of(0, 0);
        Signer signer = wallet.derive(path);

        assertNotNull(signer);
        assertNotNull(signer.address());

        // Should match the signer from derive(0)
        assertEquals(wallet.derive(0).address(), signer.address());
    }

    @Test
    void testDeriveByDerivationPathDifferentAccount() {
        MnemonicWallet wallet = MnemonicWallet.fromPhrase(TEST_MNEMONIC);

        // Account 0, address 0
        Signer signer0 = wallet.derive(DerivationPath.of(0, 0));
        // Account 1, address 0
        Signer signer1 = wallet.derive(DerivationPath.of(1, 0));

        assertNotEquals(signer0.address(), signer1.address());
    }

    @Test
    void testDeriveWithNullPath() {
        MnemonicWallet wallet = MnemonicWallet.fromPhrase(TEST_MNEMONIC);

        assertThrows(NullPointerException.class, () -> wallet.derive(null));
    }

    @Test
    void testDeriveWithNegativeIndex() {
        MnemonicWallet wallet = MnemonicWallet.fromPhrase(TEST_MNEMONIC);

        assertThrows(IllegalArgumentException.class, () -> wallet.derive(-1));
    }

    @Test
    void testDeriveProducesKnownAddress() {
        MnemonicWallet wallet = MnemonicWallet.fromPhrase(TEST_MNEMONIC);

        Signer signer = wallet.derive(0);
        String actualAddress = signer.address().value().toLowerCase();

        assertEquals(EXPECTED_ADDRESS_0, actualAddress);
    }

    @Test
    void testDerivedSignerCanSign() {
        MnemonicWallet wallet = MnemonicWallet.fromPhrase(TEST_MNEMONIC);
        Signer signer = wallet.derive(0);

        // Should be able to sign a message without exception
        byte[] message = "Hello, World!".getBytes();
        var signature = signer.signMessage(message);

        assertNotNull(signature);
        assertNotNull(signature.r());
        assertNotNull(signature.s());
        assertTrue(signature.v() == 27 || signature.v() == 28);
    }

    @Test
    void testDerivedSignerIsDeterministic() {
        MnemonicWallet wallet1 = MnemonicWallet.fromPhrase(TEST_MNEMONIC);
        MnemonicWallet wallet2 = MnemonicWallet.fromPhrase(TEST_MNEMONIC);

        Signer signer1 = wallet1.derive(5);
        Signer signer2 = wallet2.derive(5);

        assertEquals(signer1.address(), signer2.address());
    }

    @Test
    void testToStringDoesNotExposePhrase() {
        MnemonicWallet wallet = MnemonicWallet.fromPhrase(TEST_MNEMONIC);

        String str = wallet.toString();

        assertNotNull(str);
        assertFalse(str.contains("abandon"), "toString should not contain mnemonic words");
        assertTrue(str.contains("12"), "toString should indicate word count");
    }

    @Test
    void testToStringWithDifferentWordCounts() {
        MnemonicWallet wallet12 = MnemonicWallet.generatePhrase(12);
        MnemonicWallet wallet24 = MnemonicWallet.generatePhrase(24);

        assertTrue(wallet12.toString().contains("12"));
        assertTrue(wallet24.toString().contains("24"));
    }

    // ========== Anvil Compatibility Tests ==========

    // Anvil's default mnemonic - 12 words
    private static final String ANVIL_MNEMONIC =
            "test test test test test test test test test test test junk";

    // Expected addresses from Anvil for the test mnemonic (lowercase for comparison)
    // m/44'/60'/0'/0/0
    private static final String ANVIL_ADDRESS_0 = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";
    // m/44'/60'/0'/0/1
    private static final String ANVIL_ADDRESS_1 = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

    @Test
    void testAnvilMnemonicDeriveIndex0() {
        MnemonicWallet wallet = MnemonicWallet.fromPhrase(ANVIL_MNEMONIC);
        Signer signer = wallet.derive(0);

        // Address values are stored lowercase
        assertEquals(ANVIL_ADDRESS_0, signer.address().value());
    }

    @Test
    void testAnvilMnemonicDeriveIndex1() {
        MnemonicWallet wallet = MnemonicWallet.fromPhrase(ANVIL_MNEMONIC);
        Signer signer = wallet.derive(1);

        assertEquals(ANVIL_ADDRESS_1, signer.address().value());
    }

    @Test
    void testAnvilMnemonicIsValid() {
        assertTrue(MnemonicWallet.isValidPhrase(ANVIL_MNEMONIC));
    }

    // ========== Derive Independence Tests ==========

    @Test
    void testDeriveReturnsIndependentSigners() {
        MnemonicWallet wallet = MnemonicWallet.fromPhrase(ANVIL_MNEMONIC);

        Signer signer0a = wallet.derive(0);
        Signer signer0b = wallet.derive(0);

        // Both signers should have the same address
        assertEquals(signer0a.address(), signer0b.address());

        // But they should be different instances
        assertNotSame(signer0a, signer0b);

        // Each can sign independently
        byte[] message = "test".getBytes();
        var sig0a = signer0a.signMessage(message);
        var sig0b = signer0b.signMessage(message);

        // Same key should produce same signatures (deterministic signing)
        assertArrayEquals(sig0a.r(), sig0b.r());
        assertArrayEquals(sig0a.s(), sig0b.s());
        assertEquals(sig0a.v(), sig0b.v());
    }

    @Test
    void testMultipleDeriveCallsDoNotInterfere() {
        MnemonicWallet wallet = MnemonicWallet.fromPhrase(ANVIL_MNEMONIC);

        // Derive multiple signers
        Signer signer0 = wallet.derive(0);
        Signer signer1 = wallet.derive(1);
        Signer signer2 = wallet.derive(2);

        // Verify each has unique address
        assertNotEquals(signer0.address(), signer1.address());
        assertNotEquals(signer1.address(), signer2.address());
        assertNotEquals(signer0.address(), signer2.address());

        // Verify they match expected addresses
        assertEquals(ANVIL_ADDRESS_0, signer0.address().value());
        assertEquals(ANVIL_ADDRESS_1, signer1.address().value());
    }

    // ========== Signer Destroy Tests ==========

    @Test
    void testDerivedSignerCanBeDestroyed() {
        MnemonicWallet wallet = MnemonicWallet.fromPhrase(ANVIL_MNEMONIC);
        Signer signer = wallet.derive(0);

        // Signer should be a PrivateKeySigner which implements Destroyable
        assertInstanceOf(io.brane.core.crypto.PrivateKeySigner.class, signer);

        var privateKeySigner = (io.brane.core.crypto.PrivateKeySigner) signer;
        assertFalse(privateKeySigner.isDestroyed());

        privateKeySigner.destroy();
        assertTrue(privateKeySigner.isDestroyed());
    }

    @Test
    void testDestroyedSignerThrowsOnSign() {
        MnemonicWallet wallet = MnemonicWallet.fromPhrase(ANVIL_MNEMONIC);
        Signer signer = wallet.derive(0);

        var privateKeySigner = (io.brane.core.crypto.PrivateKeySigner) signer;
        privateKeySigner.destroy();

        // Signing after destroy should throw
        byte[] message = "test".getBytes();
        assertThrows(IllegalStateException.class, () -> signer.signMessage(message));
    }

    @Test
    void testDestroyingOneSignerDoesNotAffectOthers() {
        MnemonicWallet wallet = MnemonicWallet.fromPhrase(ANVIL_MNEMONIC);

        Signer signer0 = wallet.derive(0);
        Signer signer1 = wallet.derive(1);

        // Destroy signer0
        ((io.brane.core.crypto.PrivateKeySigner) signer0).destroy();

        // signer1 should still work
        byte[] message = "test".getBytes();
        var signature = signer1.signMessage(message);
        assertNotNull(signature);

        // And we can still derive new signers
        Signer signer0Again = wallet.derive(0);
        assertFalse(((io.brane.core.crypto.PrivateKeySigner) signer0Again).isDestroyed());
        var sig = signer0Again.signMessage(message);
        assertNotNull(sig);
    }
}
