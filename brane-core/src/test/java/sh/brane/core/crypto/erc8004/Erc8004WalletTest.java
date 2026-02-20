// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.crypto.erc8004;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import sh.brane.core.crypto.PrivateKey;
import sh.brane.core.crypto.PrivateKeySigner;
import sh.brane.core.crypto.Signature;
import sh.brane.core.crypto.eip712.Eip712Domain;
import sh.brane.core.crypto.erc8004.Erc8004Wallet.AgentWalletBinding;
import sh.brane.core.erc8004.Erc8004Addresses;
import sh.brane.core.types.Address;

class Erc8004WalletTest {

    private static final String TEST_PRIVATE_KEY =
        "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private static final Address TEST_ADDRESS =
        new Address("0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266");

    // ═══════════════════════════════════════════════════════════════════
    // AgentWalletBinding record
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void binding_rejectsNullAgentId() {
        assertThrows(NullPointerException.class,
            () -> new AgentWalletBinding(null, TEST_ADDRESS, BigInteger.ONE));
    }

    @Test
    void binding_rejectsNullWallet() {
        assertThrows(NullPointerException.class,
            () -> new AgentWalletBinding(BigInteger.ONE, null, BigInteger.ONE));
    }

    @Test
    void binding_rejectsNullDeadline() {
        assertThrows(NullPointerException.class,
            () -> new AgentWalletBinding(BigInteger.ONE, TEST_ADDRESS, null));
    }

    @Test
    void definition_hasCorrectPrimaryType() {
        assertEquals("AgentWalletBinding", AgentWalletBinding.DEFINITION.primaryType());
    }

    @Test
    void definition_hasThreeFields() {
        var fields = AgentWalletBinding.DEFINITION.types().get("AgentWalletBinding");
        assertNotNull(fields);
        assertEquals(3, fields.size());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Signing
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void signWalletBinding_producesValidSignature() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        var domain = Eip712Domain.builder()
            .name("ERC8004IdentityRegistry")
            .version("1")
            .chainId(1L)
            .verifyingContract(Erc8004Addresses.MAINNET_IDENTITY)
            .build();

        var binding = new AgentWalletBinding(
            BigInteger.valueOf(42), TEST_ADDRESS, BigInteger.valueOf(1_700_000_000L));

        Signature sig = Erc8004Wallet.signWalletBinding(binding, domain, signer);
        assertNotNull(sig);
        assertEquals(32, sig.r().length);
        assertEquals(32, sig.s().length);
        assertTrue(sig.v() == 27 || sig.v() == 28);
    }

    @Test
    void signWalletBinding_isDeterministic() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        var domain = Eip712Domain.builder()
            .name("ERC8004IdentityRegistry")
            .version("1")
            .chainId(1L)
            .verifyingContract(Erc8004Addresses.MAINNET_IDENTITY)
            .build();

        var binding = new AgentWalletBinding(
            BigInteger.valueOf(42), TEST_ADDRESS, BigInteger.valueOf(1_700_000_000L));

        Signature sig1 = Erc8004Wallet.signWalletBinding(binding, domain, signer);
        Signature sig2 = Erc8004Wallet.signWalletBinding(binding, domain, signer);

        assertArrayEquals(sig1.r(), sig2.r());
        assertArrayEquals(sig1.s(), sig2.s());
        assertEquals(sig1.v(), sig2.v());
    }

    @Test
    void signWalletBinding_roundTrip_recoversAddress() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        var domain = Eip712Domain.builder()
            .name("ERC8004IdentityRegistry")
            .version("1")
            .chainId(1L)
            .verifyingContract(Erc8004Addresses.MAINNET_IDENTITY)
            .build();

        var binding = new AgentWalletBinding(
            BigInteger.valueOf(42), TEST_ADDRESS, BigInteger.valueOf(1_700_000_000L));

        var typedData = sh.brane.core.crypto.eip712.TypedData.create(
            domain, AgentWalletBinding.DEFINITION, binding);

        Signature sig = Erc8004Wallet.signWalletBinding(binding, domain, signer);
        Address recovered = PrivateKey.recoverAddress(typedData.hash().toBytes(), sig);
        assertEquals(TEST_ADDRESS, recovered);
    }

    @Test
    void signWalletBinding_rejectsNullBinding() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        var domain = Eip712Domain.builder().name("test").build();
        assertThrows(NullPointerException.class,
            () -> Erc8004Wallet.signWalletBinding(null, domain, signer));
    }

    @Test
    void signWalletBinding_rejectsNullDomain() {
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        var binding = new AgentWalletBinding(
            BigInteger.ONE, TEST_ADDRESS, BigInteger.ONE);
        assertThrows(NullPointerException.class,
            () -> Erc8004Wallet.signWalletBinding(binding, null, signer));
    }
}
