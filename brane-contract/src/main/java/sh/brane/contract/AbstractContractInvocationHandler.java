// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.contract;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Objects;

import sh.brane.core.RevertDecoder;
import sh.brane.core.abi.Abi;
import sh.brane.core.abi.AbiBinding;
import sh.brane.core.error.AbiDecodingException;
import sh.brane.core.error.RpcException;
import sh.brane.core.types.Address;
import sh.brane.core.types.HexData;
import sh.brane.rpc.BlockTag;
import sh.brane.rpc.Brane;
import sh.brane.rpc.CallRequest;

/**
 * Base class for contract invocation handlers providing shared fields and constructor.
 *
 * <p>This abstract class contains the common infrastructure needed by all contract
 * proxy implementations: the contract address, ABI definition, method binding, and
 * RPC client.
 *
 * @param <C> the type of Brane client (Reader, Signer, or Tester)
 */
abstract class AbstractContractInvocationHandler<C extends Brane> implements InvocationHandler {

    protected final Address address;
    protected final Abi abi;
    protected final AbiBinding binding;
    protected final C client;

    /**
     * Creates a new contract invocation handler.
     *
     * @param address the contract address
     * @param abi the contract ABI
     * @param binding the method-to-ABI binding
     * @param client the RPC client
     */
    protected AbstractContractInvocationHandler(
            final Address address,
            final Abi abi,
            final AbiBinding binding,
            final C client) {
        this.address = Objects.requireNonNull(address, "address");
        this.abi = Objects.requireNonNull(abi, "abi");
        this.binding = Objects.requireNonNull(binding, "binding");
        this.client = Objects.requireNonNull(client, "client");
    }

    /**
     * Returns the suffix to append to toString() output.
     *
     * <p>Subclasses override this to indicate their specific characteristics,
     * such as read-only mode.
     *
     * @return the toString suffix, or empty string for no suffix
     */
    protected abstract String toStringSuffix();

    /**
     * Handles standard Object methods (toString, hashCode, equals).
     *
     * @param proxy the proxy instance
     * @param method the method being invoked
     * @param args the method arguments
     * @return the result of the Object method
     */
    protected Object handleObjectMethod(
            final Object proxy,
            final Method method,
            final Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "BraneContractProxy{address=" + address.value() + toStringSuffix() + "}";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
            default -> throw new UnsupportedOperationException(
                    "Object method not supported: " + method.getName());
        };
    }

    /**
     * Invokes a view/pure function via eth_call.
     *
     * @param method the Java method being invoked
     * @param call the encoded function call
     * @return the decoded result
     */
    protected Object invokeView(final Method method, final Abi.FunctionCall call) {
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
