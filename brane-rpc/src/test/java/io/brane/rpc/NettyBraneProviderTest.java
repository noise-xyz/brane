package io.brane.rpc;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class NettyBraneProviderTest {

    @Test
    void testParseResponseFromByteBuf_Primitive() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x123\"}";
        ByteBuf buf = Unpooled.wrappedBuffer(json.getBytes(StandardCharsets.UTF_8));
        JsonRpcResponse response = NettyBraneProvider.parseResponseFromByteBuf(buf);
        assertEquals("0x123", response.result());
        assertNull(response.error());
    }

    @Test
    void testParseResponseFromByteBuf_Null() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}";
        ByteBuf buf = Unpooled.wrappedBuffer(json.getBytes(StandardCharsets.UTF_8));
        JsonRpcResponse response = NettyBraneProvider.parseResponseFromByteBuf(buf);
        assertNull(response.result());
        assertNull(response.error());
    }

    @Test
    void testParseResponseFromByteBuf_Object() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"foo\":\"bar\"}}";
        ByteBuf buf = Unpooled.wrappedBuffer(json.getBytes(StandardCharsets.UTF_8));
        JsonRpcResponse response = NettyBraneProvider.parseResponseFromByteBuf(buf);
        assertTrue(response.result() instanceof Map);
        assertEquals("bar", ((Map<?, ?>) response.result()).get("foo"));
    }

    @Test
    void testParseResponseFromByteBuf_Array() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":[\"a\",\"b\"]}";
        ByteBuf buf = Unpooled.wrappedBuffer(json.getBytes(StandardCharsets.UTF_8));
        JsonRpcResponse response = NettyBraneProvider.parseResponseFromByteBuf(buf);
        assertTrue(response.result() instanceof List);
        assertEquals("a", ((List<?>) response.result()).get(0));
    }

    @Test
    void testParseResponseFromByteBuf_Error() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32600,\"message\":\"Invalid Request\"}}";
        ByteBuf buf = Unpooled.wrappedBuffer(json.getBytes(StandardCharsets.UTF_8));
        JsonRpcResponse response = NettyBraneProvider.parseResponseFromByteBuf(buf);
        assertNotNull(response.error());
        assertEquals(-32600, response.error().code());
    }
}
