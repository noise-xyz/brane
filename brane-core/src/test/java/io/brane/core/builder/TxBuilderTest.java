package io.brane.core.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import org.junit.jupiter.api.Test;

class TxBuilderTest {

    @Test
    void rejectsMissingTarget() {
        assertThrows(
                BraneTxBuilderException.class,
                () -> TxBuilder.legacy().gasPrice(Wei.of(1)).build());
        assertThrows(
                BraneTxBuilderException.class,
                () -> TxBuilder.eip1559().maxFeePerGas(Wei.of(1)).maxPriorityFeePerGas(Wei.of(1)).build());
    }

    @Test
    void requiresLegacyGasPrice() {
        assertThrows(BraneTxBuilderException.class, () -> TxBuilder.legacy().to(new Address("0x1")).build());
    }

    @Test
    void requiresEipFees() {
        assertThrows(
                BraneTxBuilderException.class,
                () -> TxBuilder.eip1559().to(new Address("0x1")).maxFeePerGas(Wei.of(1)).build());
    }

    @Test
    void buildsLegacyRequest() {
        final Address recipient = new Address("0x" + "1".repeat(40));
        TransactionRequest request =
                TxBuilder.legacy()
                        .from(new Address("0x" + "2".repeat(40)))
                        .to(recipient)
                        .value(Wei.of(5))
                        .gasPrice(Wei.of(10))
                        .gasLimit(21_000)
                        .nonce(7)
                        .build();

        assertEquals(recipient, request.to());
        assertEquals(Wei.of(5), request.value());
        assertEquals(Wei.of(10), request.gasPrice());
        assertEquals(21_000L, request.gasLimit());
        assertEquals(7L, request.nonce());
        assertNull(request.maxFeePerGas());
        assertNull(request.maxPriorityFeePerGas());
    }

    @Test
    void buildsEip1559Request() {
        final Address recipient = new Address("0x" + "3".repeat(40));
        TransactionRequest request =
                TxBuilder.eip1559()
                        .to(recipient)
                        .value(Wei.of(2))
                        .maxFeePerGas(Wei.of(30))
                        .maxPriorityFeePerGas(Wei.of(5))
                        .data(new HexData("0x1234"))
                        .build();

        assertEquals(recipient, request.to());
        assertEquals(Wei.of(2), request.value());
        assertEquals(Wei.of(30), request.maxFeePerGas());
        assertEquals(Wei.of(5), request.maxPriorityFeePerGas());
        assertNull(request.gasPrice());
    }
}
