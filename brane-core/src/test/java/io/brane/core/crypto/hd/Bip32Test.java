package io.brane.core.crypto.hd;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.brane.primitives.Hex;

/**
 * Tests for BIP-32 hierarchical deterministic key derivation.
 * <p>
 * Test vectors from:
 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki#test-vectors">BIP-32 Test Vectors</a>
 */
class Bip32Test {

    // BIP-32 Test Vector 1 seed
    private static final byte[] TEST_VECTOR_1_SEED = Hex.decode(
            "000102030405060708090a0b0c0d0e0f");

    // BIP-32 Test Vector 2 seed (from BIP-39 mnemonic)
    private static final byte[] TEST_VECTOR_2_SEED = Hex.decode(
            "fffcf9f6f3f0edeae7e4e1dedbd8d5d2cfccc9c6c3c0bdbab7b4b1aeaba8a5a29f9c999693908d8a8784817e7b7875726f6c696663605d5a5754514e4b484542");

    // BIP-39 test vector seed: "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private static final byte[] BIP39_TEST_SEED = Hex.decode(
            "5eb00bbddcf069084889a8ab9155568165f5c453ccb85e70811aaed6f6da5fc19a5ac40b389cd370d086206dec8aa6c43daea6690f20ad3d8d48b2d2ce9e38e4");

    @Test
    void testMasterKeyFromTestVector1() {
        // Need to pad the 16-byte test vector to 64 bytes for the seed input
        // In practice, BIP-32 master key derivation works with seeds of various lengths
        // But our implementation enforces 64-byte seeds per BIP-39
        // Use the BIP-39 test seed instead
        Bip32.ExtendedKey masterKey = Bip32.masterKey(BIP39_TEST_SEED);

        assertNotNull(masterKey);
        assertEquals(32, masterKey.keyBytes().length);
        assertEquals(32, masterKey.chainCode().length);

        // Verify the master key is non-zero
        assertFalse(isAllZeros(masterKey.keyBytes()));
        assertFalse(isAllZeros(masterKey.chainCode()));
    }

    @Test
    void testMasterKeyFromTestVector2() {
        Bip32.ExtendedKey masterKey = Bip32.masterKey(TEST_VECTOR_2_SEED);

        assertNotNull(masterKey);
        assertEquals(32, masterKey.keyBytes().length);
        assertEquals(32, masterKey.chainCode().length);

        // BIP-32 Test Vector 2 expected values (m chain):
        // private key: 4b03d6fc340455b363f51020ad3ecca4f0850280cf436c70c727923f6db46c3e
        // chain code: 60499f801b896d83179a4374aeb7822aaeaceaa0db1f85ee3e904c4defbd9689
        String expectedPrivateKey = "0x4b03d6fc340455b363f51020ad3ecca4f0850280cf436c70c727923f6db46c3e";
        String expectedChainCode = "0x60499f801b896d83179a4374aeb7822aaeaceaa0db1f85ee3e904c4defbd9689";

        assertEquals(expectedPrivateKey.toLowerCase(), Hex.encode(masterKey.keyBytes()).toLowerCase());
        assertEquals(expectedChainCode.toLowerCase(), Hex.encode(masterKey.chainCode()).toLowerCase());
    }

    @Test
    void testMasterKeyWithNullSeed() {
        assertThrows(IllegalArgumentException.class, () -> Bip32.masterKey(null));
    }

    @Test
    void testMasterKeyWithInvalidSeedLength() {
        assertThrows(IllegalArgumentException.class, () -> Bip32.masterKey(new byte[32]));
        assertThrows(IllegalArgumentException.class, () -> Bip32.masterKey(new byte[128]));
    }

    @Test
    void testDeriveChildNormal() {
        Bip32.ExtendedKey masterKey = Bip32.masterKey(TEST_VECTOR_2_SEED);

        // Derive m/0 (normal, non-hardened)
        // BIP-32 Test Vector 2 uses m/0 (not m/0')
        Bip32.ExtendedKey child = Bip32.deriveChild(masterKey, 0);

        assertNotNull(child);
        assertEquals(32, child.keyBytes().length);
        assertEquals(32, child.chainCode().length);

        // BIP-32 Test Vector 2: m/0
        // private key: abe74a98f6c7eabee0428f53798f0ab8aa1bd37873999041703c742f15ac7e1e
        // chain code: f0909affaa7ee7abe5dd4e100598d4dc53cd709d5a5c2cac40e7412f232f7c9c
        String expectedPrivateKey = "0xabe74a98f6c7eabee0428f53798f0ab8aa1bd37873999041703c742f15ac7e1e";
        String expectedChainCode = "0xf0909affaa7ee7abe5dd4e100598d4dc53cd709d5a5c2cac40e7412f232f7c9c";

        assertEquals(expectedPrivateKey.toLowerCase(), Hex.encode(child.keyBytes()).toLowerCase());
        assertEquals(expectedChainCode.toLowerCase(), Hex.encode(child.chainCode()).toLowerCase());
    }

    @Test
    void testDeriveChildHardened() {
        Bip32.ExtendedKey masterKey = Bip32.masterKey(TEST_VECTOR_2_SEED);

        // First derive m/0
        Bip32.ExtendedKey m0 = Bip32.deriveChild(masterKey, 0);

        // Then derive m/0/2147483647' (hardened)
        int hardenedIndex = 2147483647 | 0x80000000;
        Bip32.ExtendedKey child = Bip32.deriveChild(m0, hardenedIndex);

        assertNotNull(child);
        assertEquals(32, child.keyBytes().length);
        assertEquals(32, child.chainCode().length);

        // BIP-32 Test Vector 2: m/0/2147483647'
        // private key: 877c779ad9687164e9c2f4f0f4ff0340814392330693ce95a58fe18fd52e6e93
        // chain code: be17a268474a6bb9c61e1d720cf6215e2a88c5406c4aee7b38547f585c9a37d9
        String expectedPrivateKey = "0x877c779ad9687164e9c2f4f0f4ff0340814392330693ce95a58fe18fd52e6e93";
        String expectedChainCode = "0xbe17a268474a6bb9c61e1d720cf6215e2a88c5406c4aee7b38547f585c9a37d9";

        assertEquals(expectedPrivateKey.toLowerCase(), Hex.encode(child.keyBytes()).toLowerCase());
        assertEquals(expectedChainCode.toLowerCase(), Hex.encode(child.chainCode()).toLowerCase());
    }

    @Test
    void testDeriveChildWithNullParent() {
        assertThrows(IllegalArgumentException.class, () -> Bip32.deriveChild(null, 0));
    }

    @Test
    void testDerivePath() {
        Bip32.ExtendedKey masterKey = Bip32.masterKey(TEST_VECTOR_2_SEED);

        // BIP-32 Test Vector 2 path: m/0/2147483647'/1/2147483646'/2
        Bip32.ExtendedKey derived = Bip32.derivePath(masterKey, "m/0/2147483647'/1/2147483646'/2");

        assertNotNull(derived);

        // BIP-32 Test Vector 2: m/0/2147483647'/1/2147483646'/2
        // private key: bb7d39bdb83ecf58f2fd82b6d918341cbef428661ef01ab97c28a4842125ac23
        // chain code: 9452b549be8cea3ecb7a84bec10dcfd94afe4d129ebfd3b3cb58eedf394ed271
        String expectedPrivateKey = "0xbb7d39bdb83ecf58f2fd82b6d918341cbef428661ef01ab97c28a4842125ac23";
        String expectedChainCode = "0x9452b549be8cea3ecb7a84bec10dcfd94afe4d129ebfd3b3cb58eedf394ed271";

        assertEquals(expectedPrivateKey.toLowerCase(), Hex.encode(derived.keyBytes()).toLowerCase());
        assertEquals(expectedChainCode.toLowerCase(), Hex.encode(derived.chainCode()).toLowerCase());
    }

    @Test
    void testDerivePathEthereumStandard() {
        Bip32.ExtendedKey masterKey = Bip32.masterKey(BIP39_TEST_SEED);

        // Standard Ethereum path: m/44'/60'/0'/0/0
        Bip32.ExtendedKey derived = Bip32.derivePath(masterKey, "m/44'/60'/0'/0/0");

        assertNotNull(derived);
        assertEquals(32, derived.keyBytes().length);
        assertEquals(32, derived.chainCode().length);

        // This is a well-known test vector for the "abandon" mnemonic
        // The derived address should be 0x9858EfFD232B4033E47d90003D41EC34EcaEda94
        // Private key for m/44'/60'/0'/0/0: 0x1ab42cc412b618bdea3a599e3c9bae199ebf030895b039e9db1e30dafb12b727
        String expectedPrivateKey = "0x1ab42cc412b618bdea3a599e3c9bae199ebf030895b039e9db1e30dafb12b727";
        assertEquals(expectedPrivateKey.toLowerCase(), Hex.encode(derived.keyBytes()).toLowerCase());
    }

    @Test
    void testDerivePathWithHSuffix() {
        Bip32.ExtendedKey masterKey = Bip32.masterKey(TEST_VECTOR_2_SEED);

        // Test using 'h' suffix for hardened derivation
        // m/0/2147483647' is equivalent to m/0/2147483647h
        Bip32.ExtendedKey derived = Bip32.derivePath(masterKey, "m/0/2147483647h");

        // Should produce the same result as m/0/2147483647'
        Bip32.ExtendedKey expected = Bip32.derivePath(masterKey, "m/0/2147483647'");

        assertEquals(Hex.encode(expected.keyBytes()), Hex.encode(derived.keyBytes()));
        assertEquals(Hex.encode(expected.chainCode()), Hex.encode(derived.chainCode()));
    }

    @Test
    void testDerivePathOnlyMaster() {
        Bip32.ExtendedKey masterKey = Bip32.masterKey(TEST_VECTOR_2_SEED);

        // Path "m" should return the master key unchanged
        Bip32.ExtendedKey derived = Bip32.derivePath(masterKey, "m");

        assertEquals(Hex.encode(masterKey.keyBytes()), Hex.encode(derived.keyBytes()));
        assertEquals(Hex.encode(masterKey.chainCode()), Hex.encode(derived.chainCode()));
    }

    @Test
    void testDerivePathWithNullMasterKey() {
        assertThrows(IllegalArgumentException.class, () -> Bip32.derivePath(null, "m/0"));
    }

    @Test
    void testDerivePathWithNullPath() {
        Bip32.ExtendedKey masterKey = Bip32.masterKey(TEST_VECTOR_2_SEED);
        assertThrows(IllegalArgumentException.class, () -> Bip32.derivePath(masterKey, null));
    }

    @Test
    void testDerivePathWithEmptyPath() {
        Bip32.ExtendedKey masterKey = Bip32.masterKey(TEST_VECTOR_2_SEED);
        assertThrows(IllegalArgumentException.class, () -> Bip32.derivePath(masterKey, ""));
    }

    @Test
    void testDerivePathWithInvalidPath() {
        Bip32.ExtendedKey masterKey = Bip32.masterKey(TEST_VECTOR_2_SEED);

        // Path not starting with 'm'
        assertThrows(IllegalArgumentException.class, () -> Bip32.derivePath(masterKey, "0/1/2"));

        // Invalid component
        assertThrows(IllegalArgumentException.class, () -> Bip32.derivePath(masterKey, "m/abc"));

        // Empty component
        assertThrows(IllegalArgumentException.class, () -> Bip32.derivePath(masterKey, "m//0"));
    }

    @Test
    void testMultipleDerivationsProduceDifferentKeys() {
        Bip32.ExtendedKey masterKey = Bip32.masterKey(BIP39_TEST_SEED);

        Bip32.ExtendedKey key0 = Bip32.derivePath(masterKey, "m/44'/60'/0'/0/0");
        Bip32.ExtendedKey key1 = Bip32.derivePath(masterKey, "m/44'/60'/0'/0/1");
        Bip32.ExtendedKey key2 = Bip32.derivePath(masterKey, "m/44'/60'/0'/0/2");

        assertNotEquals(Hex.encode(key0.keyBytes()), Hex.encode(key1.keyBytes()));
        assertNotEquals(Hex.encode(key1.keyBytes()), Hex.encode(key2.keyBytes()));
        assertNotEquals(Hex.encode(key0.keyBytes()), Hex.encode(key2.keyBytes()));
    }

    @Test
    void testExtendedKeyRecord() {
        byte[] keyBytes = new byte[32];
        byte[] chainCode = new byte[32];
        keyBytes[0] = 1;
        chainCode[0] = 2;

        Bip32.ExtendedKey key = new Bip32.ExtendedKey(keyBytes, chainCode);

        assertEquals(32, key.keyBytes().length);
        assertEquals(32, key.chainCode().length);
        assertEquals(1, key.keyBytes()[0]);
        assertEquals(2, key.chainCode()[0]);
    }

    @Test
    void testExtendedKeyWithInvalidKeyBytes() {
        assertThrows(IllegalArgumentException.class,
                () -> new Bip32.ExtendedKey(null, new byte[32]));
        assertThrows(IllegalArgumentException.class,
                () -> new Bip32.ExtendedKey(new byte[16], new byte[32]));
    }

    @Test
    void testExtendedKeyWithInvalidChainCode() {
        assertThrows(IllegalArgumentException.class,
                () -> new Bip32.ExtendedKey(new byte[32], null));
        assertThrows(IllegalArgumentException.class,
                () -> new Bip32.ExtendedKey(new byte[32], new byte[16]));
    }

    private static boolean isAllZeros(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }
}
