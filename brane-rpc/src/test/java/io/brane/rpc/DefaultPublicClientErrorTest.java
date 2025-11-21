package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.brane.core.error.RpcException;
import io.brane.core.types.Address;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultPublicClientErrorTest {

    @Test
    void getLogsThrowsRpcExceptionForBlockRange() {
        BraneProvider provider =
                new FakeProvider(
                        new JsonRpcResponse(
                                "2.0",
                                null,
                                new JsonRpcError(-32000, "block range is too large", null),
                                "1"));

        PublicClient client = PublicClient.from(provider);
        LogFilter filter = new LogFilter(Optional.empty(), Optional.empty(), Optional.of(new Address("0x" + "1".repeat(40))), Optional.empty());

        RpcException ex = assertThrows(RpcException.class, () -> client.getLogs(filter));
        assertTrue(ex.isBlockRangeTooLarge());
    }

    @Test
    void getLogsThrowsRpcExceptionForFilterNotFound() {
        BraneProvider provider =
                new FakeProvider(
                        new JsonRpcResponse(
                                "2.0",
                                null,
                                new JsonRpcError(-32000, "filter not found", null),
                                "1"));

        PublicClient client = PublicClient.from(provider);
        LogFilter filter = new LogFilter(Optional.empty(), Optional.empty(), Optional.of(new Address("0x" + "1".repeat(40))), Optional.empty());

        RpcException ex = assertThrows(RpcException.class, () -> client.getLogs(filter));
        assertTrue(ex.isFilterNotFound());
    }

    private record FakeProvider(JsonRpcResponse response) implements BraneProvider {
        @Override
        public JsonRpcResponse send(final String method, final List<?> params) {
            return response;
        }
    }
}
