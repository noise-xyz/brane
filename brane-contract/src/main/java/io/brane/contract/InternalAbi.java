package io.brane.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.brane.internal.web3j.abi.FunctionEncoder;
import io.brane.internal.web3j.abi.FunctionReturnDecoder;
import io.brane.internal.web3j.abi.TypeDecoder;
import io.brane.internal.web3j.abi.TypeReference;
import io.brane.internal.web3j.abi.datatypes.Function;
import io.brane.internal.web3j.abi.datatypes.Type;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

final class InternalAbi implements Abi {

    private final JsonNode root;
    private final ObjectMapper mapper = new ObjectMapper();

    InternalAbi(final String json) {
        try {
            this.root = mapper.readTree(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to parse ABI json", e);
        }
    }

    @Override
    public FunctionCall encodeFunction(final String name, final Object... args) {
        final JsonNode fnNode = findFunction(name, args == null ? 0 : args.length);
        if (fnNode == null) {
            throw new IllegalArgumentException("Unknown function with name '" + name + "'");
        }

        final ArrayNode inputs = arrayField(fnNode, "inputs");
        final Object[] providedArgs = args == null ? new Object[0] : args;
        if (inputs.size() != providedArgs.length) {
            throw new IllegalArgumentException(
                    "Function "
                            + name
                            + " expects "
                            + inputs.size()
                            + " arguments but "
                            + providedArgs.length
                            + " were supplied");
        }

        final List<Type> encodedInputs = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            final JsonNode input = inputs.get(i);
            final String solidityType = input.path("type").asText();
            encodedInputs.add(instantiate(solidityType, providedArgs[i]));
        }

        final List<TypeReference<?>> outputs = buildOutputs(arrayField(fnNode, "outputs"));
        final String functionName = fnNode.path("name").asText(name);
        return new Call(new Function(functionName, encodedInputs, outputs));
    }

    private <T> T decodeReturn(
            final Function function, final String data, final Class<T> returnType) {
        if (returnType == Void.class || returnType == Void.TYPE) {
            return null;
        }

        if (function.getOutputParameters().isEmpty() || data == null) {
            return null;
        }

        final List<Type> decoded =
                FunctionReturnDecoder.decode(data, function.getOutputParameters());
        if (decoded.isEmpty()) {
            return null;
        }

        final Type value = decoded.get(0);
        if (returnType.isInstance(value)) {
            return returnType.cast(value);
        }

        final Object raw = value.getValue();
        if (raw == null) {
            return null;
        }

        if (returnType.isInstance(raw)) {
            return returnType.cast(raw);
        }

        if (raw instanceof Number number) {
            return coerceNumber(number, returnType);
        }

        if (returnType == String.class) {
            return returnType.cast(raw.toString());
        }

        if (returnType == byte[].class && raw instanceof byte[] bytes) {
            return returnType.cast(bytes);
        }

        throw new IllegalArgumentException(
                "Unsupported return type mapping for "
                        + returnType.getName()
                        + " using ABI definition");
    }

    @SuppressWarnings("unchecked")
    private <T> T coerceNumber(final Number number, final Class<T> returnType) {
        if (returnType == Integer.class || returnType == int.class) {
            return (T) Integer.valueOf(number.intValue());
        }
        if (returnType == Long.class || returnType == long.class) {
            return (T) Long.valueOf(number.longValue());
        }
        if (returnType == Short.class || returnType == short.class) {
            return (T) Short.valueOf(number.shortValue());
        }
        if (returnType == Byte.class || returnType == byte.class) {
            return (T) Byte.valueOf(number.byteValue());
        }
        if (returnType == Double.class || returnType == double.class) {
            return (T) Double.valueOf(number.doubleValue());
        }
        if (returnType == Float.class || returnType == float.class) {
            return (T) Float.valueOf(number.floatValue());
        }
        if (returnType == BigInteger.class && number instanceof BigInteger bigInteger) {
            return returnType.cast(bigInteger);
        }
        throw new IllegalArgumentException(
                "Cannot coerce numeric ABI value into " + returnType.getName());
    }

    private JsonNode findFunction(final String name, final int argCount) {
        if (root == null || !root.isArray()) {
            return null;
        }

        JsonNode fallback = null;
        final Iterator<JsonNode> iterator = root.elements();
        while (iterator.hasNext()) {
            final JsonNode node = iterator.next();
            if (!node.path("type")
                    .asText("")
                    .toLowerCase(Locale.ROOT)
                    .equals("function")) {
                continue;
            }

            if (!name.equals(node.path("name").asText())) {
                continue;
            }

            final JsonNode inputsNode = node.path("inputs");
            final int inputsSize = inputsNode.isArray() ? inputsNode.size() : 0;
            if (inputsSize == argCount) {
                return node;
            }
            fallback = node;
        }

        return fallback;
    }

    private List<TypeReference<?>> buildOutputs(final ArrayNode outputsNode) {
        if (outputsNode == null || outputsNode.isEmpty()) {
            return List.of();
        }

        final List<TypeReference<?>> outputs = new ArrayList<>(outputsNode.size());
        for (JsonNode node : outputsNode) {
            final String type = node.path("type").asText();
            try {
                @SuppressWarnings("unchecked")
                final TypeReference<Type> ref =
                        (TypeReference<Type>) TypeReference.makeTypeReference(type);
                outputs.add(ref);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("Unsupported return type " + type, e);
            }
        }
        return outputs;
    }

    private Type instantiate(final String solidityType, final Object value) {
        try {
            return TypeDecoder.instantiateType(solidityType, value);
        } catch (NoSuchMethodException
                | IllegalAccessException
                | InvocationTargetException
                | InstantiationException
                | ClassNotFoundException e) {
            throw new IllegalArgumentException("Unable to encode argument of type " + solidityType, e);
        }
    }

    private ArrayNode arrayField(final JsonNode node, final String field) {
        final JsonNode value = node.get(field);
        if (value != null && value.isArray()) {
            return (ArrayNode) value;
        }
        return mapper.createArrayNode();
    }

    private final class Call implements Abi.FunctionCall {
        private final Function function;

        private Call(final Function function) {
            this.function = function;
        }

        @Override
        public String data() {
            return FunctionEncoder.encode(function);
        }

        @Override
        public <T> T decode(final String output, final Class<T> returnType) {
            return decodeReturn(function, output, returnType);
        }
    }
}
