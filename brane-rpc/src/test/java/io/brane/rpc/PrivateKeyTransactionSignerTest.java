package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.brane.core.types.Address;
import io.brane.internal.web3j.crypto.RawTransaction;
import org.junit.jupiter.api.Test;

class PrivateKeyTransactionSignerTest {

    @Test
    void derivesAddressAndSigns() {
        PrivateKeyTransactionSigner signer =
                new PrivateKeyTransactionSigner(
                        "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");

        Address address = signer.address();
        assertEquals("0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266", address.value());

        RawTransaction tx =
                RawTransaction.createTransaction(
                        1L,
                        java.math.BigInteger.ZERO,
                        java.math.BigInteger.valueOf(21_000L),
                        "0x0000000000000000000000000000000000000000",
                        java.math.BigInteger.ZERO,
                        "",
                        java.math.BigInteger.ZERO,
                        java.math.BigInteger.ZERO);
        String signed = signer.sign(tx);
        // should be 0x-prefixed hex
        assertEquals("0x", signed.substring(0, 2));
    }

    @Test
    void invalidKeyThrows() {
        assertThrows(IllegalArgumentException.class, () -> new PrivateKeyTransactionSigner("not-a-key"));
    }
}
