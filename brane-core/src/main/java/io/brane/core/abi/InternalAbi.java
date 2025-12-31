package io.brane.core.abi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.brane.core.model.MulticallResult;
import io.brane.core.error.AbiDecodingException;
import io.brane.core.error.AbiEncodingException;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.primitives.Hex;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class InternalAbi implements Abi {

    private static final Logger LOG = LoggerFactory.getLogger(InternalAbi.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, AbiFunction> functionsByName;
    private final Map<String, AbiFunction> functionsBySignature;
    private final Map<String, AbiEvent> eventsBySignature;
    private final AbiFunction constructor;

    InternalAbi(final String json) {
        final ParsedAbi parsed = parse(json);
        this.functionsByName = parsed.functionsByName;
        this.functionsBySignature = parsed.functionsBySignature;
        this.eventsBySignature = parsed.eventsBySignature;
        this.constructor = parsed.constructor;
    }

    @Override
    public FunctionCall encodeFunction(final String name, final Object... args) {
        final Object[] providedArgs = args == null ? new Object[0] : args;
        final AbiFunction fn = resolveFunction(name, providedArgs.length);

        // Calculate selector
        final byte[] selector = Arrays
                .copyOf(io.brane.core.crypto.Keccak256.hash(fn.signature().getBytes(StandardCharsets.UTF_8)), 4);

        // Calculate arguments size
        int headSize = 0;
        int dynamicTailSize = 0;
        final List<AbiParameter> inputs = fn.inputs();
        final int count = inputs.size();

        for (int i = 0; i < count; i++) {
            final AbiParameter param = inputs.get(i);
            final Object arg = providedArgs[i];
            headSize += param.converter.getHeadSize();
            if (param.converter.isDynamic()) {
                dynamicTailSize += param.converter.getContentSize(arg);
            }
        }

        final int totalSize = 4 + headSize + dynamicTailSize;
        final byte[] encoded = new byte[totalSize];
        final java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(encoded);

        buffer.put(selector);

        // Encode Heads
        int currentTailOffset = headSize;
        for (int i = 0; i < count; i++) {
            final AbiParameter param = inputs.get(i);
            final Object arg = providedArgs[i];

            if (param.converter.isDynamic()) {
                io.brane.core.abi.FastAbiEncoder.encodeUInt256(BigInteger.valueOf(currentTailOffset), buffer);
                currentTailOffset += param.converter.getContentSize(arg);
            } else {
                param.converter.encodeContent(arg, buffer);
            }
        }

        // Encode Tails
        for (int i = 0; i < count; i++) {
            final AbiParameter param = inputs.get(i);
            final Object arg = providedArgs[i];

            if (param.converter.isDynamic()) {
                param.converter.encodeContent(arg, buffer);
            }
        }

        return new Call(fn, HexData.fromBytes(encoded));
    }

    @Override
    public HexData encodeConstructor(final Object... args) {
        if (constructor == null) {
            if (args != null && args.length > 0) {
                throw new AbiEncodingException("Constructor not defined in ABI, but arguments provided");
            }
            return HexData.EMPTY;
        }

        final Object[] providedArgs = args == null ? new Object[0] : args;
        if (constructor.inputs().size() != providedArgs.length) {
            throw new AbiEncodingException(
                    "Constructor expects "
                            + constructor.inputs().size()
                            + " arguments but "
                            + providedArgs.length
                            + " were supplied");
        }

        // 1. Calculate Total Size
        int headSize = 0;
        int dynamicTailSize = 0;
        final List<AbiParameter> inputs = constructor.inputs();
        final int count = inputs.size();

        for (int i = 0; i < count; i++) {
            final AbiParameter param = inputs.get(i);
            final Object arg = providedArgs[i];
            headSize += param.converter.getHeadSize();
            if (param.converter.isDynamic()) {
                dynamicTailSize += param.converter.getContentSize(arg);
            }
        }

        final int totalSize = headSize + dynamicTailSize;
        final byte[] encoded = new byte[totalSize];
        final java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(encoded);

        // 2. Encode Heads
        int currentTailOffset = headSize;
        for (int i = 0; i < count; i++) {
            final AbiParameter param = inputs.get(i);
            final Object arg = providedArgs[i];

            if (param.converter.isDynamic()) {
                // Write offset
                io.brane.core.abi.FastAbiEncoder.encodeUInt256(BigInteger.valueOf(currentTailOffset), buffer);
                currentTailOffset += param.converter.getContentSize(arg);
            } else {
                // Write static content
                param.converter.encodeContent(arg, buffer);
            }
        }

        // 3. Encode Tails
        for (int i = 0; i < count; i++) {
            final AbiParameter param = inputs.get(i);
            final Object arg = providedArgs[i];

            if (param.converter.isDynamic()) {
                param.converter.encodeContent(arg, buffer);
            }
        }

        // Use trusted constructor via fromBytes which uses Hex.encodeNoPrefix
        return HexData.fromBytes(encoded);
    }

    @Override
    public Optional<FunctionMetadata> getFunction(final String name) {
        return Optional.ofNullable(functionsByName.get(name)).map(AbiFunction::metadata);
    }

    private AbiFunction resolveFunction(final String nameOrSignature, final int argCount) {
        final AbiFunction bySignature = functionsBySignature.get(nameOrSignature);
        if (bySignature != null) {
            return bySignature;
        }

        final AbiFunction function = functionsByName.get(nameOrSignature);
        if (function == null) {
            throw new AbiEncodingException("Unknown function '" + nameOrSignature + "'");
        }

        if (function.inputs().size() != argCount) {
            throw new AbiEncodingException(
                    "Function "
                            + nameOrSignature
                            + " expects "
                            + function.inputs().size()
                            + " arguments but "
                            + argCount
                            + " were supplied");
        }

        return function;
    }

    /**
     * Internal interface for converting Java objects to ABI types and performing
     * direct encoding.
     * <p>
     * This interface is the key to the high-performance encoding strategy. Instead
     * of creating
     * intermediate {@link AbiType} objects, implementations of this interface can
     * calculate sizes
     * and write directly to a {@link java.nio.ByteBuffer}.
     * </p>
     */
    interface TypeConverter {
        /**
         * Converts a Java object to an AbiType.
         * Used for fallback or when an AbiType object is explicitly needed.
         */
        AbiType convert(Object value);

        // Optimization methods

        /**
         * Returns true if this type is dynamic (has a variable length).
         * Dynamic types are encoded as an offset in the head, and their content is
         * written in the tail.
         */
        default boolean isDynamic() {
            return false; // Default to static
        }

        /**
         * Returns the size of the head part of this type.
         * For static types, this is the content size.
         * For dynamic types, this is always 32 bytes (the offset).
         */
        default int getHeadSize() {
            return 32; // Default head size
        }

        /**
         * Calculates the size of the content part of this type.
         * For static types, this is usually 32 bytes.
         * For dynamic types, this is the length of the actual data (plus length
         * prefix).
         */
        default int getContentSize(Object value) {
            return 32; // Default content size (static)
        }

        /**
         * Encodes the content of this type directly into the buffer.
         * <p>
         * For static types, this writes the data at the current position.
         * For dynamic types, this writes the data at the tail position (which is the
         * current position during Pass 2).
         * </p>
         */
        default void encodeContent(Object value, java.nio.ByteBuffer buffer) {
            // Fallback to legacy conversion if not optimized
            // This is slow but safe for complex types not yet optimized
            throw new UnsupportedOperationException("Direct encoding not implemented for this type");
        }
    }

    private static TypeConverter createConverter(final AbiParameter param) {
        final String solidityType = param.type;

        if (solidityType.endsWith("[]")) {
            final String baseType = solidityType.substring(0, solidityType.length() - 2);
            final AbiParameter elementParam = new AbiParameter("", baseType, false, param.components);
            final TypeConverter elementConverter = createConverter(elementParam);

            final int elementHeadSize = elementConverter.getHeadSize();

            return new TypeConverter() {
                @Override
                public AbiType convert(Object value) {
                    if (value == null)
                        throw new AbiEncodingException("Array value cannot be null");
                    final List<?> listValue = asList(value);
                    final List<AbiType> elements = new ArrayList<>(listValue.size());
                    for (Object element : listValue) {
                        elements.add(elementConverter.convert(element));
                    }
                    return new Array<AbiType>(elements, AbiType.class, true);
                }

                @Override
                public boolean isDynamic() {
                    return true;
                }

                @Override
                public int getContentSize(Object value) {
                    final List<?> list = asList(value);

                    // Length (32 bytes)
                    int size = 32;

                    if (elementConverter.isDynamic()) {
                        // Offsets (32 * count)
                        size += 32 * list.size();
                        // Content of elements
                        for (Object element : list) {
                            size += elementConverter.getContentSize(element);
                        }
                    } else {
                        // Elements are static, encoded in sequence
                        size += list.size() * elementHeadSize;
                    }
                    return size;
                }

                @Override
                public void encodeContent(Object value, java.nio.ByteBuffer buffer) {
                    final List<?> list = asList(value);

                    // Write length
                    io.brane.core.abi.FastAbiEncoder.encodeUInt256(BigInteger.valueOf(list.size()), buffer);

                    if (elementConverter.isDynamic()) {
                        int currentTailOffset = 32 * list.size();

                        // Pass 1: Offsets
                        for (Object element : list) {
                            io.brane.core.abi.FastAbiEncoder.encodeUInt256(BigInteger.valueOf(currentTailOffset),
                                    buffer);
                            currentTailOffset += elementConverter.getContentSize(element);
                        }

                        // Pass 2: Tails
                        for (Object element : list) {
                            elementConverter.encodeContent(element, buffer);
                        }
                    } else {
                        // Elements are static, write them directly
                        for (Object element : list) {
                            elementConverter.encodeContent(element, buffer);
                        }
                    }
                }
            };
        }

        if (solidityType.equals("tuple")) {
            if (param.components == null || param.components.isEmpty()) {
                throw new AbiEncodingException("Tuple parameter missing components");
            }
            final List<TypeConverter> componentConverters = new ArrayList<>(param.components.size());
            for (AbiParameter component : param.components) {
                componentConverters.add(createConverter(component));
            }

            // Pre-calculate dynamic status and static head size
            boolean dynamic = false;
            int staticSize = 0;
            for (TypeConverter c : componentConverters) {
                if (c.isDynamic()) {
                    dynamic = true;
                }
                staticSize += c.getHeadSize();
            }
            final boolean isTupleDynamic = dynamic;
            final int tupleStaticHeadSize = staticSize;

            return new TypeConverter() {

                @Override
                public AbiType convert(Object value) {
                    if (value == null)
                        throw new AbiEncodingException("Tuple value cannot be null");
                    final List<?> listValue = asList(value);
                    if (listValue.size() != componentConverters.size()) {
                        throw new AbiEncodingException(
                                "Tuple expects "
                                        + componentConverters.size()
                                        + " components but got "
                                        + listValue.size());
                    }

                    final List<AbiType> components = new ArrayList<>(componentConverters.size());
                    for (int i = 0; i < componentConverters.size(); i++) {
                        components.add(componentConverters.get(i).convert(listValue.get(i)));
                    }
                    return new Tuple(components);
                }

                @Override
                public boolean isDynamic() {
                    return isTupleDynamic;
                }

                @Override
                public int getHeadSize() {
                    return isTupleDynamic ? 32 : tupleStaticHeadSize;
                }

                @Override
                public int getContentSize(Object value) {
                    final List<?> list = asList(value);

                    // Start with static head size of all components
                    int size = tupleStaticHeadSize;

                    // Add content size of dynamic components
                    for (int i = 0; i < componentConverters.size(); i++) {
                        TypeConverter c = componentConverters.get(i);
                        if (c.isDynamic()) {
                            size += c.getContentSize(list.get(i));
                        }
                    }
                    return size;
                }

                @Override
                public void encodeContent(Object value, java.nio.ByteBuffer buffer) {
                    final List<?> list = asList(value);

                    int headSize = tupleStaticHeadSize;
                    int currentTailOffset = headSize;

                    // Pass 1: Heads
                    for (int i = 0; i < componentConverters.size(); i++) {
                        TypeConverter c = componentConverters.get(i);
                        Object v = list.get(i);

                        if (c.isDynamic()) {
                            io.brane.core.abi.FastAbiEncoder.encodeUInt256(BigInteger.valueOf(currentTailOffset),
                                    buffer);
                            currentTailOffset += c.getContentSize(v);
                        } else {
                            c.encodeContent(v, buffer);
                        }
                    }

                    // Pass 2: Tails
                    for (int i = 0; i < componentConverters.size(); i++) {
                        TypeConverter c = componentConverters.get(i);
                        Object v = list.get(i);

                        if (c.isDynamic()) {
                            c.encodeContent(v, buffer);
                        }
                    }
                }
            };
        }

        final String normalizedType = solidityType.toLowerCase(Locale.ROOT);

        if (normalizedType.startsWith("uint")) {
            int width = 256;
            if (normalizedType.length() > 4) {
                width = Integer.parseInt(normalizedType.substring(4));
            }
            final int finalWidth = width;
            return new TypeConverter() {
                @Override
                public AbiType convert(Object value) {
                    if (value == null)
                        throw new AbiEncodingException("uint value cannot be null");
                    return new UInt(finalWidth, toBigInteger(value, false));
                }

                @Override
                public boolean isDynamic() {
                    return false;
                }

                @Override
                public int getHeadSize() {
                    return 32;
                }

                @Override
                public int getContentSize(Object value) {
                    return 32;
                }

                @Override
                public void encodeContent(Object value, java.nio.ByteBuffer buffer) {
                    io.brane.core.abi.FastAbiEncoder.encodeUInt256(toBigInteger(value, false), buffer);
                }
            };
        }

        if (normalizedType.startsWith("int")) {
            int width = 256;
            if (normalizedType.length() > 3) {
                width = Integer.parseInt(normalizedType.substring(3));
            }
            final int finalWidth = width;
            return new TypeConverter() {
                @Override
                public AbiType convert(Object value) {
                    if (value == null)
                        throw new AbiEncodingException("int value cannot be null");
                    return new Int(finalWidth, toBigInteger(value, true));
                }

                @Override
                public boolean isDynamic() {
                    return false;
                }

                @Override
                public int getHeadSize() {
                    return 32;
                }

                @Override
                public int getContentSize(Object value) {
                    return 32;
                }

                @Override
                public void encodeContent(Object value, java.nio.ByteBuffer buffer) {
                    io.brane.core.abi.FastAbiEncoder.encodeInt256(toBigInteger(value, true), buffer);
                }
            };
        }

        if (normalizedType.equals("address")) {
            return new TypeConverter() {
                @Override
                public AbiType convert(Object value) {
                    if (value == null)
                        throw new AbiEncodingException("address value cannot be null");
                    if (value instanceof Address a)
                        return new AddressType(a);
                    if (value instanceof String s)
                        return new AddressType(new Address(s));
                    throw new AbiEncodingException("Expected Address for type 'address'");
                }

                @Override
                public boolean isDynamic() {
                    return false;
                }

                @Override
                public int getHeadSize() {
                    return 32;
                }

                @Override
                public int getContentSize(Object value) {
                    return 32;
                }

                @Override
                public void encodeContent(Object value, java.nio.ByteBuffer buffer) {
                    Address a = (value instanceof Address) ? (Address) value : new Address((String) value);
                    io.brane.core.abi.FastAbiEncoder.encodeAddress(a, buffer);
                }
            };
        }

        if (normalizedType.equals("bool")) {
            return new TypeConverter() {
                @Override
                public AbiType convert(Object value) {
                    if (value == null)
                        throw new AbiEncodingException("bool value cannot be null");
                    if (value instanceof Boolean b)
                        return new Bool(b);
                    throw new AbiEncodingException("Expected Boolean for type 'bool'");
                }

                @Override
                public boolean isDynamic() {
                    return false;
                }

                @Override
                public int getHeadSize() {
                    return 32;
                }

                @Override
                public int getContentSize(Object value) {
                    return 32;
                }

                @Override
                public void encodeContent(Object value, java.nio.ByteBuffer buffer) {
                    io.brane.core.abi.FastAbiEncoder.encodeBool((Boolean) value, buffer);
                }
            };
        }

        if (normalizedType.equals("string")) {
            return new TypeConverter() {
                @Override
                public AbiType convert(Object value) {
                    if (value == null)
                        throw new AbiEncodingException("string value cannot be null");
                    if (value instanceof String s)
                        return new Utf8String(s);
                    throw new AbiEncodingException("Expected String for type 'string'");
                }

                @Override
                public boolean isDynamic() {
                    return true;
                }

                @Override
                public int getHeadSize() {
                    return 32;
                }

                @Override
                public int getContentSize(Object value) {
                    String s = (String) value;
                    int len = s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                    int padding = (32 - (len % 32)) % 32;
                    return 32 + len + padding;
                }

                @Override
                public void encodeContent(Object value, java.nio.ByteBuffer buffer) {
                    io.brane.core.abi.FastAbiEncoder.encodeString(new Utf8String((String) value), buffer);
                }
            };
        }

        if (normalizedType.equals("bytes")) {
            return new TypeConverter() {
                @Override
                public AbiType convert(Object value) {
                    if (value == null)
                        throw new AbiEncodingException("bytes value cannot be null");
                    return toBytes(value, true);
                }

                @Override
                public boolean isDynamic() {
                    return true;
                }

                @Override
                public int getHeadSize() {
                    return 32;
                }

                @Override
                public int getContentSize(Object value) {
                    Bytes b = toBytes(value, true);
                    int len = Hex.decode(b.value().value()).length;
                    int padding = (32 - (len % 32)) % 32;
                    return 32 + len + padding;
                }

                @Override
                public void encodeContent(Object value, java.nio.ByteBuffer buffer) {
                    if (value instanceof byte[] b) {
                        io.brane.core.abi.FastAbiEncoder.encodeBytes(HexData.fromBytes(b), buffer);
                    } else if (value instanceof HexData h) {
                        io.brane.core.abi.FastAbiEncoder.encodeBytes(h, buffer);
                    } else {
                        throw new AbiEncodingException("Expected byte[] or HexData for bytes");
                    }
                }
            };
        }

        if (normalizedType.startsWith("bytes")) {
            return new TypeConverter() {
                @Override
                public AbiType convert(Object value) {
                    if (value == null)
                        throw new AbiEncodingException("bytesN value cannot be null");
                    return toBytes(value, false);
                }

                @Override
                public boolean isDynamic() {
                    return false;
                }

                @Override
                public int getHeadSize() {
                    return 32;
                }

                @Override
                public int getContentSize(Object value) {
                    return 32;
                }

                @Override
                public void encodeContent(Object value, java.nio.ByteBuffer buffer) {
                    // Static bytes are just right-padded
                    // Static bytes are just right-padded
                    byte[] data;
                    if (value instanceof byte[]) {
                        data = (byte[]) value;
                    } else if (value instanceof HexData) {
                        data = Hex.decode(((HexData) value).value());
                    } else {
                        throw new AbiEncodingException("Expected byte[] or HexData for bytesN");
                    }

                    if (data.length > 32) {
                        throw new AbiEncodingException(
                                "Static bytesN data cannot be longer than 32 bytes, but got " + data.length);
                    }

                    buffer.put(data);
                    // Pad remaining bytes with zeros
                    for (int i = 0; i < 32 - data.length; i++) {
                        buffer.put((byte) 0);
                    }
                }
            };
        }

        throw new AbiEncodingException("Unsupported argument type '" + solidityType + "'");
    }

    private static Bytes toBytes(Object value, boolean isDynamic) {
        if (value instanceof byte[] b)
            return isDynamic ? Bytes.of(b) : Bytes.ofStatic(b);
        if (value instanceof HexData h) {
            byte[] b = Hex.decode(h.value());
            return isDynamic ? Bytes.of(b) : Bytes.ofStatic(b);
        }
        throw new AbiEncodingException("Expected byte[] or HexData for bytes");
    }

    private static BigInteger toBigInteger(final Object value, final boolean signed) {
        return switch (value) {
            case BigInteger bi -> ensureSign(bi, signed);
            case Number number -> ensureSign(BigInteger.valueOf(number.longValue()), signed);
            case String s -> {
                if (s.startsWith("0x"))
                    yield ensureSign(new BigInteger(s.substring(2), 16), signed);
                yield ensureSign(new BigInteger(s), signed);
            }
            default -> throw new AbiEncodingException("Expected numeric value for " + (signed ? "int" : "uint")
                    + " but got " + value.getClass().getSimpleName());

        };
    }

    private static BigInteger ensureSign(final BigInteger value, final boolean signed) {
        if (!signed && value.signum() < 0) {
            throw new AbiEncodingException("uint cannot be negative");
        }
        return value;
    }

    private static List<?> asList(final Object value) {
        if (value instanceof List<?> l) {
            return l;
        }
        if (value.getClass().isArray()) {
            final int length = java.lang.reflect.Array.getLength(value);
            final List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(java.lang.reflect.Array.get(value, i));
            }
            return list;
        }
        throw new AbiEncodingException("Expected array/list value but got " + value.getClass());
    }

    private static ParsedAbi parse(final String json) {
        if (json == null || json.isBlank()) {
            throw new AbiEncodingException("ABI json must not be null or empty");
        }

        final JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (IOException e) {
            throw new AbiEncodingException("Unable to parse ABI json", e);
        }

        if (!root.isArray()) {
            throw new AbiEncodingException("ABI json must be an array");
        }

        final Map<String, AbiFunction> functionsByName = new HashMap<>();
        final Map<String, AbiFunction> functionsBySignature = new HashMap<>();
        final Map<String, AbiEvent> eventsBySignature = new HashMap<>();
        AbiFunction constructor = null;
        final Iterator<JsonNode> iterator = root.elements();
        while (iterator.hasNext()) {
            final JsonNode node = iterator.next();
            final String type = node.path("type").asText("").toLowerCase(Locale.ROOT);
            if ("function".equals(type)) {
                final AbiFunction fn = parseFunction(node);
                final AbiFunction existing = functionsByName.putIfAbsent(fn.name(), fn);
                if (existing != null) {
                    throw new AbiEncodingException(
                            "Overloaded functions are not supported; duplicate name '" + fn.name() + "'");
                }
                functionsBySignature.put(fn.signature(), fn);
            } else if ("event".equals(type)) {
                final AbiEvent event = parseEvent(node);
                eventsBySignature.put(event.signature(), event);
            } else if ("constructor".equals(type)) {
                if (constructor != null) {
                    throw new AbiEncodingException("Multiple constructors found in ABI");
                }
                constructor = parseConstructor(node);
            }
        }

        return new ParsedAbi(functionsByName, functionsBySignature, eventsBySignature, constructor);
    }

    private static AbiFunction parseConstructor(final JsonNode node) {
        final List<AbiParameter> inputs = parseParameters(arrayField(node, "inputs"));
        final String stateMutability = parseStateMutability(node);
        return new AbiFunction("constructor", stateMutability, inputs, Collections.emptyList());
    }

    private static AbiFunction parseFunction(final JsonNode node) {
        final String name = requireText(node, "name", "function");
        final List<AbiParameter> inputs = parseParameters(arrayField(node, "inputs"));
        final List<AbiParameter> outputs = parseParameters(arrayField(node, "outputs"));
        final String stateMutability = parseStateMutability(node);
        return new AbiFunction(name, stateMutability, inputs, outputs);
    }

    private static String parseStateMutability(final JsonNode node) {
        String stateMutability = node.path("stateMutability").asText("");
        if (stateMutability == null || stateMutability.isBlank()) {
            final boolean constant = node.path("constant").asBoolean(false);
            stateMutability = constant ? "view" : "nonpayable";
        }
        return stateMutability.toLowerCase(Locale.ROOT);
    }

    private static AbiEvent parseEvent(final JsonNode node) {
        final String name = requireText(node, "name", "event");
        final List<AbiParameter> inputs = parseParameters(arrayField(node, "inputs"));
        return new AbiEvent(name, inputs);
    }

    private static List<AbiParameter> parseParameters(final ArrayNode array) {
        if (array == null || array.isEmpty()) {
            return Collections.emptyList();
        }

        final List<AbiParameter> params = new ArrayList<>(array.size());
        for (JsonNode node : array) {
            params.add(parseParameter(node));
        }
        return params;
    }

    private static AbiParameter parseParameter(final JsonNode node) {
        final String name = node.path("name").asText("");
        final String type = requireText(node, "type", "parameter");
        final boolean indexed = node.path("indexed").asBoolean(false);
        final List<AbiParameter> components = parseParameters(arrayField(node, "components"));
        return new AbiParameter(name, type, indexed, components);
    }

    private static ArrayNode arrayField(final JsonNode node, final String name) {
        final JsonNode field = node.path(name);
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }
        if (!field.isArray()) {
            throw new AbiEncodingException("Field '" + name + "' must be an array");
        }
        return (ArrayNode) field;
    }

    private static String requireText(final JsonNode node, final String field, final String context) {
        final JsonNode value = node.path(field);
        if (value.isMissingNode() || !value.isTextual()) {
            throw new AbiEncodingException(
                    "Field '" + field + "' is required and must be a string in " + context);
        }
        return value.asText();
    }

    private static final class AbiParameter {
        final String name;
        final String type;
        final boolean indexed;
        final List<AbiParameter> components;
        final TypeConverter converter;

        AbiParameter(String name, String type, boolean indexed, List<AbiParameter> components) {
            this.name = name;
            this.type = type;
            this.indexed = indexed;
            this.components = components;
            // Pre-compute the converter for this parameter type
            this.converter = createConverter(this);
        }

        String canonicalType() {
            if (type.startsWith("tuple")) {
                final String suffix = type.substring(5);
                final String joined = components.stream()
                        .map(AbiParameter::canonicalType)
                        .collect(Collectors.joining(","));
                return "(" + joined + ")" + suffix;
            }
            return type;
        }
    }

    private record AbiFunction(
            String name, String stateMutability, List<AbiParameter> inputs, List<AbiParameter> outputs) {
        String signature() {
            final String joined = inputs.stream().map(AbiParameter::canonicalType).collect(Collectors.joining(","));
            return name + "(" + joined + ")";
        }

        FunctionMetadata metadata() {
            final List<String> inputTypes = inputs.stream().map(p -> p.type).toList();
            final List<String> outputTypes = outputs.stream().map(p -> p.type).toList();
            return new FunctionMetadata(name, stateMutability, inputTypes, outputTypes);
        }
    }

    private record AbiEvent(String name, List<AbiParameter> inputs) {
        String signature() {
            final String joined = inputs.stream().map(AbiParameter::canonicalType).collect(Collectors.joining(","));
            return name + "(" + joined + ")";
        }
    }

    private static final class ParsedAbi {
        final Map<String, AbiFunction> functionsByName;
        final Map<String, AbiFunction> functionsBySignature;
        final Map<String, AbiEvent> eventsBySignature;
        final AbiFunction constructor;

        ParsedAbi(
                final Map<String, AbiFunction> functionsByName,
                final Map<String, AbiFunction> functionsBySignature,
                final Map<String, AbiEvent> eventsBySignature,
                final AbiFunction constructor) {
            this.functionsByName = functionsByName;
            this.functionsBySignature = functionsBySignature;
            this.eventsBySignature = eventsBySignature;
            this.constructor = constructor;
        }
    }

    @Override
    public <T> List<T> decodeEvents(
            final String eventName,
            final List<io.brane.core.model.LogEntry> logs,
            final Class<T> eventType) {
        Objects.requireNonNull(eventName, "eventName");
        Objects.requireNonNull(logs, "logs");
        final List<AbiEvent> matching = eventsBySignature.values().stream()
                .filter(ev -> ev.name().equals(eventName))
                .toList();
        if (matching.isEmpty()) {
            throw new AbiDecodingException("Unknown event '" + eventName + "'");
        }

        final List<T> decoded = new ArrayList<>();
        for (io.brane.core.model.LogEntry log : logs) {
            for (AbiEvent event : matching) {
                if (!matchesEvent(event, log)) {
                    continue;
                }
                decoded.add(decodeEvent(event, log, eventType));
            }
        }
        return decoded;
    }

    static List<MulticallResult> decodeMulticallResults(final String hex) {
        if (hex == null || hex.isBlank()) {
            throw new AbiDecodingException("Multicall3 returned an empty or null result. This usually indicates an RPC provider error or a call to a non-existent contract.");
        }

        final byte[] data = Hex.decode(hex);
        if (data.length == 0) {
            throw new AbiDecodingException(
                    "Multicall3 returned empty data (0x). This usually indicates the Multicall3 contract is not deployed at the target address.");
        }

        // Schema for (bool success, bytes returnData)[]
        final TypeSchema multicallResultSchema = new TypeSchema.ArraySchema(
                new TypeSchema.TupleSchema(List.of(
                        new TypeSchema.BoolSchema(),
                        new TypeSchema.BytesSchema(true))),
                -1);

        try {
            final List<AbiType> decoded = AbiDecoder.decode(data, List.of(multicallResultSchema));
            if (decoded.isEmpty() || !(decoded.get(0) instanceof Array<?> array)) {
                return Collections.emptyList();
            }

            final List<MulticallResult> results = new ArrayList<>(array.values().size());
            for (int i = 0; i < array.values().size(); i++) {
                final Object item = array.values().get(i);
                if (!(item instanceof Tuple tuple) || tuple.components().size() != 2) {
                    throw new AbiDecodingException(
                            "Multicall3 result[" + i + "] has unexpected format: expected (bool, bytes) tuple, got "
                                    + (item == null ? "null" : item.getClass().getSimpleName()));
                }
                final Boolean success = ((Bool) tuple.components().get(0)).value();
                final HexData returnData = ((Bytes) tuple.components().get(1)).value();
                results.add(new MulticallResult(success, returnData));
            }
            return results;
        } catch (Exception e) {
            throw new AbiDecodingException("Failed to decode Multicall3 results", e);
        }
    }

    private boolean matchesEvent(final AbiEvent event, final io.brane.core.model.LogEntry log) {
        if (log.topics() == null || log.topics().isEmpty()) {
            return false;
        }
        final String topic0 = Abi.eventTopic(event.signature()).value();
        return topic0.equalsIgnoreCase(log.topics().get(0).value());
    }

    private <T> T decodeEvent(
            final AbiEvent event, final io.brane.core.model.LogEntry log, final Class<T> eventType) {
        final List<AbiParameter> params = event.inputs();
        final List<Object> values = new ArrayList<>(params.size());

        int topicIdx = 1;
        final List<TypeSchema> nonIndexedSchemas = new ArrayList<>();
        for (AbiParameter param : params) {
            if (param.indexed) {
                if (log.topics().size() <= topicIdx) {
                    throw new AbiDecodingException("Missing topic for indexed param '" + param.name + "'");
                }
                final String topic = log.topics().get(topicIdx).value();
                try {
                    // Indexed params are just 32 bytes (except dynamic types which are hashed)
                    // For now assume simple types.
                    // We need to decode the topic hex.
                    final byte[] topicBytes = Hex.decode(topic);
                    final TypeSchema schema = toTypeSchema(param);
                    // Decode as if it's a single static value
                    // But AbiDecoder expects a tuple.
                    // Actually indexed params are just values.
                    // We can use AbiDecoder.decodeStatic if we expose it, or wrap in tuple.
                    // Let's wrap in tuple.
                    final List<AbiType> decoded = AbiDecoder.decode(topicBytes, List.of(schema));
                    values.add(toJavaValue(decoded.get(0)));
                } catch (Exception e) {
                    throw new AbiDecodingException("Failed to decode indexed param '" + param.name + "'", e);
                }
                topicIdx++;
            } else {
                nonIndexedSchemas.add(toTypeSchema(param));
            }
        }

        if (!nonIndexedSchemas.isEmpty()) {
            final byte[] data = Hex.decode(log.data().value());
            final List<AbiType> decoded = AbiDecoder.decode(data, nonIndexedSchemas);
            for (AbiType t : decoded) {
                values.add(toJavaValue(t));
            }
        }

        return mapToEventType(values, eventType, event.name());
    }

    private <T> T mapToEventType(List<Object> values, Class<T> eventType, String eventName) {
        if (eventType == List.class || List.class.isAssignableFrom(eventType)) {
            @SuppressWarnings("unchecked")
            final T cast = (T) values;
            return cast;
        }
        if (eventType == Object[].class) {
            @SuppressWarnings("unchecked")
            final T cast = (T) values.toArray();
            return cast;
        }

        for (var ctor : eventType.getDeclaredConstructors()) {
            if (ctor.getParameterCount() == values.size()) {
                try {
                    ctor.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    final T instance = (T) ctor.newInstance(values.toArray());
                    return instance;
                } catch (Exception e) {
                    // Try next constructor - type mismatch or invocation failure
                    LOG.debug("Constructor {} failed for event '{}': {}",
                            ctor.toGenericString(), eventName, e.getMessage());
                }
            }
        }
        throw new AbiDecodingException(
                "Cannot map event '" + eventName + "' to " + eventType.getName());
    }

    private static Object toJavaValue(final AbiType type) {
        return switch (type) {
            case AddressType addr -> addr.value();
            case Bool b -> b.value();
            case Utf8String s -> s.value();
            case Bytes bytes -> bytes.value();
            case Array<?> array -> {
                final List<Object> list = new ArrayList<>();
                for (AbiType t : array.values()) {
                    list.add(toJavaValue(t));
                }
                yield list;
            }
            case UInt u -> u.value();
            case Int i -> i.value();
            case Tuple t -> {
                final List<Object> list = new ArrayList<>();
                for (AbiType c : t.components()) {
                    list.add(toJavaValue(c));
                }
                yield list;
            }
        };
    }

    private static TypeSchema toTypeSchema(final AbiParameter param) {
        final String solidityType = param.type;
        if (solidityType.endsWith("[]")) {
            String base = solidityType.substring(0, solidityType.length() - 2);
            AbiParameter baseParam = new AbiParameter("", base, false, param.components);
            return new TypeSchema.ArraySchema(toTypeSchema(baseParam), -1);
        }
        if (solidityType.equals("tuple")) {
            if (param.components == null || param.components.isEmpty()) {
                throw new AbiEncodingException("Tuple missing components");
            }
            List<TypeSchema> componentSchemas = new ArrayList<>();
            for (AbiParameter component : param.components) {
                componentSchemas.add(toTypeSchema(component));
            }
            return new TypeSchema.TupleSchema(componentSchemas);
        }
        String normalized = solidityType.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("uint")) {
            int width = 256;
            if (normalized.length() > 4)
                width = Integer.parseInt(normalized.substring(4));
            return new TypeSchema.UIntSchema(width);
        }
        if (normalized.startsWith("int")) {
            int width = 256;
            if (normalized.length() > 3)
                width = Integer.parseInt(normalized.substring(3));
            return new TypeSchema.IntSchema(width);
        }
        if (normalized.equals("address"))
            return new TypeSchema.AddressSchema();
        if (normalized.equals("bool"))
            return new TypeSchema.BoolSchema();
        if (normalized.equals("string"))
            return new TypeSchema.StringSchema();
        if (normalized.equals("bytes"))
            return new TypeSchema.BytesSchema(true);
        if (normalized.startsWith("bytes"))
            return new TypeSchema.BytesSchema(false);

        throw new AbiEncodingException("Unsupported type schema: " + solidityType);
    }

    private static final class Call implements Abi.FunctionCall {
        private final AbiFunction abiFunction;
        private final HexData data;

        private Call(final AbiFunction abiFunction, final HexData data) {
            this.abiFunction = Objects.requireNonNull(abiFunction, "abiFunction");
            this.data = Objects.requireNonNull(data, "data");
        }

        @Override
        public String data() {
            return data.value();
        }

        @Override
        public <T> T decode(final String rawResultHex, final Class<T> returnType) {
            if (returnType == null) {
                throw new AbiDecodingException("returnType must not be null");
            }
            if (rawResultHex == null || rawResultHex.isBlank() || rawResultHex.length() < 4) {
                throw new AbiDecodingException("Raw result is empty");
            }

            final byte[] data = Hex.decode(rawResultHex);
            if (data.length == 0) {
                if (abiFunction.outputs().isEmpty())
                    return null;
                throw new AbiDecodingException("No data returned");
            }

            try {
                if (abiFunction.outputs().isEmpty()) {
                    return null;
                }

                final List<TypeSchema> schemas = new ArrayList<>();
                for (AbiParameter param : abiFunction.outputs()) {
                    schemas.add(toTypeSchema(param));
                }

                final List<AbiType> decoded = AbiDecoder.decode(data, schemas);
                if (decoded.isEmpty()) {
                    throw new AbiDecodingException("No values decoded for " + abiFunction.name());
                }

                if (decoded.size() > 1) {
                    final List<Object> mapped = decoded.stream()
                            .map(InternalAbi::toJavaValue)
                            .toList();
                    return mapToEventType(mapped, returnType, abiFunction.name());
                }

                final Object mapped = toJavaValue(decoded.get(0));
                return mapValue(mapped, returnType);
            } catch (AbiDecodingException e) {
                throw e;
            } catch (Exception e) {
                throw new AbiDecodingException(
                        "Failed to decode output for " + abiFunction.signature(), e);
            }
        }

        private <T> T mapToEventType(List<Object> values, Class<T> eventType, String eventName) {
            if (eventType == List.class || List.class.isAssignableFrom(eventType)) {
                @SuppressWarnings("unchecked")
                final T cast = (T) values;
                return cast;
            }
            if (eventType == Object[].class) {
                @SuppressWarnings("unchecked")
                final T cast = (T) values.toArray();
                return cast;
            }
            throw new AbiDecodingException(
                    "Function " + eventName + " returns multiple values; use List or Object[]");
        }

        private <T> T mapValue(final Object value, final Class<T> targetType) {
            if (value == null)
                return null;
            if (targetType.isInstance(value)) {
                @SuppressWarnings("unchecked")
                final T cast = (T) value;
                return cast;
            }
            // Coercion logic
            if (value instanceof BigInteger bi) {
                return coerceNumber(bi, targetType);
            }
            if (value instanceof HexData hex && targetType == byte[].class) {
                @SuppressWarnings("unchecked")
                final T cast = (T) Hex.decode(hex.value());
                return cast;
            }
            if (value instanceof Address addr && targetType == String.class) {
                @SuppressWarnings("unchecked")
                final T cast = (T) addr.value();
                return cast;
            }

            throw new AbiDecodingException(
                    "Cannot map " + value.getClass().getSimpleName() + " to " + targetType.getName());
        }

        private <T> T coerceNumber(final BigInteger value, final Class<T> targetType) {
            if (targetType == BigInteger.class || targetType == Object.class) {
                @SuppressWarnings("unchecked")
                final T cast = (T) value;
                return cast;
            }
            if (targetType == Long.class || targetType == long.class) {
                @SuppressWarnings("unchecked")
                final T cast = (T) Long.valueOf(value.longValueExact());
                return cast;
            }
            if (targetType == Integer.class || targetType == int.class) {
                @SuppressWarnings("unchecked")
                final T cast = (T) Integer.valueOf(value.intValueExact());
                return cast;
            }
            throw new AbiDecodingException("Cannot map numeric value to " + targetType.getName());
        }
    }
}

