// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.brane.core.types.Wei;

class ContractOptionsTest {

    @Test
    void equalsReturnsTrueForIdenticalOptions() {
        ContractOptions options1 = ContractOptions.builder()
                .gasLimit(500_000L)
                .timeout(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .transactionType(ContractOptions.TransactionType.EIP1559)
                .maxPriorityFee(Wei.gwei(3))
                .build();

        ContractOptions options2 = ContractOptions.builder()
                .gasLimit(500_000L)
                .timeout(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .transactionType(ContractOptions.TransactionType.EIP1559)
                .maxPriorityFee(Wei.gwei(3))
                .build();

        assertEquals(options1, options2);
        assertEquals(options1.hashCode(), options2.hashCode());
    }

    @Test
    void equalsReturnsFalseForDifferentGasLimit() {
        ContractOptions options1 = ContractOptions.builder().gasLimit(100_000L).build();
        ContractOptions options2 = ContractOptions.builder().gasLimit(200_000L).build();

        assertNotEquals(options1, options2);
    }

    @Test
    void equalsReturnsFalseForDifferentTimeout() {
        ContractOptions options1 = ContractOptions.builder().timeout(Duration.ofSeconds(10)).build();
        ContractOptions options2 = ContractOptions.builder().timeout(Duration.ofSeconds(20)).build();

        assertNotEquals(options1, options2);
    }

    @Test
    void equalsReturnsFalseForDifferentTransactionType() {
        ContractOptions options1 = ContractOptions.builder()
                .transactionType(ContractOptions.TransactionType.EIP1559).build();
        ContractOptions options2 = ContractOptions.builder()
                .transactionType(ContractOptions.TransactionType.LEGACY).build();

        assertNotEquals(options1, options2);
    }

    @Test
    void equalsReturnsFalseForDifferentMaxPriorityFee() {
        ContractOptions options1 = ContractOptions.builder().maxPriorityFee(Wei.gwei(1)).build();
        ContractOptions options2 = ContractOptions.builder().maxPriorityFee(Wei.gwei(5)).build();

        assertNotEquals(options1, options2);
    }

    @Test
    void equalsReturnsFalseForNull() {
        ContractOptions options = ContractOptions.defaults();
        assertNotEquals(null, options);
    }

    @Test
    void equalsReturnsFalseForDifferentType() {
        ContractOptions options = ContractOptions.defaults();
        assertNotEquals("not an options object", options);
    }

    @Test
    void defaultsAreEqual() {
        assertEquals(ContractOptions.defaults(), ContractOptions.defaults());
    }

    @Test
    void toStringContainsAllFields() {
        ContractOptions options = ContractOptions.builder()
                .gasLimit(500_000L)
                .timeout(Duration.ofSeconds(30))
                .build();

        String str = options.toString();
        assertTrue(str.contains("gasLimit=500000"));
        assertTrue(str.contains("timeout="));
        assertTrue(str.contains("transactionType="));
        assertTrue(str.contains("maxPriorityFee="));
    }

    @Test
    void zeroTimeoutThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                ContractOptions.builder().timeout(Duration.ZERO));
    }

    @Test
    void negativeTimeoutThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                ContractOptions.builder().timeout(Duration.ofSeconds(-1)));
    }

    @Test
    void negativePollIntervalThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                ContractOptions.builder().pollInterval(Duration.ofMillis(-1)));
    }

    @Test
    void zeroPollIntervalThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                ContractOptions.builder().pollInterval(Duration.ZERO));
    }

    @Test
    void negativeGasLimitThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                ContractOptions.builder().gasLimit(-1));
    }

    @Test
    void zeroGasLimitThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                ContractOptions.builder().gasLimit(0));
    }
}
