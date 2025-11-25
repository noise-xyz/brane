package io.brane.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.brane.contract.Abi.FunctionMetadata;
import io.brane.core.error.AbiDecodingException;
import io.brane.core.error.AbiEncodingException;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.internal.web3j.abi.FunctionEncoder;
import io.brane.internal.web3j.abi.FunctionReturnDecoder;
import io.brane.internal.web3j.abi.TypeDecoder;
import io.brane.internal.web3j.abi.TypeReference;
import io.brane.internal.web3j.abi.datatypes.Array;
import io.brane.internal.web3j.abi.datatypes.Bool;
import io.brane.internal.web3j.abi.datatypes.DynamicBytes;
import io.brane.internal.web3j.abi.datatypes.Function;
import io.brane.internal.web3j.abi.datatypes.Type;
import io.brane.internal.web3j.abi.datatypes.Utf8String;
import io.brane.internal.web3j.abi.datatypes.generated.Int256;
import io.brane.internal.web3j.abi.datatypes.generated.Uint256;
import io.brane.internal.web3j.utils.Numeric;
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

    InternalAbi(final String json) {
        final ParsedAbi parsed = parse(json);
        this.functionsByName = parsed.functionsByName;
        this.functionsBySignature = parsed.functionsBySignature;
        this.eventsBySignature = parsed.eventsBySignature;
    }

    @Override
    public FunctionCall encodeFunction(final String name, final Object... args) {
        final Object[] providedArgs = args == null ? new Object[0] : args;
        final AbiFunction fn = resolveFunction(name, providedArgs.length);

        final List<Type> encodedInputs = new ArrayList<>(fn.inputs().size());
        for (int i = 0; i < fn.inputs().size(); i++) {
            final AbiParameter param = fn.inputs().get(i);
            final Object normalizedValue = normalizeInput(param.type(), providedArgs[i]);
            encodedInputs.add(instantiate(param.type(), normalizedValue));
        }

        final List<TypeReference<?>> outputTypes = buildOutputs(fn.outputs());
        final Function function = new Function(fn.name(), encodedInputs, new ArrayList<>(outputTypes));
        final String data = FunctionEncoder.encode(function);
        return new Call(fn, function, data, outputTypes);
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

    private static List<TypeReference<?>> buildOutputs(final List<AbiParameter> outputs) {
        if (outputs.isEmpty()) {
            return Collections.emptyList();
        }

        final List<TypeReference<?>> refs = new ArrayList<>(outputs.size());
        for (AbiParameter param : outputs) {
            try {
                refs.add(TypeReference.makeTypeReference(param.type()));
            } catch (ClassNotFoundException e) {
                throw new AbiEncodingException(
                        "Unsupported output type '" + param.type() + "'", e);
            }
        }
        return refs;
    }

    private static Type instantiate(final String solidityType, final Object value) {
        try {
            return TypeDecoder.instantiateType(solidityType, value);
        } catch (Exception e) {
            throw new AbiEncodingException(
                    "Unable to encode argument of type " + solidityType, e);
        }
    }

    private static Object normalizeInput(final String solidityType, final Object value) {
        if (value == null) {
            throw new AbiEncodingException("Argument for type '" + solidityType + "' is null");
        }

        if (solidityType.endsWith("[]")) {
            final String baseType = solidityType.substring(0, solidityType.length() - 2);
            final List<?> listValue = asList(value);
            final List<Object> normalized = new ArrayList<>(listValue.size());
            for (Object element : listValue) {
                normalized.add(normalizeInput(baseType, element));
            }
            return normalized;
        }

        final String normalizedType = solidityType.toLowerCase(Locale.ROOT);
        return switch (normalizedType) {
            case "uint256" -> toBigInteger(value, false);
            case "int256" -> toBigInteger(value, true);
            case "address" -> switch (value) {
                case Address address -> address.value();
                default -> throw new AbiEncodingException("Expected Address for type 'address'");
            };
            case "bool" -> switch (value) {
                case Boolean ignored -> value;
                default -> throw new AbiEncodingException("Expected Boolean for type 'bool'");
            };
            case "string" -> switch (value) {
                case String ignored -> value;
                default -> throw new AbiEncodingException("Expected String for type 'string'");
            };
            default -> {
                if (normalizedType.equals("bytes") || normalizedType.startsWith("bytes")) {
                    yield normalizeBytes(value, normalizedType);
                }
                throw new AbiEncodingException("Unsupported argument type '" + solidityType + "'");
            }
        };
    }

    private static Object normalizeBytes(final Object value, final String solidityType) {
        return switch (value) {
            case byte[] bytes -> {
                validateBytesLength(bytes, solidityType);
                yield bytes;
            }
            case HexData hex -> {
                final byte[] bytes = Numeric.hexStringToByteArray(hex.value());
                validateBytesLength(bytes, solidityType);
                yield bytes;
            }
            default -> throw new AbiEncodingException(
                    "Unsupported bytes value for type '" + solidityType + "'");
        };
    }

    private static void validateBytesLength(final byte[] bytes, final String solidityType) {
        if (!solidityType.equals("bytes") && solidityType.startsWith("bytes")) {
            final int expected = Integer.parseInt(solidityType.substring("bytes".length()));
            if (bytes.length != expected) {
                throw new AbiEncodingException(
                        "Expected "
                                + expected
                                + " bytes for type '"
                                + solidityType
                                + "' but was "
                                + bytes.length);
            }
        }
    }

    private static BigInteger toBigInteger(final Object value, final boolean signed) {
        return switch (value) {
            case BigInteger bi -> ensureSign(bi, signed);
            case Number number -> ensureSign(BigInteger.valueOf(number.longValue()), signed);
            case null ->
                throw new AbiEncodingException(
                        "Expected numeric value for "
                                + (signed ? "int256" : "uint256")
                                + " but got null");
            default ->
                throw new AbiEncodingException(
                        "Expected numeric value for "
                                + (signed ? "int256" : "uint256")
                                + " but got "
                                + value.getClass().getSimpleName());
        };
    }

    private static BigInteger ensureSign(final BigInteger value, final boolean signed) {
        if (!signed && value.signum() < 0) {
            throw new AbiEncodingException("uint256 cannot be negative");
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
            }
        }

        return new ParsedAbi(functionsByName, functionsBySignature, eventsBySignature);
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
            params.add(new AbiParameter(name, type, indexed));
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

    private record AbiParameter(String name, String type, boolean indexed) {
    }

    private record AbiFunction(
            String name, String stateMutability, List<AbiParameter> inputs, List<AbiParameter> outputs) {
        String signature() {
            final String joined = inputs.stream().map(AbiParameter::type).collect(Collectors.joining(","));
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
            final String joined = inputs.stream().map(AbiParameter::type).collect(Collectors.joining(","));
            return name + "(" + joined + ")";
        }
    }

    private record ParsedAbi(
            Map<String, AbiFunction> functionsByName,
            Map<String, AbiFunction> functionsBySignature,
            Map<String, AbiEvent> eventsBySignature) {
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
        final List<TypeReference<?>> nonIndexed = new ArrayList<>();
        for (AbiParameter param : params) {
            if (param.indexed()) {
                if (log.topics().size() <= topicIdx) {
                    throw new AbiDecodingException("Missing topic for indexed param '" + param.name() + "'");
                }
                final String topic = log.topics().get(topicIdx).value();
                try {
                    @SuppressWarnings("unchecked")
                    final TypeReference<Type> ref = (TypeReference<Type>) TypeReference.makeTypeReference(param.type());
                    final Type decoded = TypeDecoder.decode(topic, ref.getClassType());
                    values.add(toJavaValue(decoded));
                } catch (Exception e) {
                    throw new AbiDecodingException("Failed to decode indexed param '" + param.name() + "'", e);
                }
                topicIdx++;
            } else {
                try {
                    @SuppressWarnings("unchecked")
                    final TypeReference<Type> ref = (TypeReference<Type>) TypeReference.makeTypeReference(param.type());
                    nonIndexed.add(ref);
                } catch (ClassNotFoundException e) {
                    throw new AbiDecodingException("Unsupported non-indexed type '" + param.type() + "'", e);
                }
            }
        }

        if (!nonIndexed.isEmpty()) {
            final List<Type> decoded = FunctionReturnDecoder.decode(log.data().value(), castNonIndexed(nonIndexed));
            for (Type t : decoded) {
                values.add(toJavaValue(t));
            }
        }

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
                "Cannot map event '" + event.name() + "' to " + eventType.getName());
    }

    private static Object toJavaValue(final Type type) {
        return switch (type) {
            case io.brane.internal.web3j.abi.datatypes.Address addr -> new Address(addr.getValue());
            case Bool b -> b.getValue();
            case Utf8String s -> s.getValue();
            case DynamicBytes dyn -> new HexData("0x" + Numeric.toHexStringNoPrefix(dyn.getValue()));
            case io.brane.internal.web3j.abi.datatypes.Bytes bytes ->
                new HexData("0x" + Numeric.toHexStringNoPrefix(bytes.getValue()));
            case Array<?> array -> {
                final List<Object> list = new ArrayList<>();
                for (Type t : array.getValue()) {
                    list.add(toJavaValue(t));
                }
                yield list;
            }
            case Type t when t.getValue() instanceof BigInteger bi -> bi;
            case Type t -> t.getValue();
        };
    }

    private static final class Call implements Abi.FunctionCall {
        private final AbiFunction abiFunction;
        private final Function function;
        private final String data;
        private final List<TypeReference<?>> outputTypes;

        private Call(
                final AbiFunction abiFunction,
                final Function function,
                final String data,
                final List<TypeReference<?>> outputTypes) {
            this.abiFunction = Objects.requireNonNull(abiFunction, "abiFunction");
            this.function = Objects.requireNonNull(function, "function");
            this.data = Objects.requireNonNull(data, "data");
            this.outputTypes = Objects.requireNonNull(outputTypes, "outputTypes");
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
            // Require at least one full 32-byte word after stripping 0x.
            final int hexLength = Numeric.cleanHexPrefix(rawResultHex).length();
            if (hexLength < 64) {
                throw new AbiDecodingException(
                        "Invalid ABI result length for " + abiFunction.signature());
            }

            try {
                if (abiFunction.outputs().isEmpty()) {
                    return null;
                }

                @SuppressWarnings("unchecked")
                final List<Type> decoded = (List<Type>) (List<?>) FunctionReturnDecoder.decode(
                        rawResultHex,
                        (List<TypeReference<Type>>) (List<?>) outputTypes);
                if (decoded == null || decoded.isEmpty()) {
                    throw new AbiDecodingException("No values decoded for " + abiFunction.name());
                }

                if (decoded.size() > 1) {
                    final List<Object> mapped = decoded.stream()
                            .map(value -> mapValue(value, Object.class))
                            .toList();
                    if (returnType == Object[].class) {
                        @SuppressWarnings("unchecked")
                        final T cast = (T) mapped.toArray();
                        return cast;
                    }
                    if (returnType == List.class || List.class.isAssignableFrom(returnType)) {
                        @SuppressWarnings("unchecked")
                        final T cast = (T) mapped;
                        return cast;
                    }
                    throw new AbiDecodingException(
                            "Function "
                                    + abiFunction.name()
                                    + " returns multiple values; use List or Object[]");
                }

                final Object mapped = mapValue(decoded.get(0), returnType);
                if (mapped == null) {
                    return null;
                }
                if (!returnType.isInstance(mapped) && !isPrimitiveWrapper(returnType, mapped)) {
                    throw new AbiDecodingException(
                            "Cannot map "
                                    + decoded.get(0).getTypeAsString()
                                    + " to "
                                    + returnType.getName());
                }
                @SuppressWarnings("unchecked")
                final T cast = (T) mapped;
                return cast;
            } catch (AbiDecodingException e) {
                throw e;
            } catch (Exception e) {
                throw new AbiDecodingException(
                        "Failed to decode output for " + abiFunction.signature(), e);
            }
        }

        private static boolean isPrimitiveWrapper(final Class<?> expected, final Object value) {
            return switch (expected) {
                case Class<?> clazz when clazz == int.class -> value instanceof Integer;
                case Class<?> clazz when clazz == long.class -> value instanceof Long;
                case Class<?> clazz when clazz == boolean.class -> value instanceof Boolean;
                case Class<?> clazz when clazz == byte[].class -> value instanceof byte[];
                default -> false;
            };
        }

        private static Object mapValue(final Type type, final Class<?> targetType) {
            return switch (type) {
                case io.brane.internal.web3j.abi.datatypes.Address addr -> mapAddress(addr, targetType);
                case Bool bool -> mapBoolean(bool, targetType);
                case Utf8String utf8 -> mapUtf8(utf8, targetType);
                case DynamicBytes dynBytes -> mapBytes(dynBytes.getValue(), targetType);
                case io.brane.internal.web3j.abi.datatypes.Bytes bytesType ->
                    mapBytes(bytesType.getValue(), targetType);
                case Array<?> array -> mapArray(array, targetType);
                case Uint256 uint -> coerceNumber((BigInteger) uint.getValue(), targetType);
                case Int256 sint -> coerceNumber((BigInteger) sint.getValue(), targetType);
                default -> mapUnknown(type, targetType);
            };
        }

        private static Object mapAddress(
                final io.brane.internal.web3j.abi.datatypes.Address addr, final Class<?> targetType) {
            final Address value = new Address(addr.getValue());
            return switch (targetType) {
                case Class<?> c when c == Address.class || c == Object.class -> value;
                default -> throw new AbiDecodingException("Cannot map address to " + targetType.getName());
            };
        }

        private static Object mapBoolean(final Bool bool, final Class<?> targetType) {
            final Boolean value = bool.getValue();
            return switch (targetType) {
                case Class<?> c when c == Boolean.class || c == boolean.class || c == Object.class -> value;
                default -> throw new AbiDecodingException("Cannot map bool to " + targetType.getName());
            };
        }

        private static Object mapUtf8(final Utf8String utf8, final Class<?> targetType) {
            final String value = utf8.getValue();
            return switch (targetType) {
                case Class<?> c when c == String.class || c == Object.class -> value;
                default -> throw new AbiDecodingException("Cannot map string to " + targetType.getName());
            };
        }

        private static Object mapBytes(final byte[] bytes, final Class<?> targetType) {
            return switch (targetType) {
                case Class<?> c when c == HexData.class || c == Object.class ->
                    new HexData("0x" + Numeric.toHexStringNoPrefix(bytes));
                case Class<?> c when c == byte[].class -> bytes;
                default -> throw new AbiDecodingException("Cannot map bytes to " + targetType.getName());
            };
        }

        private static Object mapArray(final Array<?> array, final Class<?> targetType) {
            @SuppressWarnings("unchecked")
            final List<Type> values = (List<Type>) (List<?>) array.getValue();
            final List<Object> mapped = new ArrayList<>(values.size());
            for (Type element : values) {
                mapped.add(mapValue(element, Object.class));
            }
            return switch (targetType) {
                case Class<?> c when c == List.class || List.class.isAssignableFrom(c) || c == Object.class -> mapped;
                case Class<?> c when c == Object[].class -> mapped.toArray();
                default -> throw new AbiDecodingException("Cannot map array to " + targetType.getName());
            };
        }

        private static Object mapUnknown(final Type type, final Class<?> targetType) {
            final Object value = type.getValue();
            if (value instanceof BigInteger bi) {
                return coerceNumber(bi, targetType);
            }
            if (value != null && targetType.isInstance(value)) {
                return value;
            }

            throw new AbiDecodingException(
                    "Unsupported output type mapping for " + targetType.getName());
        }

        private static Object coerceNumber(final BigInteger value, final Class<?> targetType) {
            return switch (targetType) {
                case Class<?> c when c == BigInteger.class || c == Object.class -> value;
                case Class<?> c when c == Long.class || c == long.class -> value.longValueExact();
                case Class<?> c when c == Integer.class || c == int.class -> value.intValueExact();
                default -> throw new AbiDecodingException("Cannot map numeric value to " + targetType.getName());
            };
        }
    }

    @SuppressWarnings("unchecked")
    private static List<TypeReference<Type>> castNonIndexed(final List<TypeReference<?>> list) {
        final List<TypeReference<Type>> result = new ArrayList<>(list.size());
        for (TypeReference<?> ref : list) {
            result.add((TypeReference<Type>) ref);
        }
        return result;
    }
}
