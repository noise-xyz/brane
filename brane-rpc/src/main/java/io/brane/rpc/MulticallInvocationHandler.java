package io.brane.rpc;

import io.brane.core.abi.Abi;
import io.brane.core.abi.AbiBinding;
import io.brane.core.types.Address;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

/**
 * Invocation handler for Multicall3 recording proxies.
 * 
 * <p>
 * This handler intercepts method calls on a contract proxy and records the
 * function call metadata instead of executing it. The recorded call is then
 * pushed to the associated {@link MulticallBatch}.
 */
final class MulticallInvocationHandler implements InvocationHandler {

    private static final Map<Class<?>, Object> PRIMITIVE_DEFAULTS = Map.of(
            boolean.class, false,
            char.class, (char) 0,
            byte.class, (byte) 0,
            short.class, (short) 0,
            int.class, 0,
            long.class, 0L,
            float.class, 0.0f,
            double.class, 0.0d);

    private final Address address;
    private final Abi abi;
    private final AbiBinding binding;
    private final MulticallBatch batch;

    MulticallInvocationHandler(
            final Address address,
            final Abi abi,
            final AbiBinding binding,
            final MulticallBatch batch) {
        this.address = Objects.requireNonNull(address, "address");
        this.abi = Objects.requireNonNull(abi, "abi");
        this.binding = Objects.requireNonNull(binding, "binding");
        this.batch = Objects.requireNonNull(batch, "batch");
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        if (isObjectMethod(method)) {
            return handleObjectMethod(proxy, method, args);
        }

        final Object[] invocationArgs = args == null ? new Object[0] : args;
        final Abi.FunctionMetadata metadata = binding.resolve(method);
        final Abi.FunctionCall functionCall = abi.encodeFunction(metadata.name(), invocationArgs);

        if (!metadata.isView()) {
            throw new IllegalArgumentException("Only view/pure functions can be used in Multicall3 batching. Function '"
                    + metadata.name() + "' is state-changing.");
        }

        // Record the call in the batch
        batch.recordCall(address, functionCall, method.getReturnType());

        // Return a default value for the return type (will be ignored by batch.add)
        return defaultValueFor(method.getReturnType());
    }

    private static boolean isObjectMethod(final Method method) {
        return method.getDeclaringClass() == Object.class;
    }

    private Object handleObjectMethod(
            final Object proxy, final Method method, final Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "toString" -> "MulticallRecordingProxy{" + "address=" + address.value() + "}";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
            default -> method.invoke(proxy, args);
        };
    }

    private Object defaultValueFor(Class<?> type) {
        return PRIMITIVE_DEFAULTS.get(type);
    }
}

