package io.brane.contract;

import io.brane.core.RevertDecoder;
import io.brane.core.builder.TxBuilder;
import io.brane.core.abi.Abi;
import io.brane.core.abi.AbiBinding;
import io.brane.core.error.AbiDecodingException;
import io.brane.core.error.RpcException;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.PublicClient;
import io.brane.rpc.WalletClient;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class ContractInvocationHandler implements InvocationHandler {

    private final Address address;
    private final Abi abi;
    private final AbiBinding binding;
    private final PublicClient publicClient;
    private final WalletClient walletClient;
    private final ContractOptions options;

    ContractInvocationHandler(
            final Address address,
            final Abi abi,
            final AbiBinding binding,
            final PublicClient publicClient,
            final WalletClient walletClient,
            final ContractOptions options) {
        this.address = Objects.requireNonNull(address, "address");
        this.abi = Objects.requireNonNull(abi, "abi");
        this.binding = Objects.requireNonNull(binding, "binding");
        this.publicClient = Objects.requireNonNull(publicClient, "publicClient");
        this.walletClient = Objects.requireNonNull(walletClient, "walletClient");
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        if (isObjectMethod(method)) {
            return handleObjectMethod(proxy, method, args);
        }

        final Object[] invocationArgs = args == null ? new Object[0] : args;
        final Abi.FunctionMetadata metadata = binding.resolve(method);

        // Handle payable functions - extract Wei value from first parameter
        final boolean isPayable = method.isAnnotationPresent(Payable.class);
        final Wei value;
        final Object[] contractArgs;

        if (isPayable && invocationArgs.length > 0 && invocationArgs[0] instanceof Wei) {
            value = (Wei) invocationArgs[0];
            contractArgs = Arrays.copyOfRange(invocationArgs, 1, invocationArgs.length);
        } else {
            value = Wei.of(0);
            contractArgs = invocationArgs;
        }

        final Abi.FunctionCall functionCall = abi.encodeFunction(metadata.name(), contractArgs);

        if (metadata.isView()) {
            return invokeView(method, functionCall);
        }

        return invokeWrite(method, functionCall, value);
    }

    private Object invokeView(final Method method, final Abi.FunctionCall call) {
        final Map<String, Object> callObject = new LinkedHashMap<>();
        callObject.put("to", address.value());
        callObject.put("data", call.data());

        try {
            final String output = publicClient.call(callObject, "latest");
            if (output == null || output.isBlank()) {
                throw new AbiDecodingException(
                        "eth_call returned empty result for function call");
            }
            if (method.getReturnType() == void.class || method.getReturnType() == Void.class) {
                return null;
            }
            return call.decode(output, method.getReturnType());
        } catch (RpcException e) {
            RevertDecoder.throwIfRevert(e);
            throw e;
        }
    }

    private Object invokeWrite(final Method method, final Abi.FunctionCall call, final Wei value) {
        final TransactionRequest request =
                TxBuilder.eip1559()
                        .to(address)
                        .data(new HexData(call.data()))
                        .value(value)
                        .build();

        final TransactionReceipt receipt =
                walletClient.sendTransactionAndWait(
                        request, options.timeoutMillis(), options.pollIntervalMillis());
        if (method.getReturnType() == void.class || method.getReturnType() == Void.class) {
            return null;
        }
        return receipt;
    }

    private static boolean isObjectMethod(final Method method) {
        return method.getDeclaringClass() == Object.class;
    }

    private Object handleObjectMethod(
            final Object proxy, final Method method, final Object[] args) {
        return switch (method.getName()) {
        case "toString" ->
                    "BraneContractProxy{" + "address=" + address.value() + "}";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
            default -> throw new UnsupportedOperationException(
                    "Object method not supported: " + method.getName());
        };
    }
}
