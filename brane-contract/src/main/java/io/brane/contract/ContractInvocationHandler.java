package io.brane.contract;

import io.brane.core.builder.TxBuilder;
import io.brane.core.abi.Abi;
import io.brane.core.abi.AbiBinding;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.PublicClient;
import io.brane.rpc.WalletClient;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class ContractInvocationHandler implements InvocationHandler {

    private static final long DEFAULT_TIMEOUT_MILLIS = 10_000L;
    private static final long DEFAULT_POLL_INTERVAL_MILLIS = 500L;

    private final Address address;
    private final Abi abi;
    private final AbiBinding binding;
    private final PublicClient publicClient;
    private final WalletClient walletClient;

    ContractInvocationHandler(
            final Address address,
            final Abi abi,
            final AbiBinding binding,
            final PublicClient publicClient,
            final WalletClient walletClient) {
        this.address = Objects.requireNonNull(address, "address");
        this.abi = Objects.requireNonNull(abi, "abi");
        this.binding = Objects.requireNonNull(binding, "binding");
        this.publicClient = Objects.requireNonNull(publicClient, "publicClient");
        this.walletClient = Objects.requireNonNull(walletClient, "walletClient");
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        if (isObjectMethod(method)) {
            return handleObjectMethod(proxy, method, args);
        }

        final Object[] invocationArgs = args == null ? new Object[0] : args;
        final Abi.FunctionMetadata metadata = binding.resolve(method);
        final Abi.FunctionCall functionCall = abi.encodeFunction(metadata.name(), invocationArgs);

        if (metadata.isView()) {
            return invokeView(method, functionCall);
        }

        return invokeWrite(method, functionCall);
    }

    private Object invokeView(final Method method, final Abi.FunctionCall call) {
        final Map<String, Object> callObject = new LinkedHashMap<>();
        callObject.put("to", address.value());
        callObject.put("data", call.data());

        final String output = publicClient.call(callObject, "latest");
        if (method.getReturnType() == void.class || method.getReturnType() == Void.class) {
            return null;
        }
        return call.decode(output, method.getReturnType());
    }

    private Object invokeWrite(final Method method, final Abi.FunctionCall call) {
        final TransactionRequest request =
                TxBuilder.eip1559()
                        .to(address)
                        .data(new HexData(call.data()))
                        .value(Wei.of(0))
                        .build();

        final TransactionReceipt receipt =
                walletClient.sendTransactionAndWait(
                        request, DEFAULT_TIMEOUT_MILLIS, DEFAULT_POLL_INTERVAL_MILLIS);
        if (method.getReturnType() == void.class || method.getReturnType() == Void.class) {
            return null;
        }
        return receipt;
    }

    private static boolean isObjectMethod(final Method method) {
        return method.getDeclaringClass() == Object.class;
    }

    private Object handleObjectMethod(
            final Object proxy, final Method method, final Object[] args) throws Throwable {
        return switch (method.getName()) {
        case "toString" ->
                    "BraneContractProxy{" + "address=" + address.value() + "}";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
            default -> method.invoke(proxy, args);
        };
    }
}
