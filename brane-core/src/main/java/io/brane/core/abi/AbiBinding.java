package io.brane.core.abi;

import io.brane.core.util.MethodUtils;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Binds Java interface methods to ABI function metadata for type-safe contract interactions.
 *
 * <p>This class creates a mapping between methods on a contract interface and their
 * corresponding ABI function metadata, enabling type-safe encoding and decoding of
 * contract calls without repeated ABI lookups.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Define a contract interface
 * interface ERC20 {
 *     BigInteger balanceOf(Address owner);
 *     void transfer(Address to, BigInteger amount);
 * }
 *
 * // Create binding from ABI JSON
 * Abi abi = Abi.fromJson(abiJson);
 * AbiBinding binding = new AbiBinding(abi, ERC20.class);
 *
 * // Resolve metadata for a method
 * Method method = ERC20.class.getMethod("balanceOf", Address.class);
 * Abi.FunctionMetadata metadata = binding.resolve(method);
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. The method-to-metadata cache is created as an immutable
 * snapshot during construction and can be safely shared across threads.
 *
 * @see Abi
 * @see Abi.FunctionMetadata
 */
public final class AbiBinding {

    private final Abi abi;
    private final Map<Method, Abi.FunctionMetadata> cache;

    public AbiBinding(final Abi abi, final Class<?> contractInterface) {
        this.abi = Objects.requireNonNull(abi, "abi");
        Objects.requireNonNull(contractInterface, "contractInterface");

        final Map<Method, Abi.FunctionMetadata> mutableCache = new HashMap<>();
        for (Method method : contractInterface.getMethods()) {
            if (MethodUtils.isObjectMethod(method)) {
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
}

