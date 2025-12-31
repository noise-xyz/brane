package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.brane.core.model.LogEntry;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PublicClientLogsTest {

    @Test
    void buildsRequestAndDecodesLogs() {
        FakeProvider provider =
                new FakeProvider(
                        new JsonRpcResponse(
                                "2.0",
                                List.of(
                                        Map.of(
                                                "address", "0x" + "1".repeat(40),
                                                "data", "0x01",
                                                "blockHash", "0x" + "2".repeat(64),
                                                "transactionHash", "0x" + "3".repeat(64),
                                                "logIndex", "0x0",
                                                "topics",
                                                        List.of(
                                                                "0x" + "4".repeat(64),
                                                                "0x" + "5".repeat(64)))),
                                null,
                                "1"));

        PublicClient client = PublicClient.from(provider);
        LogFilter filter =
                new LogFilter(
                        java.util.Optional.of(1L),
                        java.util.Optional.of(2L),
                        java.util.Optional.of(List.of(new Address("0x" + "a".repeat(40)))),
                        java.util.Optional.of(List.of(new Hash("0x" + "b".repeat(64)))));

        List<LogEntry> logs = client.getLogs(filter);

        assertEquals(1, logs.size());
        LogEntry entry = logs.get(0);
        assertEquals("0x" + "1".repeat(40), entry.address().value());
        assertEquals(new HexData("0x01"), entry.data());
        assertEquals(2, entry.topics().size());
        assertEquals("eth_getLogs", provider.lastMethod);
        @SuppressWarnings("unchecked")
        Map<String, Object> req = (Map<String, Object>) provider.lastParams.get(0);
        assertEquals("0x1", req.get("fromBlock"));
        assertEquals("0x2", req.get("toBlock"));
        assertEquals("0x" + "a".repeat(40), req.get("address"));
        @SuppressWarnings("unchecked")
        List<String> topics = (List<String>) req.get("topics");
        assertEquals(List.of("0x" + "b".repeat(64)), topics);
    }

    @Test
    void serializesMultipleAddressesAsArray() {
        FakeProvider provider =
                new FakeProvider(new JsonRpcResponse("2.0", List.of(), null, "1"));

        PublicClient client = PublicClient.from(provider);
        Address addr1 = new Address("0x" + "a".repeat(40));
        Address addr2 = new Address("0x" + "b".repeat(40));
        Address addr3 = new Address("0x" + "c".repeat(40));

        LogFilter filter = LogFilter.byContracts(List.of(addr1, addr2, addr3), List.of());

        client.getLogs(filter);

        @SuppressWarnings("unchecked")
        Map<String, Object> req = (Map<String, Object>) provider.lastParams.get(0);

        // Multiple addresses should be serialized as an array
        @SuppressWarnings("unchecked")
        List<String> addresses = (List<String>) req.get("address");
        assertEquals(3, addresses.size());
        assertEquals("0x" + "a".repeat(40), addresses.get(0));
        assertEquals("0x" + "b".repeat(40), addresses.get(1));
        assertEquals("0x" + "c".repeat(40), addresses.get(2));
    }

    @Test
    void byContractFactoryMethodCreatesSingleAddress() {
        Address addr = new Address("0x" + "d".repeat(40));
        LogFilter filter = LogFilter.byContract(addr, List.of());

        assertEquals(1, filter.addresses().get().size());
        assertEquals(addr, filter.addresses().get().get(0));
    }

    @Test
    void byContractsFactoryMethodCreatesMultipleAddresses() {
        Address addr1 = new Address("0x" + "e".repeat(40));
        Address addr2 = new Address("0x" + "f".repeat(40));
        LogFilter filter = LogFilter.byContracts(List.of(addr1, addr2), List.of());

        assertEquals(2, filter.addresses().get().size());
        assertEquals(addr1, filter.addresses().get().get(0));
        assertEquals(addr2, filter.addresses().get().get(1));
    }

    private static final class FakeProvider implements BraneProvider {
        private final JsonRpcResponse response;
        private String lastMethod;
        private List<?> lastParams;

        FakeProvider(final JsonRpcResponse response) {
            this.response = response;
        }

        @Override
        public JsonRpcResponse send(final String method, final List<?> params)
                throws io.brane.core.error.RpcException {
            this.lastMethod = method;
            this.lastParams = params;
            return response;
        }
    }
}
