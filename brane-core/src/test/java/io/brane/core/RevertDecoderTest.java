package io.brane.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.brane.internal.web3j.abi.FunctionEncoder;
import io.brane.internal.web3j.abi.datatypes.Utf8String;
import io.brane.internal.web3j.abi.datatypes.Function;
import io.brane.internal.web3j.abi.datatypes.Type;
import io.brane.internal.web3j.abi.datatypes.generated.Uint256;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class RevertDecoderTest {

    @Test
    void decodesErrorString() {
        List<Type> inputs = List.of((Type) new Utf8String("simple reason"));
        Function fn = new Function("Error", inputs, List.of());
        String rawData = FunctionEncoder.encode(fn);

        RevertDecoder.Decoded decoded = RevertDecoder.decode(rawData);

        assertSame(RevertDecoder.RevertKind.ERROR_STRING, decoded.kind());
        assertEquals("simple reason", decoded.reason());
        assertEquals(rawData, decoded.rawDataHex());
    }

    @Test
    void decodesPanic() {
        List<Type> inputs = List.of((Type) new Uint256(BigInteger.valueOf(0x11)));
        Function fn = new Function("Panic", inputs, List.of());
        String rawData = FunctionEncoder.encode(fn);

        RevertDecoder.Decoded decoded = RevertDecoder.decode(rawData);

        assertSame(RevertDecoder.RevertKind.PANIC, decoded.kind());
        assertEquals("arithmetic overflow or underflow", decoded.reason());
        assertEquals(rawData, decoded.rawDataHex());
    }

    @Test
    void decodesCustomErrorsWhenProvided() {
        List<Type> inputs = List.of((Type) new Utf8String("hello"));
        Function fn = new Function("CustomError", inputs, List.of());
        String rawData = FunctionEncoder.encode(fn);
        String selector = rawData.substring(2, 10).toLowerCase(Locale.ROOT);

        Map<String, RevertDecoder.CustomErrorAbi> customErrors =
                Map.of(
                        selector,
                        new RevertDecoder.CustomErrorAbi(
                                "CustomError", List.of(new io.brane.internal.web3j.abi.TypeReference<Utf8String>() {})));

        RevertDecoder.Decoded decoded = RevertDecoder.decode(rawData, customErrors);

        assertSame(RevertDecoder.RevertKind.CUSTOM, decoded.kind());
        assertEquals("CustomError(hello)", decoded.reason());
        assertEquals(rawData, decoded.rawDataHex());
    }

    @Test
    void nonErrorDataReturnsNullReason() {
        String rawData = "0x12345678deadbeef";
        RevertDecoder.Decoded decoded = RevertDecoder.decode(rawData);

        assertSame(RevertDecoder.RevertKind.UNKNOWN, decoded.kind());
        assertNull(decoded.reason());
        assertEquals(rawData, decoded.rawDataHex());
    }
}
