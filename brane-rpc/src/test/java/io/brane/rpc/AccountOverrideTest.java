package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

class AccountOverrideTest {

    @Test
    void testValidAccountOverrideCreation() {
        Wei balance = Wei.of(1000);
        long nonce = 5L;
        HexData code = new HexData("0x600160005260206000f3");
        Hash slot = new Hash("0x" + "1".repeat(64));
        Hash value = new Hash("0x" + "2".repeat(64));

        AccountOverride override = AccountOverride.builder()
                .balance(balance)
                .nonce(nonce)
                .code(code)
                .putStateDiff(slot, value)
                .build();

        assertNotNull(override);
        assertEquals(balance, override.balance());
        assertEquals(nonce, override.nonce());
        assertEquals(code, override.code());
        assertNotNull(override.stateDiff());
        assertEquals(value, override.stateDiff().get(slot));
    }

    @Test
    void testToMapForJsonRpc() {
        Wei balance = Wei.of(1000);
        long nonce = 42L;
        HexData code = new HexData("0x1234");
        Hash slot = new Hash("0x" + "a".repeat(64));
        Hash val = new Hash("0x" + "b".repeat(64));

        AccountOverride override = AccountOverride.builder()
                .balance(balance)
                .nonce(nonce)
                .code(code)
                .putStateDiff(slot, val)
                .build();

        Map<String, Object> map = override.toMap();

        assertEquals("0x3e8", map.get("balance"));
        assertEquals("0x2a", map.get("nonce"));
        assertEquals("0x1234", map.get("code"));

        @SuppressWarnings("unchecked")
        Map<String, String> stateDiffMap = (Map<String, String>) map.get("stateDiff");
        assertNotNull(stateDiffMap);
        assertEquals(val.value(), stateDiffMap.get(slot.value()));
    }

    @Test
    void testDefensiveCopies() {
        Map<Hash, Hash> mutableMap = new HashMap<>();
        Hash slot = new Hash("0x" + "1".repeat(64));
        Hash val = new Hash("0x" + "2".repeat(64));
        mutableMap.put(slot, val);

        AccountOverride override = AccountOverride.builder()
                .stateDiff(mutableMap)
                .build();

        // Modify original map
        mutableMap.put(new Hash("0x" + "3".repeat(64)), val);

        // Verify override didn't change
        assertEquals(1, override.stateDiff().size());

        // Verify result of stateDiff() is immutable
        assertThrows(UnsupportedOperationException.class, () ->
            override.stateDiff().put(new Hash("0x" + "4".repeat(64)), val));
    }

    @Test
    void testEmptyStateDiffNotSerialized() {
        // Case 1: null stateDiff
        AccountOverride override1 = AccountOverride.builder().build();
        assertFalse(override1.toMap().containsKey("stateDiff"));

        // Case 2: empty stateDiff
        AccountOverride override2 = AccountOverride.builder().stateDiff(new HashMap<>()).build();
        assertFalse(override2.toMap().containsKey("stateDiff"));
    }
}
