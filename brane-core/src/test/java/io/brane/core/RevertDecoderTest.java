package io.brane.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.brane.internal.web3j.abi.FunctionEncoder;
import io.brane.internal.web3j.abi.datatypes.Utf8String;
import io.brane.internal.web3j.abi.datatypes.Function;
import io.brane.internal.web3j.abi.datatypes.Type;
import java.util.List;
import org.junit.jupiter.api.Test;

class RevertDecoderTest {

    @Test
    void decodesErrorString() {
        List<Type> inputs = List.of((Type) new Utf8String("simple reason"));
        Function fn = new Function("Error", inputs, List.of());
        String rawData = FunctionEncoder.encode(fn);

        RevertDecoder.Decoded decoded = RevertDecoder.decode(rawData);

        assertEquals("simple reason", decoded.reason());
        assertEquals(rawData, decoded.rawDataHex());
    }

    @Test
    void nonErrorDataReturnsNullReason() {
        String rawData = "0x12345678deadbeef";
        RevertDecoder.Decoded decoded = RevertDecoder.decode(rawData);

        assertNull(decoded.reason());
        assertEquals(rawData, decoded.rawDataHex());
    }
}
