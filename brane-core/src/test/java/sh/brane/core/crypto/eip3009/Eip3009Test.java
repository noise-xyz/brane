// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.crypto.eip3009;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import sh.brane.core.crypto.Keccak256;
import sh.brane.core.crypto.PrivateKey;
import sh.brane.core.crypto.PrivateKeySigner;
import sh.brane.core.crypto.Signature;
import sh.brane.core.crypto.eip712.Eip712Domain;
import sh.brane.core.types.Address;
import sh.brane.core.types.Hash;

class Eip3009Test {

    // Anvil's first default key
    private static final String TEST_PRIVATE_KEY =
        "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private static final Address TEST_ADDRESS =
        new Address("0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266");

    private static final Address USDC_ADDRESS =
        new Address("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48");

    private static final Address RECIPIENT =
        new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

    // ═══════════════════════════════════════════════════════════════════
    // Typehash verification
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void transferAuthorization_typehashMatchesKeccak() {
        byte[] computed = Keccak256.hash(
            "TransferWithAuthorization(address from,address to,uint256 value,uint256 validAfter,uint256 validBefore,bytes32 nonce)"
                .getBytes());
        assertEquals(TransferAuthorization.TYPEHASH, Hash.fromBytes(computed));
    }

    @Test
    void receiveAuthorization_typehashMatchesKeccak() {
        byte[] computed = Keccak256.hash(
            "ReceiveWithAuthorization(address from,address to,uint256 value,uint256 validAfter,uint256 validBefore,bytes32 nonce)"
                .getBytes());
        assertEquals(ReceiveAuthorization.TYPEHASH, Hash.fromBytes(computed));
    }

