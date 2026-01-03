package io.brane.core.abi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.brane.core.error.AbiDecodingException;
import io.brane.core.error.AbiEncodingException;
import io.brane.core.model.LogEntry;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;

class AbiTest {

    @Test
    void parsesStateMutabilityWithFallbacks() {
        final String abiJson =
                """
                [
                  {
                    "name": "status",
                    "type": "function",
                    "stateMutability": "view",
                    "inputs": [],
                    "outputs": [{"name": "", "type": "bool"}]
                  },
                  {
                    "name": "setStatus",
                    "type": "function",
                    "constant": false,
                    "inputs": [{"name": "value", "type": "bool"}],
                    "outputs": []
                  }
                ]
                """;

        final Abi abi = Abi.fromJson(abiJson);
        final Abi.FunctionMetadata status =
                abi.getFunction("status").orElseThrow(() -> new AssertionError("status missing"));
        assertEquals("view", status.stateMutability());
        assertTrue(status.isView());

        final Abi.FunctionMetadata setStatus =
                abi.getFunction("setStatus").orElseThrow(() -> new AssertionError("setStatus missing"));
        assertEquals("nonpayable", setStatus.stateMutability());
        assertFalse(setStatus.isView());
    }

    @Test
    void rejectsOverloadedFunctions() {
        final String abiJson =
                """
                [
                  {"type": "function", "name": "foo", "inputs": [], "outputs": []},
                  {"type": "function", "name": "foo", "inputs": [{"name": "a", "type": "uint256"}], "outputs": []}
                ]
                """;

        assertThrows(AbiEncodingException.class, () -> Abi.fromJson(abiJson));
    }

    @Test
    void decodeMulticallResultsThrowsOnNullInput() {
        // CRIT-2: Verify null input throws AbiDecodingException (not NPE wrapped in generic exception)
        AbiDecodingException ex = assertThrows(
                AbiDecodingException.class,
                () -> Abi.decodeMulticallResults(null));
        assertTrue(ex.getMessage().contains("empty or null"));
    }

    @Test
    void decodeMulticallResultsThrowsOnEmptyInput() {
        // CRIT-2: Verify empty data throws AbiDecodingException
        AbiDecodingException ex = assertThrows(
                AbiDecodingException.class,
                () -> Abi.decodeMulticallResults("0x"));
        assertTrue(ex.getMessage().contains("empty data"));
    }

    @Test
    void decodeMulticallResultsThrowsOnMalformedData() {
        // CRIT-2: Verify malformed data throws AbiDecodingException (not generic Exception wrapper)
        // This hex is too short to be valid multicall result
        AbiDecodingException ex = assertThrows(
                AbiDecodingException.class,
                () -> Abi.decodeMulticallResults("0x1234"));
        // Should be a decode error, not a wrapped NPE or ClassCastException
        assertTrue(ex.getCause() == null ||
                   ex.getCause() instanceof IllegalArgumentException ||
                   ex.getCause() instanceof ArrayIndexOutOfBoundsException);
    }

    // ERC-20 Transfer event ABI for testing
    private static final String TRANSFER_EVENT_ABI = """
            [
              {
                "anonymous": false,
                "inputs": [
                  {"indexed": true, "name": "from", "type": "address"},
                  {"indexed": true, "name": "to", "type": "address"},
                  {"indexed": false, "name": "value", "type": "uint256"}
                ],
                "name": "Transfer",
                "type": "event"
              }
            ]
            """;

    // Transfer event topic0 = keccak256("Transfer(address,address,uint256)")
    private static final String TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    @Test
    void decodeEventsToListClass() {
        final Abi abi = Abi.fromJson(TRANSFER_EVENT_ABI);

        // Create a Transfer event log
        // topic[0] = event signature
        // topic[1] = indexed from address (padded to 32 bytes)
        // topic[2] = indexed to address (padded to 32 bytes)
        // data = non-indexed value (uint256)
        final LogEntry log = new LogEntry(
                new Address("0x0000000000000000000000000000000000000000"),
                new HexData("0x00000000000000000000000000000000000000000000000000000000000f4240"), // 1000000
                List.of(
                        new Hash(TRANSFER_TOPIC),
                        new Hash("0x0000000000000000000000001111111111111111111111111111111111111111"),
                        new Hash("0x0000000000000000000000002222222222222222222222222222222222222222")),
                new Hash("0x0000000000000000000000000000000000000000000000000000000000000001"),
                new Hash("0x0000000000000000000000000000000000000000000000000000000000000002"),
                0,
                false);

        // Test decoding to List.class - should work without accessibility issues
        List<List> events = assertDoesNotThrow(() -> abi.decodeEvents("Transfer", List.of(log), List.class));
        assertEquals(1, events.size());
        List<?> event = events.get(0);
        assertEquals(3, event.size());
    }

