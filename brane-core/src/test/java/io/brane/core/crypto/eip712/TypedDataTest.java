// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.crypto.eip712;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.brane.core.crypto.PrivateKey;
import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.crypto.Signature;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;

class TypedDataTest {

    // Test private key (Anvil's first default key)
    private static final String TEST_PRIVATE_KEY =
        "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    // Test record for ERC-2612 Permit
    record Permit(Address owner, Address spender, BigInteger value, BigInteger nonce, BigInteger deadline) {}

    private static final Map<String, List<TypedDataField>> PERMIT_TYPES = Map.of(
        "Permit", List.of(
            TypedDataField.of("owner", "address"),
            TypedDataField.of("spender", "address"),
            TypedDataField.of("value", "uint256"),
            TypedDataField.of("nonce", "uint256"),
            TypedDataField.of("deadline", "uint256")
        )
    );

    private static final TypeDefinition<Permit> PERMIT_DEFINITION =
        TypeDefinition.forRecord(Permit.class, "Permit", PERMIT_TYPES);

    // Simple Mail example from EIP-712 spec
    record Mail(Address from, Address to, String contents) {}

    private static final Map<String, List<TypedDataField>> MAIL_TYPES = Map.of(
        "Mail", List.of(
            TypedDataField.of("from", "address"),
            TypedDataField.of("to", "address"),
            TypedDataField.of("contents", "string")
        )
    );

    private static final TypeDefinition<Mail> MAIL_DEFINITION =
        TypeDefinition.forRecord(Mail.class, "Mail", MAIL_TYPES);

    @Test
    void create_returnsTypedData() {
        var domain = Eip712Domain.builder()
            .name("TestToken")
            .version("1")
            .chainId(1)
            .verifyingContract(new Address("0x1234567890123456789012345678901234567890"))
            .build();

        var permit = new Permit(
            new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            BigInteger.valueOf(1000),
            BigInteger.ZERO,
            BigInteger.valueOf(1234567890)
        );

        var typedData = TypedData.create(domain, PERMIT_DEFINITION, permit);

        assertNotNull(typedData);
        assertEquals(domain, typedData.domain());
        assertEquals("Permit", typedData.primaryType());
        assertEquals(permit, typedData.message());
        assertEquals(PERMIT_DEFINITION, typedData.definition());
    }

    @Test
    void create_nullDomain_throws() {
        var permit = new Permit(
            new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            BigInteger.ONE, BigInteger.ZERO, BigInteger.TEN
        );

        assertThrows(NullPointerException.class, () ->
            TypedData.create(null, PERMIT_DEFINITION, permit));
    }

    @Test
    void create_nullDefinition_throws() {
        var domain = Eip712Domain.builder().name("Test").version("1").build();
        var permit = new Permit(
            new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            BigInteger.ONE, BigInteger.ZERO, BigInteger.TEN
        );

        assertThrows(NullPointerException.class, () ->
            TypedData.create(domain, null, permit));
    }

    @Test
    void create_nullMessage_throws() {
        var domain = Eip712Domain.builder().name("Test").version("1").build();

        assertThrows(NullPointerException.class, () ->
            TypedData.create(domain, PERMIT_DEFINITION, null));
    }

    @Test
    void hash_returnsConsistentHash() {
        var domain = Eip712Domain.builder()
            .name("TestToken")
            .version("1")
            .chainId(1)
            .verifyingContract(new Address("0x1234567890123456789012345678901234567890"))
            .build();

        var permit = new Permit(
            new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            BigInteger.valueOf(1000),
            BigInteger.ZERO,
            BigInteger.valueOf(1234567890)
        );

        var typedData = TypedData.create(domain, PERMIT_DEFINITION, permit);

        Hash hash1 = typedData.hash();
        Hash hash2 = typedData.hash();

        assertNotNull(hash1);
        assertEquals(32, hash1.toBytes().length);
        assertEquals(hash1, hash2);
    }

    @Test
    void hash_differentDomains_differentHashes() {
        var domain1 = Eip712Domain.builder()
            .name("Token1")
            .version("1")
            .chainId(1)
            .build();

        var domain2 = Eip712Domain.builder()
            .name("Token2")
            .version("1")
            .chainId(1)
            .build();

        var permit = new Permit(
            new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            BigInteger.ONE, BigInteger.ZERO, BigInteger.TEN
        );

        var typedData1 = TypedData.create(domain1, PERMIT_DEFINITION, permit);
        var typedData2 = TypedData.create(domain2, PERMIT_DEFINITION, permit);

        assertNotEquals(typedData1.hash(), typedData2.hash());
    }

