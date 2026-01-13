// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.contract;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

import sh.brane.core.abi.Abi;
import sh.brane.core.abi.AbiBinding;
import sh.brane.core.builder.TxBuilder;
import sh.brane.core.model.TransactionReceipt;
import sh.brane.core.model.TransactionRequest;
import sh.brane.core.types.Address;
import sh.brane.core.types.HexData;
import sh.brane.core.types.Wei;
import sh.brane.core.util.MethodUtils;
import sh.brane.rpc.Brane;

/**
 * Invocation handler for read-write contract proxies using the Brane.Signer API.
 *
 * <p>This handler supports both view/pure functions (via eth_call) and state-changing
 * functions (via sendTransactionAndWait).
 */
final class SignerContractInvocationHandler extends AbstractContractInvocationHandler<Brane.Signer> {

    private static final Object[] EMPTY_ARGS = new Object[0];

    private final ContractOptions options;

    SignerContractInvocationHandler(
            final Address address,
            final Abi abi,
            final AbiBinding binding,
            final Brane.Signer signer,
            final ContractOptions options) {
        super(address, abi, binding, signer);
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    protected String toStringSuffix() {
        return "";
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        if (MethodUtils.isObjectMethod(method)) {
            return handleObjectMethod(proxy, method, args);
        }

        final Object[] invocationArgs = Objects.requireNonNullElse(args, EMPTY_ARGS);
        final Abi.FunctionMetadata metadata = binding.resolve(method);

        // Handle payable functions - extract Wei value from first parameter.
        // Bind-time validation in BraneContract.validateParameters() ensures that @Payable
        // methods have Wei as their first parameter. The instanceof check here is defensive -
        // for properly bound contracts, it will always be true. Non-payable functions or
        // payable functions invoked with null Wei will use Wei.of(0).
        final boolean isPayable = method.isAnnotationPresent(Payable.class);
        final Wei value;
        final Object[] contractArgs;

        if (isPayable && invocationArgs.length > 0 && invocationArgs[0] instanceof Wei weiValue) {
            value = weiValue;
            contractArgs = Arrays.copyOfRange(invocationArgs, 1, invocationArgs.length);
        } else {
            value = Wei.ZERO;
            contractArgs = invocationArgs;
        }

        final Abi.FunctionCall functionCall = abi.encodeFunction(metadata.name(), contractArgs);

        if (metadata.isView()) {
            return invokeView(method, functionCall);
        }

        return invokeWrite(method, functionCall, value);
    }

    private Object invokeWrite(final Method method, final Abi.FunctionCall call, final Wei value) {
        final TransactionRequest request = buildTransactionRequest(call, value);

        final TransactionReceipt receipt =
                client.sendTransactionAndWait(
                        request, options.timeoutMillis(), options.pollIntervalMillis());
        if (method.getReturnType() == void.class || method.getReturnType() == Void.class) {
            return null;
        }
        return receipt;
    }

    private TransactionRequest buildTransactionRequest(final Abi.FunctionCall call, final Wei value) {
        if (options.transactionType() == ContractOptions.TransactionType.LEGACY) {
            return TxBuilder.legacy()
                    .to(address)
                    .data(new HexData(call.data()))
                    .value(value)
                    .gasLimit(options.gasLimit())
                    .build();
        }
        return TxBuilder.eip1559()
                .to(address)
                .data(new HexData(call.data()))
                .value(value)
                .gasLimit(options.gasLimit())
                .maxPriorityFeePerGas(options.maxPriorityFee())
                .build();
    }

}