    @Test
    void decodeEventsToObjectArray() {
        final Abi abi = Abi.fromJson(TRANSFER_EVENT_ABI);

        final LogEntry log = new LogEntry(
                new Address("0x0000000000000000000000000000000000000000"),
                new HexData("0x00000000000000000000000000000000000000000000000000000000000f4240"),
                List.of(
                        new Hash(TRANSFER_TOPIC),
                        new Hash("0x0000000000000000000000001111111111111111111111111111111111111111"),
                        new Hash("0x0000000000000000000000002222222222222222222222222222222222222222")),
                new Hash("0x0000000000000000000000000000000000000000000000000000000000000001"),
                new Hash("0x0000000000000000000000000000000000000000000000000000000000000002"),
                0,
                false);

        // Test decoding to Object[].class - should work without accessibility issues
        List<Object[]> events = assertDoesNotThrow(() -> abi.decodeEvents("Transfer", List.of(log), Object[].class));
        assertEquals(1, events.size());
        Object[] event = events.get(0);
        assertEquals(3, event.length);
    }

    @Test
    void decodeEventsToPublicRecord() {
        final Abi abi = Abi.fromJson(TRANSFER_EVENT_ABI);

        final LogEntry log = new LogEntry(
                new Address("0x0000000000000000000000000000000000000000"),
                new HexData("0x00000000000000000000000000000000000000000000000000000000000f4240"),
                List.of(
                        new Hash(TRANSFER_TOPIC),
                        new Hash("0x0000000000000000000000001111111111111111111111111111111111111111"),
                        new Hash("0x0000000000000000000000002222222222222222222222222222222222222222")),
                new Hash("0x0000000000000000000000000000000000000000000000000000000000000001"),
                new Hash("0x0000000000000000000000000000000000000000000000000000000000000002"),
                0,
                false);

        // Test decoding to a public record - should work as this is in an open module (test code)
        List<TransferEvent> events = assertDoesNotThrow(
                () -> abi.decodeEvents("Transfer", List.of(log), TransferEvent.class));
        assertEquals(1, events.size());
        TransferEvent event = events.get(0);
        assertEquals(new Address("0x1111111111111111111111111111111111111111"), event.from());
        assertEquals(new Address("0x2222222222222222222222222222222222222222"), event.to());
        assertEquals(BigInteger.valueOf(1000000), event.value());
    }

    @Test
    void decodeEventsReturnsEmptyListForNonMatchingLogs() {
        final Abi abi = Abi.fromJson(TRANSFER_EVENT_ABI);

        // Log with different topic (not a Transfer event)
        final LogEntry nonMatchingLog = new LogEntry(
                new Address("0x0000000000000000000000000000000000000000"),
                new HexData("0x00000000000000000000000000000000000000000000000000000000000f4240"),
                List.of(new Hash("0x0000000000000000000000000000000000000000000000000000000000000000")),
                new Hash("0x0000000000000000000000000000000000000000000000000000000000000001"),
                new Hash("0x0000000000000000000000000000000000000000000000000000000000000002"),
                0,
                false);

        List<List> events = abi.decodeEvents("Transfer", List.of(nonMatchingLog), List.class);
        assertTrue(events.isEmpty());
    }

    /**
     * Public record for testing event decoding.
     *
     * <p>
     * Note: This record must be public for reflection-based instantiation to work
     * in all module configurations. This demonstrates the documented requirement
     * for event types.
     */
    public record TransferEvent(Address from, Address to, BigInteger value) {
    }
}
