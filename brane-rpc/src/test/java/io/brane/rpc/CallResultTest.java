package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.brane.core.model.LogEntry;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;

class CallResultTest {

    private static final Address TEST_ADDRESS = new Address("0x1234567890123456789012345678901234567890");
    private static final Hash TEST_HASH = new Hash("0x1234567890123456789012345678901234567890123456789012345678901234");
    private static final BigInteger TEST_GAS = BigInteger.valueOf(21000);

    @Test
    void testSuccessWithReturnData() {
        HexData returnData = new HexData("0xabcdef");
        List<LogEntry> logs = List.of();

        CallResult.Success success = new CallResult.Success(TEST_GAS, logs, returnData);

        assertEquals(TEST_GAS, success.gasUsed());
        assertEquals(logs, success.logs());
        assertEquals(returnData, success.returnData());
    }

    @Test
    void testSuccessWithoutReturnData() {
        List<LogEntry> logs = List.of();

        CallResult.Success success = new CallResult.Success(TEST_GAS, logs, null);

        assertEquals(TEST_GAS, success.gasUsed());
        assertEquals(logs, success.logs());
        assertNull(success.returnData());
    }

    @Test
    void testSuccessWithLogs() {
        LogEntry log = new LogEntry(
                TEST_ADDRESS,
                new HexData("0x1234"),
                List.of(TEST_HASH),
                TEST_HASH,
                TEST_HASH,
                0L,
                false
        );
        List<LogEntry> logs = List.of(log);

        CallResult.Success success = new CallResult.Success(TEST_GAS, logs, null);

        assertEquals(1, success.logs().size());
        assertEquals(log, success.logs().get(0));
    }

    @Test
    void testSuccessLogsAreImmutable() {
        // Create a mutable list
        List<LogEntry> mutableLogs = new ArrayList<>();
        LogEntry log = new LogEntry(
                TEST_ADDRESS,
                new HexData("0x1234"),
                List.of(TEST_HASH),
                TEST_HASH,
                TEST_HASH,
                0L,
                false
        );
        mutableLogs.add(log);

        CallResult.Success success = new CallResult.Success(TEST_GAS, mutableLogs, null);

        // Try to modify the original list - should not affect the Success record
        mutableLogs.clear();
        assertEquals(1, success.logs().size(), "Success logs should be defensively copied");

        // Try to modify the logs from the record - should throw
        assertThrows(UnsupportedOperationException.class, () ->
                success.logs().clear()
        );
    }

    @Test
    void testSuccessRequiresGasUsed() {
        assertThrows(NullPointerException.class, () ->
                new CallResult.Success(null, List.of(), null)
        );
    }

    @Test
    void testSuccessRequiresLogs() {
        assertThrows(NullPointerException.class, () ->
                new CallResult.Success(TEST_GAS, null, null)
        );
    }

    @Test
    void testFailureWithRevertData() {
        HexData revertData = new HexData("0x08c379a0"); // Error(string) selector
        List<LogEntry> logs = List.of();
        String errorMessage = "execution reverted";

        CallResult.Failure failure = new CallResult.Failure(TEST_GAS, logs, errorMessage, revertData);

        assertEquals(TEST_GAS, failure.gasUsed());
        assertEquals(logs, failure.logs());
        assertEquals(errorMessage, failure.errorMessage());
        assertEquals(revertData, failure.revertData());
    }

    @Test
    void testFailureWithoutRevertData() {
        List<LogEntry> logs = List.of();
        String errorMessage = "out of gas";

        CallResult.Failure failure = new CallResult.Failure(TEST_GAS, logs, errorMessage, null);

        assertEquals(TEST_GAS, failure.gasUsed());
        assertEquals(logs, failure.logs());
        assertEquals(errorMessage, failure.errorMessage());
        assertNull(failure.revertData());
    }

