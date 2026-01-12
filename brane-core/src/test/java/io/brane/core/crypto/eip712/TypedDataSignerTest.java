// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.crypto.eip712;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.brane.core.crypto.PrivateKey;
import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.crypto.Signature;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;

/**
 * Tests for TypedDataSigner - the static utility API for EIP-712 signing.
 *
 * <p>Test vectors are validated against viem (TypeScript) output to ensure
 * cross-library compatibility.
 */
class TypedDataSignerTest {

    // Test private key (Anvil's first default key)
    private static final String TEST_PRIVATE_KEY =
        "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    // Expected address for test private key
    private static final Address TEST_ADDRESS =
        new Address("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266");

    // ═══════════════════════════════════════════════════════════════
    // Test fixtures from EIP-712 specification (also used by viem)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Mail example from EIP-712 spec.
     * Mail(Person from,Person to,string contents)
     * Person(string name,address wallet)
     */
    private static final Map<String, List<TypedDataField>> MAIL_TYPES = new LinkedHashMap<>();
    static {
        MAIL_TYPES.put("Mail", List.of(
            TypedDataField.of("from", "Person"),
            TypedDataField.of("to", "Person"),
            TypedDataField.of("contents", "string")
        ));
        MAIL_TYPES.put("Person", List.of(
            TypedDataField.of("name", "string"),
            TypedDataField.of("wallet", "address")
        ));
    }

    /**
     * Simple Permit type for ERC-2612.
     */
    private static final Map<String, List<TypedDataField>> PERMIT_TYPES = Map.of(
        "Permit", List.of(
            TypedDataField.of("owner", "address"),
            TypedDataField.of("spender", "address"),
            TypedDataField.of("value", "uint256"),
            TypedDataField.of("nonce", "uint256"),
            TypedDataField.of("deadline", "uint256")
        )
    );

    // ═══════════════════════════════════════════════════════════════
    // hashTypedData() tests - matches viem output
    // ═══════════════════════════════════════════════════════════════

    @Test
    void hashTypedData_mailExample_matchesViemOutput() {
        // This test vector matches viem's hashTypedData output for the EIP-712 Mail example.
        // Domain: { name: "Ether Mail", version: "1", chainId: 1, verifyingContract: 0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC }
        // Mail: { from: { name: "Cow", wallet: 0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826 },
        //         to: { name: "Bob", wallet: 0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB },
        //         contents: "Hello, Bob!" }
        //
        // Expected hash from EIP-712 spec (also viem):
        // 0xbe609aee343fb3c4b28e1df9e632fca64fcfaede20f02e86244efddf30957bd2
        var domain = Eip712Domain.builder()
            .name("Ether Mail")
            .version("1")
            .chainId(1)
            .verifyingContract(new Address("0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"))
            .build();

        var from = new LinkedHashMap<String, Object>();
        from.put("name", "Cow");
        from.put("wallet", new Address("0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"));

        var to = new LinkedHashMap<String, Object>();
        to.put("name", "Bob");
        to.put("wallet", new Address("0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB"));

        var message = new LinkedHashMap<String, Object>();
        message.put("from", from);
        message.put("to", to);
        message.put("contents", "Hello, Bob!");

        Hash hash = TypedDataSigner.hashTypedData(domain, "Mail", MAIL_TYPES, message);

        assertEquals(
            "0xbe609aee343fb3c4b28e1df9e632fca64fcfaede20f02e86244efddf30957bd2",
            hash.value(),
            "Hash should match EIP-712 spec/viem output"
        );
    }

