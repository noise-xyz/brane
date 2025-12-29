package io.brane.core.abi;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class AbiBinding {

    private final Abi abi;
    private final Map<Method, Abi.FunctionMetadata> cache;

    public AbiBinding(final Abi abi, final Class<?> contractInterface) {
        this.abi = Objects.requireNonNull(abi, "abi");
        Objects.requireNonNull(contractInterface, "contractInterface");
        this.cache = new HashMap<>();

        for (Method method : contractInterface.getMethods()) {
            if (isObjectMethod(method)) {
                continue;
            }
            final Abi.FunctionMetadata metadata = resolveMetadata(method);
            cache.put(method, metadata);
        }
    }

    public Abi.FunctionMetadata resolve(final Method method) {
        final Abi.FunctionMetadata metadata = cache.get(method);
        if (metadata == null) {
            throw new IllegalArgumentException("No ABI binding found for method " + method.getName());
        }
        return metadata;
    }

    private Abi.FunctionMetadata resolveMetadata(final Method method) {
        final Abi.FunctionMetadata metadata =
                abi.getFunction(method.getName())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "No ABI function named '" + method.getName() + "'"));

        if (metadata.inputs().size() != method.getParameterCount()) {
            throw new IllegalArgumentException(
                    "Method "
                            + method.getName()
                            + " expects "
                            + metadata.inputs().size()
                            + " parameters but has "
                            + method.getParameterCount());
        }

        return metadata;
    }

    private static boolean isObjectMethod(final Method method) {
        return method.getDeclaringClass() == Object.class;
    }
}

