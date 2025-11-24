package io.brane.contract;

import io.brane.core.model.TransactionReceipt;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.rpc.PublicClient;
import io.brane.rpc.WalletClient;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class BraneContract {

    private BraneContract() {}

    public static <T> T bind(
            final Address address,
            final String abiJson,
            final PublicClient publicClient,
            final WalletClient walletClient,
            final Class<T> contractInterface) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(abiJson, "abiJson");
        Objects.requireNonNull(publicClient, "publicClient");
        Objects.requireNonNull(walletClient, "walletClient");
        Objects.requireNonNull(contractInterface, "contractInterface");

        if (!contractInterface.isInterface()) {
            throw new IllegalArgumentException("contractInterface must be an interface");
        }

        final Abi abi = Abi.fromJson(abiJson);
        validateMethods(contractInterface, abi);

        final AbiBinding binding = new AbiBinding(abi, contractInterface);
        final ContractInvocationHandler handler =
                new ContractInvocationHandler(address, abi, binding, publicClient, walletClient);
        final Object proxy =
                Proxy.newProxyInstance(
                        contractInterface.getClassLoader(), new Class<?>[] {contractInterface}, handler);
        return contractInterface.cast(proxy);
    }

    private static void validateMethods(final Class<?> contractInterface, final Abi abi) {
        for (Method method : contractInterface.getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }

            final Abi.FunctionMetadata metadata =
                    abi.getFunction(method.getName())
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "No ABI function named '" + method.getName() + "'"));
            validateParameters(method, metadata);
            validateReturnType(method, metadata);
        }
    }

    private static void validateParameters(final Method method, final Abi.FunctionMetadata metadata) {
        final Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != metadata.inputs().size()) {
            throw new IllegalArgumentException(
                    "Method "
                            + method.getName()
                            + " expects "
                            + metadata.inputs().size()
                            + " parameters but has "
                            + parameterTypes.length);
        }

        for (int i = 0; i < parameterTypes.length; i++) {
            final String solidityType = metadata.inputs().get(i);
            final Class<?> parameterType = parameterTypes[i];
            if (!isSupportedParameterType(solidityType, parameterType)) {
                throw new IllegalArgumentException(
                        "Unsupported parameter type for "
                                + method.getName()
                                + " index "
                                + i
                                + ": "
                                + parameterType.getSimpleName()
                                + " -> "
                                + solidityType);
            }
        }
    }

    private static void validateReturnType(final Method method, final Abi.FunctionMetadata metadata) {
        final Class<?> returnType = method.getReturnType();
        final List<String> outputs = metadata.outputs();
        if (metadata.isView()) {
            if (returnType == void.class || returnType == Void.class) {
                if (!outputs.isEmpty()) {
                    throw new IllegalArgumentException(
                            "View function "
                                    + method.getName()
                                    + " must declare a return type for outputs");
                }
                return;
            }

            if (returnType == BigInteger.class) {
                requireSingleOutput(method, outputs, BraneContract::isNumericType);
                return;
            }

            if (returnType == Address.class) {
                requireSingleOutput(method, outputs, BraneContract::isAddressType);
                return;
            }

            if (returnType == Boolean.class || returnType == boolean.class) {
                requireSingleOutput(method, outputs, BraneContract::isBoolType);
                return;
            }

            throw new IllegalArgumentException(
                    "Unsupported return type for view function "
                            + method.getName()
                            + ": "
                            + returnType.getSimpleName());
        }

        if (returnType == TransactionReceipt.class
                || returnType == void.class
                || returnType == Void.class) {
            return;
        }

        throw new IllegalArgumentException(
                "Unsupported return type for non-view function "
                        + method.getName()
                        + ": "
                        + returnType.getSimpleName());
    }

    private static void requireSingleOutput(
            final Method method,
            final List<String> outputs,
            final java.util.function.Predicate<String> validator) {
        if (outputs.size() != 1 || !validator.test(outputs.getFirst())) {
            throw new IllegalArgumentException(
                    "Return type for " + method.getName() + " does not match ABI outputs");
        }
    }

    private static boolean isSupportedParameterType(
            final String solidityType, final Class<?> parameterType) {
        if (solidityType.endsWith("[]")) {
            return parameterType.isArray() || List.class.isAssignableFrom(parameterType);
        }

        final String normalized = solidityType.toLowerCase(Locale.ROOT);
        if (isNumericType(normalized)) {
            return Number.class.isAssignableFrom(parameterType)
                    || parameterType == byte.class
                    || parameterType == short.class
                    || parameterType == int.class
                    || parameterType == long.class;
        }

        if (isAddressType(normalized)) {
            return parameterType == Address.class;
        }

        if (isBoolType(normalized)) {
            return parameterType == Boolean.class || parameterType == boolean.class;
        }

        if ("string".equals(normalized)) {
            return parameterType == String.class;
        }

        if ("bytes".equals(normalized) || normalized.startsWith("bytes")) {
            return parameterType == byte[].class || parameterType == HexData.class;
        }

        return false;
    }

    private static boolean isNumericType(final String solidityType) {
        final String normalized = solidityType.toLowerCase(Locale.ROOT);
        return normalized.startsWith("uint") || normalized.startsWith("int");
    }

    private static boolean isAddressType(final String solidityType) {
        return "address".equals(solidityType.toLowerCase(Locale.ROOT));
    }

    private static boolean isBoolType(final String solidityType) {
        return "bool".equals(solidityType.toLowerCase(Locale.ROOT));
    }
}
