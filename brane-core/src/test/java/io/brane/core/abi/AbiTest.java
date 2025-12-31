package io.brane.core.abi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.brane.core.error.AbiDecodingException;
import io.brane.core.error.AbiEncodingException;
import org.junit.jupiter.api.Test;

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
}