    @Test
    void hash_differentMessages_differentHashes() {
        var domain = Eip712Domain.builder()
            .name("TestToken")
            .version("1")
            .chainId(1)
            .build();

        var permit1 = new Permit(
            new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            BigInteger.valueOf(1000),
            BigInteger.ZERO,
            BigInteger.TEN
        );

        var permit2 = new Permit(
            new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            BigInteger.valueOf(2000), // Different value
            BigInteger.ZERO,
            BigInteger.TEN
        );

        var typedData1 = TypedData.create(domain, PERMIT_DEFINITION, permit1);
        var typedData2 = TypedData.create(domain, PERMIT_DEFINITION, permit2);

        assertNotEquals(typedData1.hash(), typedData2.hash());
    }

    @Test
    void sign_returnsValidSignature() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);

        var domain = Eip712Domain.builder()
            .name("TestToken")
            .version("1")
            .chainId(1)
            .verifyingContract(new Address("0x1234567890123456789012345678901234567890"))
            .build();

        var permit = new Permit(
            new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            BigInteger.valueOf(1000),
            BigInteger.ZERO,
            BigInteger.valueOf(1234567890)
        );

        var typedData = TypedData.create(domain, PERMIT_DEFINITION, permit);

        Signature sig = typedData.sign(signer);

        assertNotNull(sig);
        assertEquals(32, sig.r().length);
        assertEquals(32, sig.s().length);
        assertTrue(sig.v() == 27 || sig.v() == 28, "v should be 27 or 28, got: " + sig.v());
    }

    @Test
    void sign_signatureCanRecoverAddress() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        var expectedAddress = signer.address();

        var domain = Eip712Domain.builder()
            .name("TestToken")
            .version("1")
            .chainId(1)
            .build();

        var permit = new Permit(
            new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            BigInteger.valueOf(1000),
            BigInteger.ZERO,
            BigInteger.valueOf(1234567890)
        );

        var typedData = TypedData.create(domain, PERMIT_DEFINITION, permit);
        Hash hash = typedData.hash();
        Signature sig = typedData.sign(signer);

        // Recover address from signature
        Address recovered = PrivateKey.recoverAddress(hash.toBytes(), sig);

        assertEquals(expectedAddress, recovered);
    }

    @Test
    void sign_nullSigner_throws() {
        var domain = Eip712Domain.builder().name("Test").version("1").build();
        var permit = new Permit(
            new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            BigInteger.ONE, BigInteger.ZERO, BigInteger.TEN
        );

        var typedData = TypedData.create(domain, PERMIT_DEFINITION, permit);

        assertThrows(NullPointerException.class, () -> typedData.sign(null));
    }

    @Test
    void sign_consistentSignature() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);

        var domain = Eip712Domain.builder()
            .name("TestToken")
            .version("1")
            .chainId(1)
            .build();

        var permit = new Permit(
            new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            BigInteger.valueOf(1000),
            BigInteger.ZERO,
            BigInteger.valueOf(1234567890)
        );

        var typedData = TypedData.create(domain, PERMIT_DEFINITION, permit);

        Signature sig1 = typedData.sign(signer);
        Signature sig2 = typedData.sign(signer);

        // Deterministic signing (RFC 6979) should produce identical signatures
        assertEquals(sig1, sig2);
    }

    @Test
    void mailExample_hashAndSign() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);

        var domain = Eip712Domain.builder()
            .name("Ether Mail")
            .version("1")
            .chainId(1)
            .verifyingContract(new Address("0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"))
            .build();

        var mail = new Mail(
            new Address("0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"),
            new Address("0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB"),
            "Hello, Bob!"
        );

        var typedData = TypedData.create(domain, MAIL_DEFINITION, mail);

        Hash hash = typedData.hash();
        Signature sig = typedData.sign(signer);

        assertNotNull(hash);
        assertNotNull(sig);
        assertEquals(32, hash.toBytes().length);
        assertTrue(sig.v() == 27 || sig.v() == 28);

        // Verify signature recovery
        Address recovered = PrivateKey.recoverAddress(hash.toBytes(), sig);
        assertEquals(signer.address(), recovered);
    }

    @Test
    void definition_accessorReturnsDefinition() {
        var domain = Eip712Domain.builder().name("Test").version("1").build();
        var permit = new Permit(
            new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            BigInteger.ONE, BigInteger.ZERO, BigInteger.TEN
        );

        var typedData = TypedData.create(domain, PERMIT_DEFINITION, permit);

        assertSame(PERMIT_DEFINITION, typedData.definition());
        assertEquals(PERMIT_TYPES, typedData.definition().types());
    }

    // ═══════════════════════════════════════════════════════════════
    // fromPayload() tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void fromPayload_createsTypedData() {
        var domain = Eip712Domain.builder()
            .name("TestToken")
            .version("1")
            .chainId(1)
            .verifyingContract(new Address("0x1234567890123456789012345678901234567890"))
            .build();

        var message = Map.<String, Object>of(
            "owner", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            "spender", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            "value", BigInteger.valueOf(1000),
            "nonce", BigInteger.ZERO,
            "deadline", BigInteger.valueOf(1234567890)
        );

        var payload = new TypedDataPayload(domain, "Permit", PERMIT_TYPES, message);
        var typedData = TypedData.fromPayload(payload);

        assertNotNull(typedData);
        assertEquals(domain, typedData.domain());
        assertEquals("Permit", typedData.primaryType());
        assertEquals(message, typedData.message());
    }

    @Test
    void fromPayload_nullPayload_throws() {
        assertThrows(NullPointerException.class, () -> TypedData.fromPayload(null));
    }

    @Test
    void fromPayload_hashConsistentWithCreate() {
        var domain = Eip712Domain.builder()
            .name("TestToken")
            .version("1")
            .chainId(1)
            .build();

        // Create with record-based approach
        var permit = new Permit(
            new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            BigInteger.valueOf(1000),
            BigInteger.ZERO,
            BigInteger.valueOf(1234567890)
        );
        var typedDataFromRecord = TypedData.create(domain, PERMIT_DEFINITION, permit);

        // Create with payload-based approach (Map message)
        var message = Map.<String, Object>of(
            "owner", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            "spender", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            "value", BigInteger.valueOf(1000),
            "nonce", BigInteger.ZERO,
            "deadline", BigInteger.valueOf(1234567890)
        );
        var payload = new TypedDataPayload(domain, "Permit", PERMIT_TYPES, message);
        var typedDataFromPayload = TypedData.fromPayload(payload);

        // Both should produce the same hash
        assertEquals(typedDataFromRecord.hash(), typedDataFromPayload.hash());
    }

    @Test
    void fromPayload_signatureRecovery() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        var expectedAddress = signer.address();

        var domain = Eip712Domain.builder()
            .name("TestToken")
            .version("1")
            .chainId(1)
            .build();

        var message = Map.<String, Object>of(
            "owner", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            "spender", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            "value", BigInteger.valueOf(1000),
            "nonce", BigInteger.ZERO,
            "deadline", BigInteger.valueOf(1234567890)
        );

        var payload = new TypedDataPayload(domain, "Permit", PERMIT_TYPES, message);
        var typedData = TypedData.fromPayload(payload);

        Hash hash = typedData.hash();
        Signature sig = typedData.sign(signer);

        Address recovered = PrivateKey.recoverAddress(hash.toBytes(), sig);
        assertEquals(expectedAddress, recovered);
    }

    // ═══════════════════════════════════════════════════════════════
    // TypeDefinition.forRecord() extraction tests (integration)
    // ═══════════════════════════════════════════════════════════════

    @Test
    void forRecord_extractsFieldsCorrectly() {
        var permit = new Permit(
            new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            BigInteger.valueOf(1000),
            BigInteger.ZERO,
            BigInteger.valueOf(1234567890)
        );

        var extracted = PERMIT_DEFINITION.extractor().apply(permit);

        assertEquals(new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), extracted.get("owner"));
        assertEquals(new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"), extracted.get("spender"));
        assertEquals(BigInteger.valueOf(1000), extracted.get("value"));
        assertEquals(BigInteger.ZERO, extracted.get("nonce"));
        assertEquals(BigInteger.valueOf(1234567890), extracted.get("deadline"));
        assertEquals(5, extracted.size());
    }

    @Test
    void forRecord_extractionPreservesFieldOrder() {
        var permit = new Permit(
            new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            BigInteger.ONE,
            BigInteger.ZERO,
            BigInteger.TEN
        );

        var extracted = PERMIT_DEFINITION.extractor().apply(permit);
        var keys = extracted.keySet().toArray(String[]::new);

        // Record components should preserve declaration order
        assertArrayEquals(new String[]{"owner", "spender", "value", "nonce", "deadline"}, keys);
    }

    @Test
    void forRecord_mailExtractionWorks() {
        var mail = new Mail(
            new Address("0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"),
            new Address("0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB"),
            "Hello, Bob!"
        );

        var extracted = MAIL_DEFINITION.extractor().apply(mail);

        assertEquals(new Address("0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"), extracted.get("from"));
        assertEquals(new Address("0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB"), extracted.get("to"));
        assertEquals("Hello, Bob!", extracted.get("contents"));
    }

    @Test
    void forRecord_hashStableAcrossExtractions() {
        var domain = Eip712Domain.builder()
            .name("TestToken")
            .version("1")
            .chainId(1)
            .build();

        var permit = new Permit(
            new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            BigInteger.valueOf(1000),
            BigInteger.ZERO,
            BigInteger.TEN
        );

        // Create new definition instances (each with own extractor)
        var definition1 = TypeDefinition.forRecord(Permit.class, "Permit", PERMIT_TYPES);
        var definition2 = TypeDefinition.forRecord(Permit.class, "Permit", PERMIT_TYPES);

        var typedData1 = TypedData.create(domain, definition1, permit);
        var typedData2 = TypedData.create(domain, definition2, permit);

        // Hashes should be identical even with different definition instances
        assertEquals(typedData1.hash(), typedData2.hash());
    }
}
