// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link JsonRpcResponse}.
 */
class JsonRpcResponseTest {

    @Test
    void resultAsMap_withStringKeys_returnsMap() {
        Map<String, Object> map = Map.of("key1", "value1", "key2", 123);
        JsonRpcResponse response = new JsonRpcResponse("2.0", map, null, "1");

        Map<String, Object> result = response.resultAsMap();

        assertNotNull(result);
        assertEquals("value1", result.get("key1"));
        assertEquals(123, result.get("key2"));
    }

    @Test
    void resultAsMap_withNullResult_returnsNull() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");

        assertNull(response.resultAsMap());
    }

    @Test
    void resultAsMap_withNonStringKeys_throwsIllegalArgumentException() {
        // Simulate a map with Integer keys (could happen with malformed RPC response)
        Map<Integer, Object> mapWithIntKeys = new HashMap<>();
        mapWithIntKeys.put(1, "value1");
        mapWithIntKeys.put(2, "value2");

        JsonRpcResponse response = new JsonRpcResponse("2.0", mapWithIntKeys, null, "1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, response::resultAsMap);
        assertTrue(ex.getMessage().contains("non-String key"));
        assertTrue(ex.getMessage().contains("Integer"));
    }

    @Test
    void resultAsMap_withMixedKeys_throwsIllegalArgumentException() {
        // Map with mixed String and Integer keys
        Map<Object, Object> mixedMap = new HashMap<>();
        mixedMap.put("stringKey", "value1");
        mixedMap.put(42, "value2");

        JsonRpcResponse response = new JsonRpcResponse("2.0", mixedMap, null, "1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, response::resultAsMap);
        assertTrue(ex.getMessage().contains("non-String key"));
    }

    @Test
    void resultAsMap_withNullKey_isAllowed() {
        // Null keys should be allowed (weird but valid in some edge cases)
        Map<String, Object> mapWithNullKey = new HashMap<>();
        mapWithNullKey.put(null, "nullKeyValue");
        mapWithNullKey.put("normalKey", "normalValue");

        JsonRpcResponse response = new JsonRpcResponse("2.0", mapWithNullKey, null, "1");

        Map<String, Object> result = response.resultAsMap();
        assertNotNull(result);
        assertEquals("nullKeyValue", result.get(null));
    }

    @Test
    void resultAsList_withList_returnsList() {
        List<Object> list = List.of("item1", "item2", 123);
        JsonRpcResponse response = new JsonRpcResponse("2.0", list, null, "1");

        List<Object> result = response.resultAsList();

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("item1", result.get(0));
    }

    @Test
    void resultAsString_returnsStringRepresentation() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x1234", null, "1");

        assertEquals("0x1234", response.resultAsString());
    }

    @Test
    void hasError_withError_returnsTrue() {
        JsonRpcError error = new JsonRpcError(-32600, "Invalid Request", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");

        assertTrue(response.hasError());
    }

    @Test
    void hasError_withoutError_returnsFalse() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", "result", null, "1");

        assertFalse(response.hasError());
    }
}
