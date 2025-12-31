package io.brane.rpc.internal;

import static org.junit.jupiter.api.Assertions.*;

import io.brane.core.model.AccessListEntry;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
        assertEquals(BigInteger.ZERO, RpcUtils.decodeHexBigInteger(null));
        assertEquals(BigInteger.ZERO, RpcUtils.decodeHexBigInteger(""));
        assertEquals(BigInteger.ZERO, RpcUtils.decodeHexBigInteger("0x"));
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
}
