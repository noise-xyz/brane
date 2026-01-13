// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.contract;

import java.lang.reflect.Method;
import java.util.Objects;

import sh.brane.core.abi.Abi;
import sh.brane.core.abi.AbiBinding;
import sh.brane.core.types.Address;
import sh.brane.core.util.MethodUtils;
import sh.brane.rpc.Brane;

/**
 * Invocation handler for read-only contract proxies using the Brane API.
 *
 * <p>This handler only supports view/pure functions. Any attempt to invoke
 * state-changing functions will throw {@link UnsupportedOperationException}.
 */
final class ReadOnlyContractInvocationHandler extends AbstractContractInvocationHandler<Brane> {

    private static final Object[] EMPTY_ARGS = new Object[0];

    ReadOnlyContractInvocationHandler(
            final Address address,
            final Abi abi,
            final AbiBinding binding,
            final Brane client) {
        super(address, abi, binding, client);
    }

    @Override
    protected String toStringSuffix() {
        return ", readOnly=true";
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        if (MethodUtils.isObjectMethod(method)) {
            return handleObjectMethod(proxy, method, args);
        }

        final Object[] invocationArgs = Objects.requireNonNullElse(args, EMPTY_ARGS);
        final Abi.FunctionMetadata metadata = binding.resolve(method);

        // Read-only handler - only view/pure functions are supported
        if (!metadata.isView()) {
            throw new UnsupportedOperationException(
                    "Cannot invoke non-view function '"
                            + method.getName()
                            + "' on read-only contract binding. Use BraneContract.bind() with a Brane.Signer for write operations.");
        }

        final Abi.FunctionCall functionCall = abi.encodeFunction(metadata.name(), invocationArgs);
        return invokeView(method, functionCall);
    }

}
