// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class KzgExceptionTest {

    @Test
    void extendsBraneException() {
        KzgException ex = new KzgException(KzgException.Kind.INVALID_BLOB, "test");
        assertInstanceOf(BraneException.class, ex);
    }

    @Test
    void kindAccessorReturnsCorrectValue() {
        KzgException ex = new KzgException(KzgException.Kind.PROOF_ERROR, "test message");
        assertEquals(KzgException.Kind.PROOF_ERROR, ex.kind());
        assertEquals("test message", ex.getMessage());
    }

    @Test
    void constructorWithCausePreservesCause() {
        var cause = new RuntimeException("underlying cause");
        KzgException ex = new KzgException(KzgException.Kind.SETUP_ERROR, "test", cause);

        assertEquals(KzgException.Kind.SETUP_ERROR, ex.kind());
        assertSame(cause, ex.getCause());
    }

    @Test
    void invalidBlobFactoryReturnsCorrectKind() {
        KzgException ex = KzgException.invalidBlob("blob too large");

        assertEquals(KzgException.Kind.INVALID_BLOB, ex.kind());
        assertEquals("blob too large", ex.getMessage());
    }

    @Test
    void invalidProofFactoryReturnsCorrectKind() {
        KzgException ex = KzgException.invalidProof("proof verification failed");

        assertEquals(KzgException.Kind.INVALID_PROOF, ex.kind());
        assertEquals("proof verification failed", ex.getMessage());
    }

    @Test
    void setupErrorFactoryReturnsCorrectKind() {
        KzgException ex = KzgException.setupError("failed to load trusted setup");

        assertEquals(KzgException.Kind.SETUP_ERROR, ex.kind());
        assertEquals("failed to load trusted setup", ex.getMessage());
    }

    @Test
    void setupErrorWithCauseFactoryReturnsCorrectKind() {
        var cause = new RuntimeException("file not found");
        KzgException ex = KzgException.setupError("failed to load trusted setup", cause);

        assertEquals(KzgException.Kind.SETUP_ERROR, ex.kind());
        assertEquals("failed to load trusted setup", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void commitmentErrorFactoryReturnsCorrectKind() {
        KzgException ex = KzgException.commitmentError("commitment computation failed");

        assertEquals(KzgException.Kind.COMMITMENT_ERROR, ex.kind());
        assertEquals("commitment computation failed", ex.getMessage());
    }

    @Test
    void proofErrorFactoryReturnsCorrectKind() {
        KzgException ex = KzgException.proofError("proof computation failed");

        assertEquals(KzgException.Kind.PROOF_ERROR, ex.kind());
        assertEquals("proof computation failed", ex.getMessage());
    }
}
