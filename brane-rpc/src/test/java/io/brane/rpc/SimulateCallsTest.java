// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.brane.core.model.AssetChange;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.rpc.exception.SimulateNotSupportedException;


@ExtendWith(MockitoExtension.class)
class SimulateCallsTest {

    @Mock
    private BraneProvider provider;

    private Brane client;

    private static final Address FROM = new Address("0x1111111111111111111111111111111111111111");
    private static final Address TO = new Address("0x2222222222222222222222222222222222222222");

    @BeforeEach
    void setUp() {
        client = new DefaultReader(provider, null, 0, RpcRetryConfig.defaults());
    }

    @Test
    void simulateCallsSerializesRequestCorrectly() throws Exception {
        SimulateCall call1 = SimulateCall.builder().from(FROM).to(TO).data(new HexData("0x1234")).build();
        SimulateRequest request = SimulateRequest.builder()
                .call(call1)
                .blockTag(BlockTag.LATEST)
                .traceAssetChanges(true)
                .build();

        // Mock response
        Map<String, Object> mockResult = Map.of(
                "results", List.of(
                        Map.of(
                                "gasUsed", "0x5208",
                                "returnData", "0x"
                        )
                )
        );
        JsonRpcResponse response = new JsonRpcResponse("2.0", mockResult, null, "1");
        when(provider.send(eq("eth_simulateV1"), any())).thenReturn(response);

        client.simulate(request);

        ArgumentCaptor<List<Object>> captor = ArgumentCaptor.forClass(List.class);
        verify(provider).send(eq("eth_simulateV1"), captor.capture());

        List<Object> params = captor.getValue();
        assertEquals(2, params.size(), "Should have two parameters: payload and block tag");

        Map<String, Object> payload = (Map<String, Object>) params.get(0);
        String blockTag = (String) params.get(1);

        // Check blockStateCalls
        List<Map<String, Object>> blockStateCalls = (List<Map<String, Object>>) payload.get("blockStateCalls");
        assertNotNull(blockStateCalls);
        assertEquals(1, blockStateCalls.size());

        Map<String, Object> firstBlock = blockStateCalls.get(0);
        List<Map<String, Object>> calls = (List<Map<String, Object>>) firstBlock.get("calls");
        assertNotNull(calls);
        assertEquals(1, calls.size());
        assertEquals(FROM.value(), calls.get(0).get("from"));
        assertEquals(TO.value(), calls.get(0).get("to"));

        // Check block tag (2nd parameter)
        assertEquals("latest", blockTag);

        // Check flags in payload
        assertEquals(true, payload.get("traceAssetChanges"));
    }

    @Test
    void simulateCallsParsesSuccessResponse() throws Exception {
        SimulateCall call = SimulateCall.builder().to(TO).build();
        SimulateRequest request = SimulateRequest.builder().call(call).build();

        Map<String, Object> callResult = new HashMap<>();
        callResult.put("gasUsed", "0x5208");
        callResult.put("returnData", "0xabcd");

        Map<String, Object> mockResult = Map.of(
                "results", List.of(callResult)
        );

        JsonRpcResponse response = new JsonRpcResponse("2.0", mockResult, null, "1");
        when(provider.send(eq("eth_simulateV1"), any())).thenReturn(response);

        SimulateResult result = client.simulate(request);

        assertEquals(1, result.results().size());
        assertInstanceOf(CallResult.Success.class, result.results().getFirst());
        CallResult.Success success = (CallResult.Success) result.results().getFirst();
        assertEquals(BigInteger.valueOf(21000), success.gasUsed());
        assertEquals("0xabcd", success.returnData().value());
    }

