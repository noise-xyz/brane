// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.contract;

import java.lang.reflect.Method;
import java.util.Objects;

import io.brane.core.abi.Abi;
import io.brane.core.abi.AbiBinding;
import io.brane.core.types.Address;
import io.brane.core.util.MethodUtils;
import io.brane.rpc.Brane;

/**
 * Invocation handler for read-only contract proxies using the Brane API.
 *
 * <p>This handler only supports view/pure functions. Any attempt to invoke
 * state-changing functions will throw {@link UnsupportedOperationException}.
 */
final class ReadOnlyContractInvocationHandler extends AbstractContractInvocationHandler<Brane> {

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

        final Object[] invocationArgs = Objects.requireNonNullElse(args, new Object[0]);
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
