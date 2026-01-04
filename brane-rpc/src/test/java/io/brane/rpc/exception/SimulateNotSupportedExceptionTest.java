package io.brane.rpc.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.brane.core.error.BraneException;
import io.brane.core.error.RpcException;

/**
 * Tests for {@link SimulateNotSupportedException}.
 * <p>
 * This exception is thrown when eth_simulateV1 is not supported by the RPC node
 * (error code -32601: "Method not found").
 */
class SimulateNotSupportedExceptionTest {

    private static final int METHOD_NOT_FOUND = -32601;

    @Test
    void testConstructorWithMessage() {
        String nodeMessage = "the method eth_simulateV1 does not exist/is not available";

        SimulateNotSupportedException exception = new SimulateNotSupportedException(nodeMessage);

        assertNotNull(exception);
        assertEquals(METHOD_NOT_FOUND, exception.code());
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("eth_simulateV1 is not supported"));
    }

    @Test
    void testExtendsRpcException() {
        SimulateNotSupportedException exception = new SimulateNotSupportedException("test");

        assertTrue(exception instanceof RpcException);
        assertTrue(exception instanceof BraneException);
    }

    @Test
    void testErrorCodeIsMethodNotFound() {
        SimulateNotSupportedException exception = new SimulateNotSupportedException("test");

        assertEquals(-32601, exception.code());
    }

    @Test
    void testMessageContainsHelpfulGuidance() {
        SimulateNotSupportedException exception = new SimulateNotSupportedException("method not found");

        String message = exception.getMessage().toLowerCase(); // Case-insensitive check

        // Should mention the method name
        assertTrue(message.contains("eth_simulatev1") || message.contains("eth_simulate"),
                "Message should mention eth_simulateV1");

        // Should mention it's not supported
        assertTrue(message.contains("not supported") || message.contains("does not support"),
                "Message should indicate lack of support");

        // Should mention compatible nodes/providers
        assertTrue(message.contains("geth") || message.contains("erigon") || 
                   message.contains("node") || message.contains("provider"),
                "Message should mention compatible RPC providers");

        // Should include original error
        assertTrue(message.contains("original error") || message.contains("method not found"),
                "Message should include original error message");
    }

    @Test
    void testOriginalMessageIsPreserved() {
        String originalMessage = "the method eth_simulateV1 does not exist/is not available";

        SimulateNotSupportedException exception = new SimulateNotSupportedException(originalMessage);

        assertTrue(exception.getMessage().contains(originalMessage),
                "Original error message should be preserved in the exception message");
    }

    @Test
    void testDataFieldIsNull() {
        SimulateNotSupportedException exception = new SimulateNotSupportedException("test");

        // Method not found errors typically don't have additional data
        assertNull(exception.data());
    }

    @Test
    void testRequestIdIsNull() {
        SimulateNotSupportedException exception = new SimulateNotSupportedException("test");

        // Request ID is not set in constructor
        assertNull(exception.requestId());
    }

    @Test
    void testDifferentNodeErrorMessages() {
        String[] nodeErrors = {
                "the method eth_simulateV1 does not exist/is not available",
                "Method not found",
                "method eth_simulateV1 not found",
                "eth_simulateV1 is not supported",
                "unknown method eth_simulateV1"
        };

        for (String nodeError : nodeErrors) {
            SimulateNotSupportedException exception = new SimulateNotSupportedException(nodeError);

            assertEquals(METHOD_NOT_FOUND, exception.code(),
                    "Should have method not found error code");
            assertTrue(exception.getMessage().contains(nodeError),
                    "Should preserve node error message: " + nodeError);
            assertTrue(exception.getMessage().contains("eth_simulateV1"),
                    "Should mention eth_simulateV1 in message");
        }
    }

    @Test
    void testNullMessage() {
        // Should handle null message gracefully
        assertDoesNotThrow(() -> {
            SimulateNotSupportedException exception = new SimulateNotSupportedException(null);
            assertNotNull(exception.getMessage());
            assertTrue(exception.getMessage().contains("eth_simulateV1"));
        });
    }

    @Test
    void testEmptyMessage() {
        SimulateNotSupportedException exception = new SimulateNotSupportedException("");

        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("eth_simulateV1"));
        // Even with empty original message, should provide helpful guidance
        assertTrue(exception.getMessage().length() > 50);
    }

    @Test
    void testToString() {
        SimulateNotSupportedException exception = new SimulateNotSupportedException("method not found");

        String toString = exception.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("code=" + METHOD_NOT_FOUND) ||
                        toString.contains("code " + METHOD_NOT_FOUND),
                "toString should include error code");
    }

    @Test
    void testMessageStructure() {
        String originalMessage = "method not found";
        SimulateNotSupportedException exception = new SimulateNotSupportedException(originalMessage);

        String message = exception.getMessage();

        // Should have a structured message with:
        // 1. Main error explanation
        // 2. Requirement/recommendation
        // 3. Original error reference

        String[] expectedParts = {
                "eth_simulateV1",
                "not supported",
                "Original error"
        };

        for (String part : expectedParts) {
            assertTrue(message.contains(part),
                    "Message should contain: " + part + "\nActual message: " + message);
        }
    }

    @Test
    void testComparableToRpcException() {
        // Should behave like a regular RpcException in error handling
        SimulateNotSupportedException simulateException = new SimulateNotSupportedException("test");
        RpcException rpcException = new RpcException(-32601, "method not found", null);

        // Both should have same error code
        assertEquals(rpcException.code(), simulateException.code());

        // Both should be catchable as RpcException
        try {
            throw simulateException;
        } catch (RpcException e) {
            assertEquals(METHOD_NOT_FOUND, e.code());
        }
    }

    @Test
    void testExceptionCanBeThrown() {
        assertThrows(SimulateNotSupportedException.class, () -> {
            throw new SimulateNotSupportedException("method not found");
        });
    }

    @Test
    void testExceptionCanBeCaughtAsRpcException() {
        try {
            throw new SimulateNotSupportedException("method not found");
        } catch (RpcException e) {
            // Should be caught as RpcException
            assertEquals(METHOD_NOT_FOUND, e.code());
            assertTrue(e instanceof SimulateNotSupportedException);
        }
    }

    @Test
    void testExceptionCanBeCaughtAsBraneException() {
        try {
            throw new SimulateNotSupportedException("method not found");
        } catch (BraneException e) {
            // Should be caught as BraneException
            assertTrue(e instanceof RpcException);
            assertTrue(e instanceof SimulateNotSupportedException);
        }
    }
}
