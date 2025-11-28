package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.brane.core.tx.LegacyTransaction;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import org.junit.jupiter.api.Test;

class PrivateKeyTransactionSignerTest {

    @Test
    void derivesAddressAndSigns() {
        PrivateKeyTransactionSigner signer = new PrivateKeyTransactionSigner(
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

        String signed = signer.sign(tx, 1L); // chainId = 1
        // should be 0x-prefixed hex
        assertEquals("0x", signed.substring(0, 2));
    }

    @Test
    void invalidKeyThrows() {
        assertThrows(IllegalArgumentException.class, () -> new PrivateKeyTransactionSigner("not-a-key"));
    }
}
