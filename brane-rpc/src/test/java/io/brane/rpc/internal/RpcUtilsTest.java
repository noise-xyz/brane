package io.brane.rpc.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.brane.core.model.AccessListEntry;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;

class RpcUtilsTest {

    @Test
    void toJsonAccessListConvertsEmptyList() {
        List<Map<String, Object>> result = RpcUtils.toJsonAccessList(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void toJsonAccessListConvertsSingleEntry() {
        Address address = new Address("0x" + "a".repeat(40));
        Hash storageKey = new Hash("0x" + "b".repeat(64));
        AccessListEntry entry = new AccessListEntry(address, List.of(storageKey));

        List<Map<String, Object>> result = RpcUtils.toJsonAccessList(List.of(entry));

        assertEquals(1, result.size());
        Map<String, Object> map = result.get(0);
        assertEquals(address.value(), map.get("address"));
        @SuppressWarnings("unchecked")
        List<String> storageKeys = (List<String>) map.get("storageKeys");
        assertEquals(1, storageKeys.size());
        assertEquals(storageKey.value(), storageKeys.get(0));
    }

    @Test
    void toJsonAccessListConvertsMultipleEntries() {
        Address addr1 = new Address("0x" + "1".repeat(40));
        Address addr2 = new Address("0x" + "2".repeat(40));
        Hash key1 = new Hash("0x" + "a".repeat(64));
        Hash key2 = new Hash("0x" + "b".repeat(64));
        Hash key3 = new Hash("0x" + "c".repeat(64));

        AccessListEntry entry1 = new AccessListEntry(addr1, List.of(key1, key2));
        AccessListEntry entry2 = new AccessListEntry(addr2, List.of(key3));

        List<Map<String, Object>> result = RpcUtils.toJsonAccessList(List.of(entry1, entry2));

        assertEquals(2, result.size());

        Map<String, Object> map1 = result.get(0);
        assertEquals(addr1.value(), map1.get("address"));
        @SuppressWarnings("unchecked")
        List<String> keys1 = (List<String>) map1.get("storageKeys");
        assertEquals(2, keys1.size());
        assertEquals(key1.value(), keys1.get(0));
        assertEquals(key2.value(), keys1.get(1));

        Map<String, Object> map2 = result.get(1);
        assertEquals(addr2.value(), map2.get("address"));
        @SuppressWarnings("unchecked")
        List<String> keys2 = (List<String>) map2.get("storageKeys");
        assertEquals(1, keys2.size());
        assertEquals(key3.value(), keys2.get(0));
    }

    @Test
    void toJsonAccessListConvertsEntryWithNoStorageKeys() {
        Address address = new Address("0x" + "a".repeat(40));
        AccessListEntry entry = new AccessListEntry(address, List.of());

        List<Map<String, Object>> result = RpcUtils.toJsonAccessList(List.of(entry));

        assertEquals(1, result.size());
        Map<String, Object> map = result.get(0);
        assertEquals(address.value(), map.get("address"));
        @SuppressWarnings("unchecked")
        List<String> storageKeys = (List<String>) map.get("storageKeys");
        assertTrue(storageKeys.isEmpty());
    }

    @Test
    void decodeHexBigIntegerHandlesNullAndEmpty() {
        // Explicit documentation: null/empty values return ZERO (lenient RPC parsing)
        assertEquals(BigInteger.ZERO, RpcUtils.decodeHexBigInteger(null));
        assertEquals(BigInteger.ZERO, RpcUtils.decodeHexBigInteger(""));
        assertEquals(BigInteger.ZERO, RpcUtils.decodeHexBigInteger("0x"));
        assertEquals(BigInteger.ZERO, RpcUtils.decodeHexBigInteger("0x0"));
    }

    @Test
    void decodeHexBigIntegerParsesValidHex() {
        assertEquals(BigInteger.valueOf(255), RpcUtils.decodeHexBigInteger("0xff"));
        assertEquals(BigInteger.valueOf(255), RpcUtils.decodeHexBigInteger("ff"));
        assertEquals(BigInteger.valueOf(1000000), RpcUtils.decodeHexBigInteger("0xf4240"));
    }

    @Test
    void toQuantityHexFormatsCorrectly() {
        assertEquals("0xff", RpcUtils.toQuantityHex(BigInteger.valueOf(255)));
        assertEquals("0x0", RpcUtils.toQuantityHex(BigInteger.ZERO));
        assertEquals("0xf4240", RpcUtils.toQuantityHex(BigInteger.valueOf(1000000)));
    }

    @Test
    void extractErrorDataReturnsNullForNull() {
        assertNull(RpcUtils.extractErrorData(null));
    }

    @Test
    void extractErrorDataReturnsHexStringDirectly() {
        assertEquals("0x1234", RpcUtils.extractErrorData("0x1234"));
        assertEquals("0xdeadbeef", RpcUtils.extractErrorData("0xdeadbeef"));
    }

    @Test
    void extractErrorDataConvertsNonHexStringToString() {
        // Non-hex strings are returned via toString fallback
        assertEquals("error message", RpcUtils.extractErrorData("error message"));
    }

    @Test
    void extractErrorDataExtractsHexFromNestedMap() {
        Map<String, Object> nested = Map.of("data", "0xdeadbeef");
        assertEquals("0xdeadbeef", RpcUtils.extractErrorData(nested));
    }

    @Test
    void extractErrorDataExtractsDeeplyNestedHex() {
        Map<String, Object> inner = Map.of("data", "0xabcdef");
        Map<String, Object> outer = Map.of("error", inner);
        assertEquals("0xabcdef", RpcUtils.extractErrorData(outer));
    }

    @Test
    void extractErrorDataExtractsFromList() {
        List<Object> list = List.of("not-hex", "0x1234");
        assertEquals("0x1234", RpcUtils.extractErrorData(list));
    }

    @Test
    void extractErrorDataFallsBackToStringForNoHex() {
        Map<String, Object> noHex = Map.of("message", "error occurred");
        String result = RpcUtils.extractErrorData(noHex);
        assertNotNull(result);
        assertTrue(result.contains("error occurred"));
    }

    /**
     * MED-3 Verification: Test if decodeHexLong throws or returns 0 for invalid input.
     *
     * The TODO claims invalid input silently becomes block 0.
     * Let's verify what actually happens.
     */
    @Test
    void verifyMed3_decodeHexLongWithInvalidInput() {
        // Test various invalid inputs
        String[] invalidInputs = {
            "garbage",
            "not_a_block",
            "latest_typo",
            "12345",  // Looks like decimal - no 0x prefix but valid hex chars
            "abc",    // Valid hex chars but no prefix
            "xyz",    // Invalid hex chars
        };

        for (String invalid : invalidInputs) {
            try {
                long result = RpcUtils.decodeHexLong(invalid);
                // If we get here without exception, check what value we got
                System.out.println("MED-3: Input '" + invalid + "' -> " + result + " (no exception)");

                // For inputs that contain only valid hex chars (even without 0x prefix),
                // Long.parseLong will succeed. This is expected behavior.
                if (invalid.matches("[0-9a-fA-F]+")) {
                    System.out.println("  -> This is valid hex (just missing 0x prefix)");
                }
            } catch (NumberFormatException e) {
                // This is the expected behavior for truly invalid input
                System.out.println("MED-3: Input '" + invalid + "' -> NumberFormatException: " + e.getMessage());
            }
        }
    }

    @Test
    void verifyMed3_decodeHexLongHandlesNullAndEmptyAsZero() {
        // These cases DO return 0 - this is documented behavior
        assertEquals(0L, RpcUtils.decodeHexLong(null));
        assertEquals(0L, RpcUtils.decodeHexLong(""));
        assertEquals(0L, RpcUtils.decodeHexLong("0x"));

        System.out.println("MED-3 Note: null, empty, and '0x' return 0 - this is DOCUMENTED behavior");
    }

    @Test
    void verifyMed3_invalidHexThrowsNumberFormatException() {
        // Truly invalid input SHOULD throw NumberFormatException
        assertThrows(NumberFormatException.class, () -> {
            RpcUtils.decodeHexLong("garbage");
        }, "Invalid hex 'garbage' should throw NumberFormatException");

        assertThrows(NumberFormatException.class, () -> {
            RpcUtils.decodeHexLong("xyz");
        }, "Invalid hex 'xyz' should throw NumberFormatException");

        System.out.println("MED-3 Verification: Invalid hex DOES throw NumberFormatException - NOT A BUG");
    }
}