    @Test
    void cancelAuthorization_typehashMatchesKeccak() {
        byte[] computed = Keccak256.hash(
            "CancelAuthorization(address authorizer,bytes32 nonce)"
                .getBytes());
        assertEquals(CancelAuthorization.TYPEHASH, Hash.fromBytes(computed));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Domain helpers
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void usdcDomain_hasCorrectFields() {
        var domain = Eip3009.usdcDomain(1L, USDC_ADDRESS);
        assertEquals("USD Coin", domain.name());
        assertEquals("2", domain.version());
        assertEquals(1L, domain.chainId());
        assertEquals(USDC_ADDRESS, domain.verifyingContract());
    }

    @Test
    void eurcDomain_hasCorrectFields() {
        var addr = new Address("0x1234567890123456789012345678901234567890");
        var domain = Eip3009.eurcDomain(8453L, addr);
        assertEquals("EURC", domain.name());
        assertEquals("2", domain.version());
        assertEquals(8453L, domain.chainId());
        assertEquals(addr, domain.verifyingContract());
    }

    @Test
    void tokenDomain_hasCorrectFields() {
        var addr = new Address("0x1234567890123456789012345678901234567890");
        var domain = Eip3009.tokenDomain("MyToken", "1", 137L, addr);
        assertEquals("MyToken", domain.name());
        assertEquals("1", domain.version());
        assertEquals(137L, domain.chainId());
        assertEquals(addr, domain.verifyingContract());
    }

    @Test
    void usdcDomain_rejectsNullAddress() {
        assertThrows(NullPointerException.class,
            () -> Eip3009.usdcDomain(1L, null));
    }

    @Test
    void eurcDomain_rejectsNullAddress() {
        assertThrows(NullPointerException.class,
            () -> Eip3009.eurcDomain(1L, null));
    }

    @Test
    void tokenDomain_rejectsNullName() {
        var addr = new Address("0x1234567890123456789012345678901234567890");
        assertThrows(NullPointerException.class,
            () -> Eip3009.tokenDomain(null, "1", 1L, addr));
    }

    @Test
    void tokenDomain_rejectsNullVersion() {
        var addr = new Address("0x1234567890123456789012345678901234567890");
        assertThrows(NullPointerException.class,
            () -> Eip3009.tokenDomain("MyToken", null, 1L, addr));
    }

    @Test
    void tokenDomain_rejectsNullAddress() {
        assertThrows(NullPointerException.class,
            () -> Eip3009.tokenDomain("MyToken", "1", 1L, null));
    }

    @Test
    void usdcDomain_separator_isDeterministic() {
        var domain = Eip3009.usdcDomain(1L, USDC_ADDRESS);
        Hash sep1 = domain.separator();
        Hash sep2 = domain.separator();
        assertEquals(sep1, sep2);
        assertNotNull(sep1);
    }

    @Test
    void differentChains_produceDifferentSeparators() {
        var domain1 = Eip3009.usdcDomain(1L, USDC_ADDRESS);
        var domain8453 = Eip3009.usdcDomain(8453L, USDC_ADDRESS);
        assertNotEquals(domain1.separator(), domain8453.separator());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Nonce generation
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void randomNonce_returns32Bytes() {
        byte[] nonce = Eip3009.randomNonce();
        assertEquals(32, nonce.length);
    }

    @Test
    void randomNonce_producesUniqueValues() {
        byte[] nonce1 = Eip3009.randomNonce();
        byte[] nonce2 = Eip3009.randomNonce();
        assertFalse(java.util.Arrays.equals(nonce1, nonce2));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Explicit factory
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void explicitFactory_createsDeterministicAuth() {
        var nonce = new byte[32];
        nonce[0] = 0x42;

        var auth = Eip3009.transferAuthorization(
            TEST_ADDRESS, RECIPIENT, BigInteger.valueOf(1_000_000),
            BigInteger.ZERO, BigInteger.valueOf(1_800_000_000L), nonce);

        assertEquals(TEST_ADDRESS, auth.from());
        assertEquals(RECIPIENT, auth.to());
        assertEquals(BigInteger.valueOf(1_000_000), auth.value());
        assertEquals(BigInteger.ZERO, auth.validAfter());
        assertEquals(BigInteger.valueOf(1_800_000_000L), auth.validBefore());
        assertEquals((byte) 0x42, auth.nonce()[0]);
    }

    @Test
    void explicitFactory_deterministicHash() {
        var nonce = new byte[32];
        var domain = Eip3009.usdcDomain(1L, USDC_ADDRESS);

        var auth1 = Eip3009.transferAuthorization(
            TEST_ADDRESS, RECIPIENT, BigInteger.valueOf(1_000_000),
            BigInteger.ZERO, BigInteger.valueOf(1_800_000_000L), nonce);
        var auth2 = Eip3009.transferAuthorization(
            TEST_ADDRESS, RECIPIENT, BigInteger.valueOf(1_000_000),
            BigInteger.ZERO, BigInteger.valueOf(1_800_000_000L), nonce);

        assertEquals(Eip3009.hash(auth1, domain), Eip3009.hash(auth2, domain));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Convenience factory
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void convenienceFactory_setsValidTimestamps() {
        var auth = Eip3009.transferAuthorization(
            TEST_ADDRESS, RECIPIENT, BigInteger.valueOf(1_000_000), 3600);

        // validAfter should be approximately now - 5
        // validBefore should be approximately now + 3600
        assertTrue(auth.validAfter().compareTo(auth.validBefore()) < 0);
        assertEquals(32, auth.nonce().length);
    }

    @Test
    void convenienceFactory_generatesRandomNonce() {
        var auth1 = Eip3009.transferAuthorization(
            TEST_ADDRESS, RECIPIENT, BigInteger.valueOf(1_000_000), 3600);
        var auth2 = Eip3009.transferAuthorization(
            TEST_ADDRESS, RECIPIENT, BigInteger.valueOf(1_000_000), 3600);

        assertFalse(java.util.Arrays.equals(auth1.nonce(), auth2.nonce()));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Signing + hashing (TransferAuthorization)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void signTransfer_producesValidSignature() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        var domain = Eip3009.usdcDomain(1L, USDC_ADDRESS);
        var nonce = new byte[32];

        var auth = Eip3009.transferAuthorization(
            TEST_ADDRESS, RECIPIENT, BigInteger.valueOf(1_000_000),
            BigInteger.ZERO, BigInteger.valueOf(1_800_000_000L), nonce);

        Signature sig = Eip3009.sign(auth, domain, signer);
        assertNotNull(sig);
        assertEquals(32, sig.r().length);
        assertEquals(32, sig.s().length);
        assertTrue(sig.v() == 27 || sig.v() == 28);
    }

    @Test
    void signTransfer_roundTrip_recoversAddress() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        var domain = Eip3009.usdcDomain(1L, USDC_ADDRESS);
        var nonce = new byte[32];

        var auth = Eip3009.transferAuthorization(
            TEST_ADDRESS, RECIPIENT, BigInteger.valueOf(1_000_000),
            BigInteger.ZERO, BigInteger.valueOf(1_800_000_000L), nonce);

        Hash hash = Eip3009.hash(auth, domain);
        Signature sig = Eip3009.sign(auth, domain, signer);

        Address recovered = PrivateKey.recoverAddress(hash.toBytes(), sig);
        assertEquals(TEST_ADDRESS, recovered);
    }

    @Test
    void hashTransfer_isDeterministic() {
        var domain = Eip3009.usdcDomain(1L, USDC_ADDRESS);
        var nonce = new byte[32];

        var auth = Eip3009.transferAuthorization(
            TEST_ADDRESS, RECIPIENT, BigInteger.valueOf(1_000_000),
            BigInteger.ZERO, BigInteger.valueOf(1_800_000_000L), nonce);

        Hash hash1 = Eip3009.hash(auth, domain);
        Hash hash2 = Eip3009.hash(auth, domain);
        assertEquals(hash1, hash2);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Signing + hashing (ReceiveAuthorization)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void signReceive_roundTrip_recoversAddress() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        var domain = Eip3009.usdcDomain(1L, USDC_ADDRESS);
        var nonce = new byte[32];

        var auth = new ReceiveAuthorization(
            TEST_ADDRESS, RECIPIENT, BigInteger.valueOf(1_000_000),
            BigInteger.ZERO, BigInteger.valueOf(1_800_000_000L), nonce);

        Hash hash = Eip3009.hash(auth, domain);
        Signature sig = Eip3009.sign(auth, domain, signer);

        Address recovered = PrivateKey.recoverAddress(hash.toBytes(), sig);
        assertEquals(TEST_ADDRESS, recovered);
    }

    @Test
    void transferAndReceive_produceDifferentHashes() {
        var domain = Eip3009.usdcDomain(1L, USDC_ADDRESS);
        var nonce = new byte[32];

        var transfer = new TransferAuthorization(
            TEST_ADDRESS, RECIPIENT, BigInteger.valueOf(1_000_000),
            BigInteger.ZERO, BigInteger.valueOf(1_800_000_000L), nonce);
        var receive = new ReceiveAuthorization(
            TEST_ADDRESS, RECIPIENT, BigInteger.valueOf(1_000_000),
            BigInteger.ZERO, BigInteger.valueOf(1_800_000_000L), nonce);

        assertNotEquals(Eip3009.hash(transfer, domain), Eip3009.hash(receive, domain));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Signing + hashing (CancelAuthorization)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void signCancel_roundTrip_recoversAddress() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        var domain = Eip3009.usdcDomain(1L, USDC_ADDRESS);
        var nonce = new byte[32];

        var cancel = new CancelAuthorization(TEST_ADDRESS, nonce);

        Hash hash = Eip3009.hash(cancel, domain);
        Signature sig = Eip3009.sign(cancel, domain, signer);

        Address recovered = PrivateKey.recoverAddress(hash.toBytes(), sig);
        assertEquals(TEST_ADDRESS, recovered);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Record validation (ReceiveAuthorization, CancelAuthorization)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void receiveAuthorization_rejectsNullFrom() {
        assertThrows(NullPointerException.class,
            () -> new ReceiveAuthorization(null, RECIPIENT, BigInteger.ONE,
                BigInteger.ZERO, BigInteger.ONE, new byte[32]));
    }

    @Test
    void receiveAuthorization_rejectsBadNonce() {
        assertThrows(IllegalArgumentException.class,
            () -> new ReceiveAuthorization(TEST_ADDRESS, RECIPIENT, BigInteger.ONE,
                BigInteger.ZERO, BigInteger.ONE, new byte[16]));
    }

    @Test
    void cancelAuthorization_rejectsNullAuthorizer() {
        assertThrows(NullPointerException.class,
            () -> new CancelAuthorization(null, new byte[32]));
    }

    @Test
    void cancelAuthorization_rejectsBadNonce() {
        assertThrows(IllegalArgumentException.class,
            () -> new CancelAuthorization(TEST_ADDRESS, new byte[10]));
    }

    @Test
    void receiveAuthorization_definition_hasCorrectPrimaryType() {
        assertEquals("ReceiveWithAuthorization", ReceiveAuthorization.DEFINITION.primaryType());
    }

    @Test
    void cancelAuthorization_definition_hasCorrectPrimaryType() {
        assertEquals("CancelAuthorization", CancelAuthorization.DEFINITION.primaryType());
    }

    @Test
    void cancelAuthorization_definition_hasTwoFields() {
        var fields = CancelAuthorization.DEFINITION.types().get("CancelAuthorization");
        assertNotNull(fields);
        assertEquals(2, fields.size());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Receive/Cancel factory methods
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void receiveAuthorizationFactory_explicit() {
        var nonce = new byte[32];
        nonce[0] = 0x42;
        var auth = Eip3009.receiveAuthorization(
            TEST_ADDRESS, RECIPIENT, BigInteger.valueOf(1_000_000),
            BigInteger.ZERO, BigInteger.valueOf(1_800_000_000L), nonce);

        assertEquals(TEST_ADDRESS, auth.from());
        assertEquals(RECIPIENT, auth.to());
        assertEquals(BigInteger.valueOf(1_000_000), auth.value());
        assertEquals((byte) 0x42, auth.nonce()[0]);
    }

    @Test
    void receiveAuthorizationFactory_convenience() {
        var auth = Eip3009.receiveAuthorization(
            TEST_ADDRESS, RECIPIENT, BigInteger.valueOf(1_000_000), 3600);

        assertTrue(auth.validAfter().compareTo(auth.validBefore()) < 0);
        assertEquals(32, auth.nonce().length);
    }

    @Test
    void cancelAuthorizationFactory_explicit() {
        var nonce = new byte[32];
        nonce[0] = 0x42;
        var cancel = Eip3009.cancelAuthorization(TEST_ADDRESS, nonce);

        assertEquals(TEST_ADDRESS, cancel.authorizer());
        assertEquals((byte) 0x42, cancel.nonce()[0]);
    }

    @Test
    void cancelAuthorizationFactory_randomNonce() {
        var cancel1 = Eip3009.cancelAuthorization(TEST_ADDRESS);
        var cancel2 = Eip3009.cancelAuthorization(TEST_ADDRESS);

        assertEquals(32, cancel1.nonce().length);
        assertFalse(java.util.Arrays.equals(cancel1.nonce(), cancel2.nonce()));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Validity window validation
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void receiveAuthorization_rejectsInvertedWindow() {
        assertThrows(IllegalArgumentException.class,
            () -> new ReceiveAuthorization(TEST_ADDRESS, RECIPIENT, BigInteger.ONE,
                BigInteger.valueOf(1000), BigInteger.valueOf(500), new byte[32]));
    }

    // ═══════════════════════════════════════════════════════════════════
    // toString
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void receiveAuthorization_toString_includesHexNonce() {
        var nonce = new byte[32];
        nonce[0] = (byte) 0xAB;
        var auth = new ReceiveAuthorization(TEST_ADDRESS, RECIPIENT, BigInteger.ONE,
            BigInteger.ZERO, BigInteger.ONE, nonce);
        String str = auth.toString();
        assertTrue(str.contains("nonce=0xab"), "toString should contain hex nonce, got: " + str);
        assertFalse(str.contains("[B@"), "toString should not contain array reference, got: " + str);
    }

    @Test
    void cancelAuthorization_toString_includesHexNonce() {
        var nonce = new byte[32];
        nonce[0] = (byte) 0xCD;
        var cancel = new CancelAuthorization(TEST_ADDRESS, nonce);
        String str = cancel.toString();
        assertTrue(str.contains("nonce=0xcd"), "toString should contain hex nonce, got: " + str);
        assertFalse(str.contains("[B@"), "toString should not contain array reference, got: " + str);
    }
}
