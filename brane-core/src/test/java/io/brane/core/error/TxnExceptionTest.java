// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.error;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TxnExceptionTest {

    @Test
    void detectsInvalidSender() {
        TxnException ex = new TxnException("Invalid sender for transaction");
        assertTrue(ex.isInvalidSender());
    }

    @Test
    void detectsChainIdMismatch() {
        TxnException ex = new TxnException("Chain ID mismatch: expected 1 but connected to 5");
        assertTrue(ex.isChainIdMismatch());
    }

    @Test
    void ignoresUnrelatedMessages() {
        TxnException ex = new TxnException("Some other error");
        assertFalse(ex.isInvalidSender());
        assertFalse(ex.isChainIdMismatch());
    }
}
