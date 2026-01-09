package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.brane.core.crypto.Signer;
import io.brane.core.types.Address;

class AnvilSignersTest {

    // Expected addresses for Anvil's default accounts (derived from well-known mnemonic)
    private static final String[] EXPECTED_ADDRESSES = {
            "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", // Account 0
            "0x70997970C51812dc3A010C7d01b50e0d17dc79C8", // Account 1
            "0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC", // Account 2
            "0x90F79bf6EB2c4f870365E785982E1f101E93b906", // Account 3
            "0x15d34AAf54267DB7D7c367839AAf71A00a2C6A65", // Account 4
            "0x9965507D1a55bcC2695C58ba16FB37d819B0A4dc", // Account 5
            "0x976EA74026E726554dB657fA54763abd0C3a0aa9", // Account 6
            "0x14dC79964da2C08b23698B3D3cc7Ca32193d9955", // Account 7
            "0x23618e81E3f5cdF7f54C3d65f7FBc0aBf5B21E8f", // Account 8
            "0xa0Ee7A142d267C1f36714E4a8F75612F20a79720"  // Account 9
    };

    @Test
    void countReturns10() {
        assertEquals(10, AnvilSigners.count());
    }

    @Test
    void defaultKeyReturnsAccount0() {
        Signer signer = AnvilSigners.defaultKey();
        assertNotNull(signer);
        assertEquals(
                new Address(EXPECTED_ADDRESSES[0]),
                signer.address(),
                "defaultKey() should return account 0");
    }

    @Test
    void keyAtReturnsCorrectAddresses() {
        for (int i = 0; i < AnvilSigners.count(); i++) {
            Signer signer = AnvilSigners.keyAt(i);
            assertNotNull(signer, "keyAt(" + i + ") should not return null");
            assertEquals(
                    new Address(EXPECTED_ADDRESSES[i]),
                    signer.address(),
                    "keyAt(" + i + ") should return correct address");
        }
    }

    @Test
    void keyAtThrowsForNegativeIndex() {
        IndexOutOfBoundsException ex = assertThrows(
                IndexOutOfBoundsException.class,
                () -> AnvilSigners.keyAt(-1));
        assertTrue(ex.getMessage().contains("-1"));
    }

    @Test
    void keyAtThrowsForIndexGreaterThan9() {
        IndexOutOfBoundsException ex = assertThrows(
                IndexOutOfBoundsException.class,
                () -> AnvilSigners.keyAt(10));
        assertTrue(ex.getMessage().contains("10"));
    }

    @Test
    void keyAtReturnsDistinctSigners() {
        // Each call should return a new instance (signers should not be cached)
        Signer signer1 = AnvilSigners.keyAt(0);
        Signer signer2 = AnvilSigners.keyAt(0);
        assertNotSame(signer1, signer2, "keyAt() should return new instances");
        assertEquals(signer1.address(), signer2.address(), "Same index should give same address");
    }

    @Test
    void allAccountsHaveUniqueAddresses() {
        var addresses = new java.util.HashSet<Address>();
        for (int i = 0; i < AnvilSigners.count(); i++) {
            Address addr = AnvilSigners.keyAt(i).address();
            assertTrue(addresses.add(addr), "Account " + i + " should have unique address");
        }
        assertEquals(10, addresses.size());
    }
}
