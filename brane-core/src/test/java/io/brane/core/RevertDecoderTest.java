// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.brane.core.abi.AbiEncoder;
import io.brane.core.abi.TypeSchema;
import io.brane.core.abi.UInt;
import io.brane.core.abi.Utf8String;
import io.brane.primitives.Hex;

class RevertDecoderTest {

    @Test
    void decodesErrorString() {
        String rawData = Hex.encode(
                AbiEncoder.encodeFunction("Error(string)",
                        List.of(new Utf8String("simple reason"))));

        RevertDecoder.Decoded decoded = RevertDecoder.decode(rawData);

        assertSame(RevertDecoder.RevertKind.ERROR_STRING, decoded.kind());
        assertEquals("simple reason", decoded.reason());
        assertEquals(rawData, decoded.rawDataHex());
    }

    @Test
    void decodesPanic() {
        String rawData = Hex.encode(
                AbiEncoder.encodeFunction("Panic(uint256)",
                        List.of(new UInt(256, BigInteger.valueOf(0x11)))));

        RevertDecoder.Decoded decoded = RevertDecoder.decode(rawData);

        assertSame(RevertDecoder.RevertKind.PANIC, decoded.kind());
        assertEquals("arithmetic overflow or underflow", decoded.reason());
        assertEquals(rawData, decoded.rawDataHex());
    }

    @Test
    void decodesCustomErrorsWhenProvided() {
        String rawData = Hex.encode(
                AbiEncoder.encodeFunction("CustomError(string)",
                        List.of(new Utf8String("hello"))));
        String selector = rawData.substring(2, 10).toLowerCase(Locale.ROOT);

        Map<String, RevertDecoder.CustomErrorAbi> customErrors = Map.of(
                selector,
                new RevertDecoder.CustomErrorAbi(
                        "CustomError", List.of(new TypeSchema.StringSchema())));

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

    @Test
    void decodesPanicWithLargeCode() {
        // Test panic code larger than Integer.MAX_VALUE to verify bounds check
        BigInteger largeCode = BigInteger.valueOf(Long.MAX_VALUE);
        String rawData = Hex.encode(
                AbiEncoder.encodeFunction("Panic(uint256)",
                        List.of(new UInt(256, largeCode))));

        RevertDecoder.Decoded decoded = RevertDecoder.decode(rawData);

        assertSame(RevertDecoder.RevertKind.PANIC, decoded.kind());
        // Should return generic "panic with code 0x..." without overflow
        assertEquals("panic with code 0x" + largeCode.toString(16), decoded.reason());
    }

    @Test
    void decodesPanicWithUnknownCode() {
        // Test unknown panic code that fits in int range
        String rawData = Hex.encode(
                AbiEncoder.encodeFunction("Panic(uint256)",
                        List.of(new UInt(256, BigInteger.valueOf(0xFF)))));

        RevertDecoder.Decoded decoded = RevertDecoder.decode(rawData);

        assertSame(RevertDecoder.RevertKind.PANIC, decoded.kind());
        assertEquals("panic with code 0xff", decoded.reason());
    }
}