    @Test
    void simulateCallsParsesFailureResponse() throws Exception {
        SimulateCall call = SimulateCall.builder().to(TO).build();
        SimulateRequest request = SimulateRequest.builder().call(call).build();

        Map<String, Object> error = new HashMap<>();
        error.put("code", -32000);
        error.put("message", "execution reverted");
        error.put("data", "0x08c379a0");

        Map<String, Object> callResult = new HashMap<>();
        callResult.put("gasUsed", "0x5208");
        callResult.put("error", error);
        callResult.put("returnData", "0x08c379a0");

        Map<String, Object> mockResult = Map.of(
                "results", List.of(callResult)
        );

        JsonRpcResponse response = new JsonRpcResponse("2.0", mockResult, null, "1");
        when(provider.send(eq("eth_simulateV1"), any())).thenReturn(response);

        SimulateResult result = client.simulate(request);

        assertEquals(1, result.results().size());
        assertInstanceOf(CallResult.Failure.class, result.results().getFirst());
        CallResult.Failure failure = (CallResult.Failure) result.results().getFirst();
        assertEquals("execution reverted", failure.errorMessage());
        assertEquals("0x08c379a0", failure.revertData().value());
    }

    @Test
    void simulateCallsParsesAssetChanges() throws Exception {
        SimulateCall call = SimulateCall.builder().to(TO).build();
        SimulateRequest request = SimulateRequest.builder().call(call).traceAssetChanges(true).build();

        Map<String, Object> assetChange = new HashMap<>();
        assetChange.put("token", "0x" + "a".repeat(40));
        assetChange.put("symbol", "USDC");
        assetChange.put("decimals", 6);

        Map<String, Object> value = new HashMap<>();
        value.put("pre", "0x0");
        value.put("post", "0x3e8"); // 1000
        value.put("diff", "0x3e8");

        assetChange.put("value", value);

        Map<String, Object> mockResult = Map.of(
                "results", List.of(Map.of("gasUsed", "0x0", "returnData", "0x")),
                "assetChanges", List.of(assetChange)
        );

        JsonRpcResponse response = new JsonRpcResponse("2.0", mockResult, null, "1");
        when(provider.send(eq("eth_simulateV1"), any())).thenReturn(response);

        SimulateResult result = client.simulate(request);

        assertNotNull(result.assetChanges());
        assertEquals(1, result.assetChanges().size());
        AssetChange change = result.assetChanges().get(0);
        assertEquals("USDC", change.token().symbol());
        assertEquals(BigInteger.valueOf(1000), change.value().post());
    }

