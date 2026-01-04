package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.brane.core.types.Address;
import io.brane.core.types.Wei;

class SimulateRequestTest {

    private static final Address ACCOUNT = new Address("0x1234567890123456789012345678901234567890");
    private static final Address TO = new Address("0x0987654321098765432109876543210987654321");

    @Test
    void testValidRequestCreation() {
        SimulateCall call = SimulateCall.builder().to(TO).build();
        AccountOverride override = AccountOverride.builder().balance(Wei.of(100)).build();

        SimulateRequest request = SimulateRequest.builder()
                .account(ACCOUNT)
                .call(call)
                .blockTag(BlockTag.LATEST)
                .stateOverride(TO, override)
                .traceAssetChanges(true)
                .traceTransfers(true)
                .validation(false)
                .fetchTokenMetadata(true)
                .build();

        assertNotNull(request);
        assertEquals(ACCOUNT, request.account());
        assertEquals(1, request.calls().size());
        assertEquals(call, request.calls().get(0));
        assertEquals(BlockTag.LATEST, request.blockTag());
        assertNotNull(request.stateOverrides());
        assertEquals(override, request.stateOverrides().get(TO));
        assertTrue(request.traceAssetChanges());
        assertTrue(request.traceTransfers());
        assertFalse(request.validation());
        assertTrue(request.fetchTokenMetadata());
    }

    @Test
    void testRejectsNullCalls() {
        assertThrows(NullPointerException.class, () -> new SimulateRequest(null, null, null, null, false, false, false, false));
    }

    @Test
    void testRejectsEmptyCalls() {
        assertThrows(IllegalArgumentException.class, () -> SimulateRequest.builder().calls(List.of()).build());
        assertThrows(IllegalArgumentException.class, () -> SimulateRequest.builder().build());
    }

    @Test
    void testDefensiveCopies() {
        List<SimulateCall> mutableCalls = new ArrayList<>();
        mutableCalls.add(SimulateCall.builder().to(TO).build());

        SimulateRequest request = SimulateRequest.builder()
                .calls(mutableCalls)
                .build();

        // Modify original list
        mutableCalls.add(SimulateCall.builder().to(ACCOUNT).build());

        // Verify request didn't change
        assertEquals(1, request.calls().size());
    }

    @Test
    void testDefaults() {
        SimulateCall call = SimulateCall.builder().to(TO).build();
        SimulateRequest request = SimulateRequest.builder()
                .call(call)
                .build();

        assertNull(request.account());
        assertNull(request.blockTag()); // Default behavior is handled in builder/executor
        assertNull(request.stateOverrides());
        assertFalse(request.traceAssetChanges());
        assertFalse(request.traceTransfers());
        assertTrue(request.validation()); // Default is true for safety
        assertFalse(request.fetchTokenMetadata());
    }
}
