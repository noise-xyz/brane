package io.brane.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.brane.core.abi.AbiDecoder;
import io.brane.core.abi.AbiEncoder;
import io.brane.core.abi.AbiType;
import io.brane.core.abi.AddressType;
import io.brane.core.abi.Array;
import io.brane.core.abi.Bool;
import io.brane.core.abi.Bytes;
import io.brane.core.abi.Int;
import io.brane.core.abi.Tuple;
import io.brane.core.abi.TypeSchema;
import io.brane.core.abi.UInt;
import io.brane.core.abi.Utf8String;
import io.brane.core.error.AbiDecodingException;
import io.brane.core.error.AbiEncodingException;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.primitives.Hex;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

final class InternalAbi implements Abi {

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

        final List<AbiType> encodedInputs = new ArrayList<>(fn.inputs().size());
        for (int i = 0; i < fn.inputs().size(); i++) {
            final AbiParameter param = fn.inputs().get(i);
            encodedInputs.add(toAbiType(param, providedArgs[i]));
        }

        final byte[] data = AbiEncoder.encodeFunction(fn.signature(), encodedInputs);
        return new Call(fn, Hex.encode(data));
    }

    @Override
    public HexData encodeConstructor(final Object... args) {
        if (constructor == null) {
            if (args != null && args.length > 0) {
                throw new AbiEncodingException("Constructor not defined in ABI, but arguments provided");
            }
            return new HexData("0x");
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

        final List<AbiType> encodedInputs = new ArrayList<>(constructor.inputs().size());
        for (int i = 0; i < constructor.inputs().size(); i++) {
            final AbiParameter param = constructor.inputs().get(i);
            encodedInputs.add(toAbiType(param, providedArgs[i]));
        }

        final byte[] encoded = AbiEncoder.encode(encodedInputs);
        return new HexData(Hex.encode(encoded));
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

    private static AbiType toAbiType(final AbiParameter param, final Object value) {
        final String solidityType = param.type();
        if (value == null) {
            throw new AbiEncodingException("Argument for type '" + solidityType + "' is null");
        }

        if (solidityType.endsWith("[]")) {
            final String baseType = solidityType.substring(0, solidityType.length() - 2);
            final List<?> listValue = asList(value);
            final List<AbiType> elements = new ArrayList<>(listValue.size());
            final AbiParameter elementParam = new AbiParameter("", baseType, false, param.components());
            for (Object element : listValue) {
                elements.add(toAbiType(elementParam, element));
            }
            // Infer element class from first element or generic AbiType
            return new Array<AbiType>(elements, AbiType.class, true);
        }

        if (solidityType.equals("tuple")) {
            if (param.components() == null || param.components().isEmpty()) {
                throw new AbiEncodingException("Tuple parameter missing components");
            }
            final List<?> listValue = asList(value);
            if (listValue.size() != param.components().size()) {
                throw new AbiEncodingException(
                        "Tuple expects "
                                + param.components().size()
                                + " components but got "
                                + listValue.size());
            }

            final List<AbiType> components = new ArrayList<>();
            for (int i = 0; i < param.components().size(); i++) {
                final AbiParameter componentParam = param.components().get(i);
                final Object componentValue = listValue.get(i);
                components.add(toAbiType(componentParam, componentValue));
            }
            return new Tuple(components);
        }

        final String normalizedType = solidityType.toLowerCase(Locale.ROOT);

        if (normalizedType.startsWith("uint")) {
            int width = 256;
            if (normalizedType.length() > 4) {
                width = Integer.parseInt(normalizedType.substring(4));
            }
            return new UInt(width, toBigInteger(value, false));
        }
        if (normalizedType.startsWith("int")) {
            int width = 256;
            if (normalizedType.length() > 3) {
                width = Integer.parseInt(normalizedType.substring(3));
            }
            return new Int(width, toBigInteger(value, true));
        }
        if (normalizedType.equals("address")) {
            if (value instanceof Address a)
                return new AddressType(a);
            if (value instanceof String s)
                return new AddressType(new Address(s));
            throw new AbiEncodingException("Expected Address for type 'address'");
        }
        if (normalizedType.equals("bool")) {
            if (value instanceof Boolean b)
                return new Bool(b);
            throw new AbiEncodingException("Expected Boolean for type 'bool'");
        }
        if (normalizedType.equals("string")) {
            if (value instanceof String s)
                return new Utf8String(s);
            throw new AbiEncodingException("Expected String for type 'string'");
        }
        if (normalizedType.equals("bytes")) {
            return toBytes(value, true);
        }
        if (normalizedType.startsWith("bytes")) {
            // bytesN
            return toBytes(value, false);
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
            default ->
                throw new AbiEncodingException(
                        "Expected numeric value for "
                                + (signed ? "int" : "uint")
                                + " but got "
                                + value.getClass().getSimpleName());
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
        for (JsonNode param : array) {
            final String type = param.path("type").asText();
            if (type == null || type.isBlank()) {
                throw new AbiEncodingException("ABI parameter missing type");
            }
            final String name = param.path("name").asText("");
            final boolean indexed = param.path("indexed").asBoolean(false);
            final List<AbiParameter> components = parseParameters(arrayField(param, "components"));
            params.add(new AbiParameter(name, type, indexed, components));
        }
        return params;
    }

    private static ArrayNode arrayField(final JsonNode node, final String field) {
        final JsonNode value = node.get(field);
        if (value != null && value.isArray()) {
            return (ArrayNode) value;
        }
        return MAPPER.createArrayNode();
    }

    private static String requireText(
            final JsonNode node, final String field, final String entryType) {
        final String value = node.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new AbiEncodingException(entryType + " missing required field '" + field + "'");
        }
        return value;
    }

    private record AbiParameter(String name, String type, boolean indexed, List<AbiParameter> components) {
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
            final List<String> inputTypes = inputs.stream().map(AbiParameter::type).toList();
            final List<String> outputTypes = outputs.stream().map(AbiParameter::type).toList();
            return new FunctionMetadata(name, stateMutability, inputTypes, outputTypes);
        }
    }

    private record AbiEvent(String name, List<AbiParameter> inputs) {
        String signature() {
            final String joined = inputs.stream().map(AbiParameter::canonicalType).collect(Collectors.joining(","));
            return name + "(" + joined + ")";
        }
    }

    private record ParsedAbi(
            Map<String, AbiFunction> functionsByName,
            Map<String, AbiFunction> functionsBySignature,
            Map<String, AbiEvent> eventsBySignature,
            AbiFunction constructor) {
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
            if (param.indexed()) {
                if (log.topics().size() <= topicIdx) {
                    throw new AbiDecodingException("Missing topic for indexed param '" + param.name() + "'");
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
                    throw new AbiDecodingException("Failed to decode indexed param '" + param.name() + "'", e);
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
                } catch (Exception ignore) {
                    // try next
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
        final String solidityType = param.type();
        if (solidityType.endsWith("[]")) {
            String base = solidityType.substring(0, solidityType.length() - 2);
            AbiParameter baseParam = new AbiParameter("", base, false, param.components());
            return new TypeSchema.ArraySchema(toTypeSchema(baseParam), -1);
        }
        if (solidityType.equals("tuple")) {
            if (param.components() == null || param.components().isEmpty()) {
                throw new AbiEncodingException("Tuple missing components");
            }
            List<TypeSchema> componentSchemas = new ArrayList<>();
            for (AbiParameter component : param.components()) {
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
        private final String data;

        private Call(final AbiFunction abiFunction, final String data) {
            this.abiFunction = Objects.requireNonNull(abiFunction, "abiFunction");
            this.data = Objects.requireNonNull(data, "data");
        }

        @Override
        public String data() {
            return data;
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