    @Test
    void testFailureWithLogsBeforeRevert() {
        LogEntry log = new LogEntry(
                TEST_ADDRESS,
                new HexData("0x1234"),
                List.of(TEST_HASH),
                TEST_HASH,
                TEST_HASH,
                0L,
                false
        );
        List<LogEntry> logs = List.of(log);
        String errorMessage = "insufficient balance";

        CallResult.Failure failure = new CallResult.Failure(TEST_GAS, logs, errorMessage, null);

        assertEquals(1, failure.logs().size());
        assertEquals(log, failure.logs().get(0));
    }

    @Test
    void testFailureLogsAreImmutable() {
        // Create a mutable list
        List<LogEntry> mutableLogs = new ArrayList<>();
        LogEntry log = new LogEntry(
                TEST_ADDRESS,
                new HexData("0x1234"),
                List.of(TEST_HASH),
                TEST_HASH,
                TEST_HASH,
                0L,
                false
        );
        mutableLogs.add(log);

        CallResult.Failure failure = new CallResult.Failure(TEST_GAS, mutableLogs, "error", null);

        // Try to modify the original list - should not affect the Failure record
        mutableLogs.clear();
        assertEquals(1, failure.logs().size(), "Failure logs should be defensively copied");

        // Try to modify the logs from the record - should throw
        assertThrows(UnsupportedOperationException.class, () ->
                failure.logs().clear()
        );
    }

    @Test
    void testFailureRequiresGasUsed() {
        assertThrows(NullPointerException.class, () ->
                new CallResult.Failure(null, List.of(), "error", null)
        );
    }

    @Test
    void testFailureRequiresLogs() {
        assertThrows(NullPointerException.class, () ->
                new CallResult.Failure(TEST_GAS, null, "error", null)
        );
    }

    @Test
    void testFailureRequiresErrorMessage() {
        assertThrows(NullPointerException.class, () ->
                new CallResult.Failure(TEST_GAS, List.of(), null, null)
        );
    }

    @Test
    void testSuccessIsCallResult() {
        CallResult result = new CallResult.Success(TEST_GAS, List.of(), null);
        assertTrue(result instanceof CallResult);
        assertTrue(result instanceof CallResult.Success);
    }

    @Test
    void testFailureIsCallResult() {
        CallResult result = new CallResult.Failure(TEST_GAS, List.of(), "error", null);
        assertTrue(result instanceof CallResult);
        assertTrue(result instanceof CallResult.Failure);
    }

    @Test
    void testPatternMatchingOnSuccess() {
        CallResult result = new CallResult.Success(TEST_GAS, List.of(), new HexData("0xabcd"));

        switch (result) {
            case CallResult.Success success -> {
                assertEquals(TEST_GAS, success.gasUsed());
                assertEquals(new HexData("0xabcd"), success.returnData());
            }
            case CallResult.Failure failure -> {
                fail("Expected Success but got Failure");
            }
        }
    }

    @Test
    void testPatternMatchingOnFailure() {
        CallResult result = new CallResult.Failure(TEST_GAS, List.of(), "execution reverted", new HexData("0x08c379a0"));

        switch (result) {
            case CallResult.Success success -> {
                fail("Expected Failure but got Success");
            }
            case CallResult.Failure failure -> {
                assertEquals(TEST_GAS, failure.gasUsed());
                assertEquals("execution reverted", failure.errorMessage());
                assertEquals(new HexData("0x08c379a0"), failure.revertData());
            }
        }
    }

    @Test
    void testInstanceofPatternMatching() {
        CallResult result = new CallResult.Success(TEST_GAS, List.of(), null);

        if (result instanceof CallResult.Success success) {
            assertEquals(TEST_GAS, success.gasUsed());
            assertNull(success.returnData());
        } else {
            fail("Expected Success");
        }
    }

    @Test
    void testInterfaceMethodAccessibility() {
        CallResult success = new CallResult.Success(BigInteger.valueOf(50000), List.of(), null);
        CallResult failure = new CallResult.Failure(BigInteger.valueOf(30000), List.of(), "error", null);

        // Both should be accessible via the interface
        assertEquals(BigInteger.valueOf(50000), success.gasUsed());
        assertEquals(BigInteger.valueOf(30000), failure.gasUsed());

        assertTrue(success.logs().isEmpty());
        assertTrue(failure.logs().isEmpty());
    }
}
