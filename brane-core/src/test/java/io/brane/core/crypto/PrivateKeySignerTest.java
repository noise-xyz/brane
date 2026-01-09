package io.brane.core.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.brane.core.tx.LegacyTransaction;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

class PrivateKeySignerTest {

    @Test
    void derivesAddressAndSigns() {
        PrivateKeySigner signer = new PrivateKeySigner(
                "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");

        Address address = signer.address();
        assertEquals("0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266", address.value());

        // Create a Legacy transaction for testing
        LegacyTransaction tx = new LegacyTransaction(
                0L, // nonce
                Wei.of(0), // gasPrice
                21_000L, // gasLimit
                new Address("0x0000000000000000000000000000000000000000"), // to
                Wei.of(0), // value
                HexData.EMPTY // data
        );

        Signature signed = signer.signTransaction(tx, 1L); // chainId = 1
        assertNotNull(signed);
        // Verify signature components are present
        assertNotNull(signed.r());
        assertNotNull(signed.s());
    }

    @Test
    void invalidKeyThrows() {
        assertThrows(IllegalArgumentException.class, () -> new PrivateKeySigner("not-a-key"));
    }

    @Test
    void signMessageProducesEthereumCompatibleV() {
        PrivateKeySigner signer = new PrivateKeySigner(
                "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");

        String message = "Hello World";
        Signature sig = signer.signMessage(message.getBytes());

        // EIP-191 expects v to be 27 or 28
        // 0 or 1 is not valid for personal_sign
        assertTrue(sig.v() == 27 || sig.v() == 28, "v should be 27 or 28, but was " + sig.v());
    }

    @Test
    void signMessageThrowsOnNullMessage() {
        // HIGH-2: Verify null message throws NullPointerException with clear message
        PrivateKeySigner signer = new PrivateKeySigner(
                "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> signer.signMessage(null));
        assertTrue(ex.getMessage().contains("message"));
    }

    @Test
    void destroyClearsKeyMaterial() {
        PrivateKeySigner signer = new PrivateKeySigner(
                "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");

        // Verify signer works before destroy
        org.junit.jupiter.api.Assertions.assertFalse(signer.isDestroyed());
        assertNotNull(signer.address());

        // Destroy the signer
        signer.destroy();

        // Verify signer is destroyed
        assertTrue(signer.isDestroyed());

        // Verify signing fails after destroy
        assertThrows(IllegalStateException.class, () ->
                signer.signMessage("test".getBytes()));
    }

    @Test
    void constructorFromPrivateKeyWorks() {
        // Package-private constructor for HD wallet derivation
        PrivateKey privateKey = PrivateKey.fromHex(
                "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");

        PrivateKeySigner signer = new PrivateKeySigner(privateKey);

        // Should derive the same address
        assertEquals("0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266", signer.address().value());

        // Should be able to sign
        Signature sig = signer.signMessage("test".getBytes());
        assertNotNull(sig);
        assertTrue(sig.v() == 27 || sig.v() == 28);
    }

    @Test
    void constructorFromPrivateKeyRejectsNull() {
        assertThrows(NullPointerException.class, () -> new PrivateKeySigner((PrivateKey) null));
    }
}
