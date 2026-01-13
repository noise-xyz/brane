// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import sh.brane.core.model.AssetChange;
import sh.brane.core.model.AssetToken;
import sh.brane.core.model.AssetValue;
import sh.brane.core.types.Address;
import sh.brane.core.types.HexData;

class SimulateResultTest {

    private static final Address TOKEN_ADDRESS = new Address("0x1234567890123456789012345678901234567890");

    @Test
    void testValidResultCreation() {
        CallResult success = new CallResult.Success(BigInteger.valueOf(21000L), List.of(), new HexData("0x01"));
        CallResult failure = new CallResult.Failure(BigInteger.valueOf(1000L), List.of(), "execution reverted", null);
        List<CallResult> results = List.of(success, failure);

        AssetToken token = new AssetToken(TOKEN_ADDRESS, 18, "WETH");
        AssetValue value = new AssetValue(BigInteger.ZERO, BigInteger.TEN, BigInteger.TEN);
        AssetChange change = new AssetChange(token, value);
        List<AssetChange> changes = List.of(change);

        SimulateResult simulateResult = new SimulateResult(results, changes);

        assertNotNull(simulateResult);
        assertEquals(2, simulateResult.results().size());
        assertEquals(success, simulateResult.results().get(0));
        assertEquals(failure, simulateResult.results().get(1));
        assertNotNull(simulateResult.assetChanges());
        assertEquals(1, simulateResult.assetChanges().size());
        assertEquals(change, simulateResult.assetChanges().get(0));
    }

    @Test
    void testResultWithoutAssetChanges() {
        CallResult success = new CallResult.Success(BigInteger.valueOf(21000L), List.of(), null);
        SimulateResult simulateResult = new SimulateResult(List.of(success), null);

        assertNotNull(simulateResult);
        assertEquals(1, simulateResult.results().size());
        assertNull(simulateResult.assetChanges());
    }

    @Test
    void testRejectsNullResults() {
        assertThrows(NullPointerException.class, () -> new SimulateResult(null, null));
    }

    @Test
    void testDefensiveCopies() {
        List<CallResult> mutableResults = new ArrayList<>();
        mutableResults.add(new CallResult.Success(BigInteger.ONE, List.of(), null));

        List<AssetChange> mutableChanges = new ArrayList<>();
        AssetToken token = new AssetToken(TOKEN_ADDRESS, 18, "WETH");
        AssetValue val = new AssetValue(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE);
        mutableChanges.add(new AssetChange(token, val));

        SimulateResult simulateResult = new SimulateResult(mutableResults, mutableChanges);

        // Modify original lists
        mutableResults.add(new CallResult.Success(BigInteger.TEN, List.of(), null));
        mutableChanges.clear();

        // Verify simulateResult didn't change
        assertEquals(1, simulateResult.results().size());
        assertNotNull(simulateResult.assetChanges());
        assertEquals(1, simulateResult.assetChanges().size());
    }

    @Test
    void testAssetChangeMissingValueField() {
        // Construct response with assetChanges where 'value' field is missing
        var assetChangeWithoutValue = new java.util.LinkedHashMap<String, Object>();
        assetChangeWithoutValue.put("token", "0x1234567890123456789012345678901234567890");
        assetChangeWithoutValue.put("decimals", 18);
        assetChangeWithoutValue.put("symbol", "WETH");
        // Intentionally omit 'value' field

        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("results", java.util.List.of(
                java.util.Map.of("status", "0x1", "gasUsed", "0x5208", "returnData", "0x")
        ));
        map.put("assetChanges", java.util.List.of(assetChangeWithoutValue));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SimulateResult.fromMap(map));
        assertTrue(ex.getMessage().contains("value"));
    }
}