    @Test
    void hashTypedData_returns32ByteHash() {
        var domain = Eip712Domain.builder()
            .name("Test")
            .version("1")
            .chainId(1)
            .build();

        var message = new LinkedHashMap<String, Object>();
        message.put("owner", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        message.put("spender", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        message.put("value", BigInteger.valueOf(1000));
        message.put("nonce", BigInteger.ZERO);
        message.put("deadline", BigInteger.valueOf(1234567890));

        Hash hash = TypedDataSigner.hashTypedData(domain, "Permit", PERMIT_TYPES, message);

        assertNotNull(hash);
        assertEquals(32, hash.toBytes().length);
    }

    @Test
    void hashTypedData_consistentResults() {
        var domain = Eip712Domain.builder()
            .name("Test")
            .version("1")
            .chainId(1)
            .build();

        var message = new LinkedHashMap<String, Object>();
        message.put("owner", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        message.put("spender", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        message.put("value", BigInteger.ONE);
        message.put("nonce", BigInteger.ZERO);
        message.put("deadline", BigInteger.TEN);

        Hash hash1 = TypedDataSigner.hashTypedData(domain, "Permit", PERMIT_TYPES, message);
        Hash hash2 = TypedDataSigner.hashTypedData(domain, "Permit", PERMIT_TYPES, message);

        assertEquals(hash1, hash2);
    }

    @Test
    void hashTypedData_differentDomains_differentHashes() {
        var domain1 = Eip712Domain.builder()
            .name("App1")
            .version("1")
            .chainId(1)
            .build();

        var domain2 = Eip712Domain.builder()
            .name("App2")
            .version("1")
            .chainId(1)
            .build();

        var message = new LinkedHashMap<String, Object>();
        message.put("owner", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        message.put("spender", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        message.put("value", BigInteger.ONE);
        message.put("nonce", BigInteger.ZERO);
        message.put("deadline", BigInteger.TEN);

        Hash hash1 = TypedDataSigner.hashTypedData(domain1, "Permit", PERMIT_TYPES, message);
        Hash hash2 = TypedDataSigner.hashTypedData(domain2, "Permit", PERMIT_TYPES, message);

        assertNotEquals(hash1, hash2);
    }

    @Test
    void hashTypedData_differentMessages_differentHashes() {
        var domain = Eip712Domain.builder()
            .name("Test")
            .version("1")
            .chainId(1)
            .build();

        var message1 = new LinkedHashMap<String, Object>();
        message1.put("owner", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        message1.put("spender", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        message1.put("value", BigInteger.valueOf(1000));
        message1.put("nonce", BigInteger.ZERO);
        message1.put("deadline", BigInteger.TEN);

        var message2 = new LinkedHashMap<String, Object>();
        message2.put("owner", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        message2.put("spender", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        message2.put("value", BigInteger.valueOf(2000)); // Different value
        message2.put("nonce", BigInteger.ZERO);
        message2.put("deadline", BigInteger.TEN);

        Hash hash1 = TypedDataSigner.hashTypedData(domain, "Permit", PERMIT_TYPES, message1);
        Hash hash2 = TypedDataSigner.hashTypedData(domain, "Permit", PERMIT_TYPES, message2);

        assertNotEquals(hash1, hash2);
    }

    @Test
    void hashTypedData_differentChainIds_differentHashes() {
        var domain1 = Eip712Domain.builder()
            .name("Test")
            .version("1")
            .chainId(1) // Mainnet
            .build();

        var domain2 = Eip712Domain.builder()
            .name("Test")
            .version("1")
            .chainId(137) // Polygon
            .build();

        var message = new LinkedHashMap<String, Object>();
        message.put("owner", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        message.put("spender", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        message.put("value", BigInteger.ONE);
        message.put("nonce", BigInteger.ZERO);
        message.put("deadline", BigInteger.TEN);

        Hash hash1 = TypedDataSigner.hashTypedData(domain1, "Permit", PERMIT_TYPES, message);
        Hash hash2 = TypedDataSigner.hashTypedData(domain2, "Permit", PERMIT_TYPES, message);

        assertNotEquals(hash1, hash2);
    }

    // ═══════════════════════════════════════════════════════════════
    // signTypedData() tests - v=27/28 requirement
    // ═══════════════════════════════════════════════════════════════

    @Test
    void signTypedData_returnsV27Or28() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);

        var domain = Eip712Domain.builder()
            .name("Test")
            .version("1")
            .chainId(1)
            .build();

        var message = new LinkedHashMap<String, Object>();
        message.put("owner", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        message.put("spender", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        message.put("value", BigInteger.ONE);
        message.put("nonce", BigInteger.ZERO);
        message.put("deadline", BigInteger.TEN);

        Signature sig = TypedDataSigner.signTypedData(domain, "Permit", PERMIT_TYPES, message, signer);

        assertTrue(
            sig.v() == 27 || sig.v() == 28,
            "Signature v should be 27 or 28 for EIP-712, got: " + sig.v()
        );
    }

    @Test
    void signTypedData_mailExample_returnsV27Or28() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);

        var domain = Eip712Domain.builder()
            .name("Ether Mail")
            .version("1")
            .chainId(1)
            .verifyingContract(new Address("0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"))
            .build();

        var from = new LinkedHashMap<String, Object>();
        from.put("name", "Cow");
        from.put("wallet", new Address("0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"));

        var to = new LinkedHashMap<String, Object>();
        to.put("name", "Bob");
        to.put("wallet", new Address("0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB"));

        var message = new LinkedHashMap<String, Object>();
        message.put("from", from);
        message.put("to", to);
        message.put("contents", "Hello, Bob!");

        Signature sig = TypedDataSigner.signTypedData(domain, "Mail", MAIL_TYPES, message, signer);

        assertTrue(
            sig.v() == 27 || sig.v() == 28,
            "Signature v should be 27 or 28, got: " + sig.v()
        );
    }

    @Test
    void signTypedData_validSignatureComponents() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);

        var domain = Eip712Domain.builder()
            .name("Test")
            .version("1")
            .chainId(1)
            .build();

        var message = new LinkedHashMap<String, Object>();
        message.put("owner", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        message.put("spender", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        message.put("value", BigInteger.ONE);
        message.put("nonce", BigInteger.ZERO);
        message.put("deadline", BigInteger.TEN);

        Signature sig = TypedDataSigner.signTypedData(domain, "Permit", PERMIT_TYPES, message, signer);

        assertNotNull(sig);
        assertEquals(32, sig.r().length, "r component should be 32 bytes");
        assertEquals(32, sig.s().length, "s component should be 32 bytes");
    }

    @Test
    void signTypedData_deterministicSignature() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);

        var domain = Eip712Domain.builder()
            .name("Test")
            .version("1")
            .chainId(1)
            .build();

        var message = new LinkedHashMap<String, Object>();
        message.put("owner", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        message.put("spender", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        message.put("value", BigInteger.ONE);
        message.put("nonce", BigInteger.ZERO);
        message.put("deadline", BigInteger.TEN);

        // RFC 6979 deterministic signing should produce identical signatures
        Signature sig1 = TypedDataSigner.signTypedData(domain, "Permit", PERMIT_TYPES, message, signer);
        Signature sig2 = TypedDataSigner.signTypedData(domain, "Permit", PERMIT_TYPES, message, signer);

        assertEquals(sig1, sig2, "Deterministic signing should produce identical signatures");
    }

    // ═══════════════════════════════════════════════════════════════
    // Signature recovery tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void signTypedData_signatureCanRecoverAddress() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        var expectedAddress = signer.address();

        var domain = Eip712Domain.builder()
            .name("Test")
            .version("1")
            .chainId(1)
            .build();

        var message = new LinkedHashMap<String, Object>();
        message.put("owner", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        message.put("spender", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        message.put("value", BigInteger.ONE);
        message.put("nonce", BigInteger.ZERO);
        message.put("deadline", BigInteger.TEN);

        Hash hash = TypedDataSigner.hashTypedData(domain, "Permit", PERMIT_TYPES, message);
        Signature sig = TypedDataSigner.signTypedData(domain, "Permit", PERMIT_TYPES, message, signer);

        // Recover address from signature
        Address recovered = PrivateKey.recoverAddress(hash.toBytes(), sig);

        assertEquals(expectedAddress, recovered);
    }

    @Test
    void signTypedData_mailExample_signatureCanRecoverAddress() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        var expectedAddress = signer.address();

        var domain = Eip712Domain.builder()
            .name("Ether Mail")
            .version("1")
            .chainId(1)
            .verifyingContract(new Address("0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"))
            .build();

        var from = new LinkedHashMap<String, Object>();
        from.put("name", "Cow");
        from.put("wallet", new Address("0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"));

        var to = new LinkedHashMap<String, Object>();
        to.put("name", "Bob");
        to.put("wallet", new Address("0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB"));

        var message = new LinkedHashMap<String, Object>();
        message.put("from", from);
        message.put("to", to);
        message.put("contents", "Hello, Bob!");

        Hash hash = TypedDataSigner.hashTypedData(domain, "Mail", MAIL_TYPES, message);
        Signature sig = TypedDataSigner.signTypedData(domain, "Mail", MAIL_TYPES, message, signer);

        Address recovered = PrivateKey.recoverAddress(hash.toBytes(), sig);

        assertEquals(expectedAddress, recovered);
    }

    @Test
    void signTypedData_recoveredAddressMatchesTestAddress() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);

        var domain = Eip712Domain.builder()
            .name("Test")
            .version("1")
            .chainId(1)
            .build();

        var message = new LinkedHashMap<String, Object>();
        message.put("owner", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        message.put("spender", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        message.put("value", BigInteger.ONE);
        message.put("nonce", BigInteger.ZERO);
        message.put("deadline", BigInteger.TEN);

        Hash hash = TypedDataSigner.hashTypedData(domain, "Permit", PERMIT_TYPES, message);
        Signature sig = TypedDataSigner.signTypedData(domain, "Permit", PERMIT_TYPES, message, signer);

        Address recovered = PrivateKey.recoverAddress(hash.toBytes(), sig);

        // Verify against the known test address
        assertEquals(TEST_ADDRESS.value().toLowerCase(), recovered.value().toLowerCase());
    }

    // ═══════════════════════════════════════════════════════════════
    // Null parameter handling tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void hashTypedData_nullDomain_throws() {
        var message = new LinkedHashMap<String, Object>();
        message.put("owner", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        message.put("spender", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        message.put("value", BigInteger.ONE);
        message.put("nonce", BigInteger.ZERO);
        message.put("deadline", BigInteger.TEN);

        assertThrows(NullPointerException.class, () ->
            TypedDataSigner.hashTypedData(null, "Permit", PERMIT_TYPES, message));
    }

    @Test
    void hashTypedData_nullPrimaryType_throws() {
        var domain = Eip712Domain.builder().name("Test").version("1").build();
        var message = new LinkedHashMap<String, Object>();
        message.put("owner", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        message.put("spender", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        message.put("value", BigInteger.ONE);
        message.put("nonce", BigInteger.ZERO);
        message.put("deadline", BigInteger.TEN);

        assertThrows(NullPointerException.class, () ->
            TypedDataSigner.hashTypedData(domain, null, PERMIT_TYPES, message));
    }

    @Test
    void hashTypedData_nullTypes_throws() {
        var domain = Eip712Domain.builder().name("Test").version("1").build();
        var message = new LinkedHashMap<String, Object>();
        message.put("owner", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        message.put("spender", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        message.put("value", BigInteger.ONE);
        message.put("nonce", BigInteger.ZERO);
        message.put("deadline", BigInteger.TEN);

        assertThrows(NullPointerException.class, () ->
            TypedDataSigner.hashTypedData(domain, "Permit", null, message));
    }

    @Test
    void hashTypedData_nullMessage_throws() {
        var domain = Eip712Domain.builder().name("Test").version("1").build();

        assertThrows(NullPointerException.class, () ->
            TypedDataSigner.hashTypedData(domain, "Permit", PERMIT_TYPES, null));
    }

    @Test
    void signTypedData_nullSigner_throws() {
        var domain = Eip712Domain.builder().name("Test").version("1").build();
        var message = new LinkedHashMap<String, Object>();
        message.put("owner", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        message.put("spender", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        message.put("value", BigInteger.ONE);
        message.put("nonce", BigInteger.ZERO);
        message.put("deadline", BigInteger.TEN);

        assertThrows(NullPointerException.class, () ->
            TypedDataSigner.signTypedData(domain, "Permit", PERMIT_TYPES, message, null));
    }

    @Test
    void signTypedData_nullDomain_throws() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        var message = new LinkedHashMap<String, Object>();
        message.put("owner", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        message.put("spender", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        message.put("value", BigInteger.ONE);
        message.put("nonce", BigInteger.ZERO);
        message.put("deadline", BigInteger.TEN);

        assertThrows(NullPointerException.class, () ->
            TypedDataSigner.signTypedData(null, "Permit", PERMIT_TYPES, message, signer));
    }
}