    @Test
    void simulateCallsThrowsSimulateNotSupportedExceptionOnMethodNotFound() throws Exception {
        SimulateRequest request = SimulateRequest.builder()
                .call(SimulateCall.builder().to(TO).build())
                .build();

        JsonRpcError error = new JsonRpcError(-32601, "Method not found", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");

        when(provider.send(eq("eth_simulateV1"), any())).thenReturn(response);

        assertThrows(SimulateNotSupportedException.class, () -> client.simulate(request));
    }

    @Test
    @SuppressWarnings("unchecked")
    void simulateCallsSerializesStateOverridesAsDirectChild() throws Exception {
        // Per eth_simulateV1 spec, stateOverrides should be a direct child of blockStateCall,
        // NOT nested inside a "blockState" wrapper object
        Address overrideAddress = new Address("0x3333333333333333333333333333333333333333");
        SimulateRequest request = SimulateRequest.builder()
                .call(SimulateCall.builder().to(TO).build())
                .stateOverride(overrideAddress, AccountOverride.builder()
                        .balance(io.brane.core.types.Wei.fromEther(new java.math.BigDecimal("100")))
                        .nonce(42)
                        .build())
                .build();

        // Mock response
        Map<String, Object> mockResult = Map.of(
                "results", List.of(Map.of("gasUsed", "0x5208", "returnData", "0x"))
        );
        JsonRpcResponse response = new JsonRpcResponse("2.0", mockResult, null, "1");
        when(provider.send(eq("eth_simulateV1"), any())).thenReturn(response);

        client.simulate(request);

        ArgumentCaptor<List<Object>> captor = ArgumentCaptor.forClass(List.class);
        verify(provider).send(eq("eth_simulateV1"), captor.capture());

        List<Object> params = captor.getValue();
        Map<String, Object> payload = (Map<String, Object>) params.get(0);
        List<Map<String, Object>> blockStateCalls = (List<Map<String, Object>>) payload.get("blockStateCalls");
        Map<String, Object> firstBlock = blockStateCalls.get(0);

        // CRITICAL: stateOverrides should be a DIRECT child, NOT nested in "blockState"
        assertNull(firstBlock.get("blockState"),
                "stateOverrides should NOT be nested inside blockState");
        assertNotNull(firstBlock.get("stateOverrides"),
                "stateOverrides should be a direct child of blockStateCall");

        // Verify the override values are correct
        Map<String, Map<String, Object>> stateOverrides =
                (Map<String, Map<String, Object>>) firstBlock.get("stateOverrides");
        Map<String, Object> override = stateOverrides.get(overrideAddress.value());
        assertNotNull(override, "Override for address should exist");
        assertNotNull(override.get("balance"), "Balance should be set");
        assertEquals("0x2a", override.get("nonce"), "Nonce should be 42 (0x2a)");
    }

    @Test
    void simulateCallsParsesFailureFromStatusZeroWithoutErrorField() throws Exception {
        // Test the status=0 failure detection path in CallResult.fromMap()
        // When status is "0x0" but there's no error field, it should still produce a Failure
        SimulateCall call = SimulateCall.builder().to(TO).build();
        SimulateRequest request = SimulateRequest.builder().call(call).build();

        // Response with status=0 but NO error field
        Map<String, Object> callResult = new HashMap<>();
        callResult.put("gasUsed", "0x5208");
        callResult.put("status", "0x0");  // Failed status
        callResult.put("returnData", "0x08c379a0");  // Revert data present
        // Note: no "error" field - this tests the status-based failure detection

        Map<String, Object> mockResult = Map.of(
                "results", List.of(callResult)
        );

        JsonRpcResponse response = new JsonRpcResponse("2.0", mockResult, null, "1");
        when(provider.send(eq("eth_simulateV1"), any())).thenReturn(response);

        SimulateResult result = client.simulate(request);

        assertEquals(1, result.results().size());
        assertTrue(result.results().get(0) instanceof CallResult.Failure,
                "status=0 without error field should produce Failure");
        CallResult.Failure failure = (CallResult.Failure) result.results().get(0);
        assertEquals("execution failed", failure.errorMessage(),
                "Default error message should be 'execution failed' when no error field");
        assertEquals(BigInteger.valueOf(21000), failure.gasUsed());
        assertEquals("0x08c379a0", failure.revertData().value());
    }

    @Test
    void simulateCallsSerializesValidationFlagWhenFalse() throws Exception {
        // Verify that validation=false is explicitly serialized (not omitted)
        SimulateRequest request = SimulateRequest.builder()
                .call(SimulateCall.builder().to(TO).build())
                .validation(false)
                .build();

        // Mock response
        Map<String, Object> mockResult = Map.of(
                "results", List.of(Map.of("gasUsed", "0x5208", "returnData", "0x"))
        );
        JsonRpcResponse response = new JsonRpcResponse("2.0", mockResult, null, "1");
        when(provider.send(eq("eth_simulateV1"), any())).thenReturn(response);

        client.simulate(request);

        ArgumentCaptor<List<Object>> captor = ArgumentCaptor.forClass(List.class);
        verify(provider).send(eq("eth_simulateV1"), captor.capture());

        List<Object> params = captor.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) params.get(0);

        // validation should be explicitly set to false
        assertEquals(false, payload.get("validation"),
                "validation=false should be explicitly serialized");
    }

    // Helper for verify since verify(provider).send(eq("eth_simulateV1"), captor.capture())
    // needs a mockito verify call which is static imported.
    private <T> T verify(T mock) {
        return org.mockito.Mockito.verify(mock);
    }
}
