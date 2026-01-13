// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.contract;

import java.lang.reflect.Method;

import io.brane.core.RevertDecoder;
import io.brane.core.abi.Abi;
import io.brane.core.abi.AbiBinding;
import io.brane.core.error.AbiDecodingException;
import io.brane.core.error.RpcException;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.util.MethodUtils;
import io.brane.rpc.BlockTag;
import io.brane.rpc.Brane;
import io.brane.rpc.CallRequest;

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
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        if (MethodUtils.isObjectMethod(method)) {
            return handleObjectMethod(proxy, method, args, true);
        }

        final Object[] invocationArgs = args == null ? new Object[0] : args;
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

    private Object invokeView(final Method method, final Abi.FunctionCall call) {
        final CallRequest request = CallRequest.builder()
                .to(address)
                .data(new HexData(call.data()))
                .build();

        try {
            final HexData output = client.call(request, BlockTag.LATEST);
            final String outputValue = output != null ? output.value() : null;
            if (outputValue == null || outputValue.isBlank() || "0x".equals(outputValue)) {
                throw new AbiDecodingException(
                        "eth_call returned empty result for function call");
            }
            if (method.getReturnType() == void.class || method.getReturnType() == Void.class) {
                return null;
            }
            return call.decode(outputValue, method.getReturnType());
        } catch (RpcException e) {
            RevertDecoder.throwIfRevert(e);
            throw e;
        }
    }

}
