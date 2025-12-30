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

        final Map<Method, Abi.FunctionMetadata> mutableCache = new HashMap<>();
        for (Method method : contractInterface.getMethods()) {
            if (isObjectMethod(method)) {
                continue;
            }
            final Abi.FunctionMetadata metadata = resolveMetadata(method);
            mutableCache.put(method, metadata);
        }
        // Create immutable snapshot for thread-safe publication
        this.cache = Map.copyOf(mutableCache);
    }

    public Abi.FunctionMetadata resolve(final Method method) {
        final Abi.FunctionMetadata metadata = cache.get(method);
        if (metadata == null) {
            throw new IllegalArgumentException("No ABI binding found for method " + method.getName());
        }
        return metadata;
    }

    private Abi.FunctionMetadata resolveMetadata(final Method method) {
        // Note: Parameter count validation is intentionally NOT done here.
        // BraneContract.validateParameters() handles this with @Payable annotation awareness.
        return abi.getFunction(method.getName())
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "No ABI function named '" + method.getName() + "'"));
    }

    private static boolean isObjectMethod(final Method method) {
        return method.getDeclaringClass() == Object.class;
    }
}

