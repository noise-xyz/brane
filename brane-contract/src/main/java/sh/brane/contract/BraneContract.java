// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.contract;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import sh.brane.core.abi.Abi;
import sh.brane.core.abi.AbiBinding;
import sh.brane.core.model.TransactionReceipt;
import sh.brane.core.types.Address;
import sh.brane.core.types.HexData;
import sh.brane.rpc.Brane;

/**
 * Dynamic proxy-based contract binding system that maps Java interfaces to
 * smart contracts.
 *
 * <p>
 * This class enables type-safe smart contract interaction by binding a Java
 * interface to a contract's ABI. At runtime, it creates a dynamic proxy that:
 * <ul>
 * <li>Encodes method calls to contract function calls (ABI encoding)</li>
 * <li>Routes view/pure functions to {@code eth_call} (read-only)</li>
 * <li>Routes state-changing functions to {@code sendTransactionAndWait} (write)</li>
 * <li>Decodes return values from hex to Java types (ABI decoding)</li>
 * </ul>
 *
 * <p>
 * <strong>Supported Type Mappings:</strong>
 * <ul>
 * <li>{@code uint8, uint256, int256} → {@link BigInteger}</li>
 * <li>{@code address} → {@link Address}</li>
 * <li>{@code bool} → {@code boolean}/{@link Boolean}</li>
 * <li>{@code string} → {@link String}</li>
 * <li>{@code bytes, bytes32} → {@code byte[]}/{@link HexData}</li>
 * <li>{@code T[]} → {@code T[]}/{@link List}&lt;T&gt;</li>
 * </ul>
 *
 * <p>
 * <strong>Limitations:</strong>
 * <ul>
 * <li><strong>Tuple/Struct return types:</strong> Functions returning Solidity structs
 * or tuples are not currently supported. The proxy binding will fail at bind time
 * with "Unsupported return type" for methods that return Java record types or custom
 * classes.</li>
 * </ul>
 *
 * <p>
 * <strong>Method Return Types:</strong>
 * <ul>
 * <li><strong>View/Pure functions:</strong> Contract output type (e.g.,
 * {@code BigInteger})</li>
 * <li><strong>State-changing functions:</strong> {@link TransactionReceipt},
 * {@code void}, or {@code Void}</li>
 * </ul>
 *
 * <p>
 * <strong>Complete Usage Example:</strong>
 *
 * <pre>{@code
 * // 1. Define Java interface matching contract ABI
 * public interface Erc20Contract {
 *     // View function: balanceOf(address) returns (uint256)
 *     BigInteger balanceOf(Address owner);
 *
 *     // State-changing: transfer(address,uint256) returns (bool)
 *     TransactionReceipt transfer(Address to, BigInteger amount);
 *
 *     // Can also return void for state-changing functions
 *     void approve(Address spender, BigInteger amount);
 * }
 *
 * // 2. Load contract ABI JSON
 * String abiJson = Files.readString(Path.of("erc20-abi.json"));
 *
 * // 3. Connect to network with signing capability
 * Brane.Signer client = Brane.connect("https://eth-mainnet.g.alchemy.com/v2/...", signer);
 *
 * // 4. Bind interface to deployed contract
 * Erc20Contract usdc = BraneContract.bind(
 *         Address.from("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"), // USDC address
 *         abiJson,
 *         client,
 *         Erc20Contract.class);
 *
 * // 5. Call view functions (no gas, instant)
 * BigInteger balance = usdc.balanceOf(myAddress);
 * System.out.println("Balance: " + balance);
 *
 * // 6. Call state-changing functions (costs gas, returns receipt)
 * TransactionReceipt receipt = usdc.transfer(
 *         recipientAddress,
 *         new BigInteger("1000000") // 1 USDC (6 decimals)
 * );
 *
 * if (receipt.status()) {
 *     System.out.println("Transfer confirmed in block " + receipt.blockNumber());
 * }
 * }</pre>
 *
 * <p>
 * <strong>Validation:</strong> The {@link #bind} method validates that:
 * <ul>
 * <li>All interface methods exist in the ABI</li>
 * <li>Parameter counts and types match</li>
 * <li>Return types are compatible with function mutability (view vs non-view)</li>
 * </ul>
 *
 * <p>
 * <strong>Implementation:</strong> Uses Java's {@link Proxy} API with
 * invocation handlers to intercept method calls and translate them to RPC calls.
 *
 * <p>
 * <strong>Runtime Exceptions from Proxy Methods:</strong>
 * Methods on the bound contract proxy may throw these exceptions at runtime:
 * <ul>
 * <li><strong>View/Pure functions:</strong>
 *   <ul>
 *   <li>{@link sh.brane.core.error.RpcException} - RPC communication failures</li>
 *   <li>{@link sh.brane.core.error.RevertException} - Contract reverted (includes revert reason if available)</li>
 *   <li>{@link sh.brane.core.error.AbiDecodingException} - Failed to decode return value</li>
 *   </ul>
 * </li>
 * <li><strong>State-changing functions:</strong>
 *   <ul>
 *   <li>{@link sh.brane.core.error.RpcException} - RPC communication failures or transaction rejection</li>
 *   <li>{@link sh.brane.core.error.AbiEncodingException} - Failed to encode function parameters</li>
 *   </ul>
 * </li>
 * </ul>
 *
 * @see Abi
 * @see Brane
 * @see Brane.Signer
 */
public final class BraneContract {

    /** Pattern to match array types: type[] (dynamic) or type[N] (fixed-size). */
    private static final Pattern ARRAY_PATTERN = Pattern.compile(".*\\[\\d*]$");

    private BraneContract() {
    }

    /**
     * Binds a Java interface to a deployed smart contract for read-only operations.
     *
     * <p>
     * Creates a type-safe proxy instance that implements the specified interface.
     * This method only supports view/pure functions - attempting to call state-changing
     * functions will throw {@link UnsupportedOperationException}.
     *
     * <p>
     * <strong>Interface Requirements:</strong>
     * <ul>
     * <li>Must be an interface (not a class)</li>
     * <li>Method names must exactly match ABI function names</li>
     * <li>All methods must be view or pure functions</li>
     * <li>Parameter types must match Solidity types</li>
     * <li>Return types must be compatible with function mutability</li>
     * </ul>
     *
     * <p>
     * <strong>Example:</strong>
     * <pre>{@code
     * // Define read-only interface
     * public interface Erc20View {
     *     BigInteger balanceOf(Address owner);
     *     String name();
     *     String symbol();
     *     BigInteger decimals();
     * }
     *
     * // Bind using Brane
     * Brane client = Brane.connect("https://eth-mainnet.g.alchemy.com/v2/...");
     * Erc20View token = BraneContract.bindReadOnly(
     *         Address.from("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"),
     *         abiJson,
     *         client,
     *         Erc20View.class);
     *
     * BigInteger balance = token.balanceOf(myAddress);
     * }</pre>
     *
     * @param <T>               the contract interface type
     * @param address           the deployed contract address
     * @param abiJson           the contract ABI in JSON format
     * @param client            the Brane client for read operations
     * @param contractInterface the Java interface class representing the contract
     * @return a proxy instance implementing the contract interface (read-only)
     * @throws IllegalArgumentException if validation fails or interface contains non-view functions
     * @throws NullPointerException     if any parameter is null
     * @since 0.1.0
     */
    public static <T> T bindReadOnly(
            final Address address,
            final String abiJson,
            final Brane client,
            final Class<T> contractInterface) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(abiJson, "abiJson");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(contractInterface, "contractInterface");

        if (!contractInterface.isInterface()) {
            throw new IllegalArgumentException("contractInterface must be an interface");
        }

        final Abi abi = Abi.fromJson(abiJson);
        validateReadOnlyMethods(contractInterface, abi);

        final AbiBinding binding = new AbiBinding(abi, contractInterface);
        final ReadOnlyContractInvocationHandler handler = new ReadOnlyContractInvocationHandler(
                address, abi, binding, client);
        final Object proxy = Proxy.newProxyInstance(
                contractInterface.getClassLoader(), new Class<?>[] { contractInterface }, handler);
        return contractInterface.cast(proxy);
    }

    /**
     * Binds a Java interface to a deployed smart contract for read-write operations using the new Brane.Signer API.
     *
     * <p>
     * Creates a type-safe proxy instance that implements the specified interface.
     * Method calls on the proxy are translated to contract function calls:
     * <ul>
     * <li>View/pure functions → {@code eth_call} via {@link Brane.Signer}</li>
     * <li>State-changing functions → {@code sendTransactionAndWait} via
     * {@link Brane.Signer}</li>
     * </ul>
     *
     * <p>
     * <strong>Interface Requirements:</strong>
     * <ul>
     * <li>Must be an interface (not a class)</li>
     * <li>Method names must exactly match ABI function names</li>
     * <li>Parameter types must match Solidity types</li>
     * <li>Return types must be compatible with function mutability</li>
     * </ul>
     *
     * <p>
     * <strong>Example:</strong>
     * <pre>{@code
     * // Define contract interface
     * public interface Erc20Contract {
     *     BigInteger balanceOf(Address owner);
     *     TransactionReceipt transfer(Address to, BigInteger amount);
     * }
     *
     * // Bind using Brane.Signer
     * Brane.Signer client = Brane.connect("https://eth-mainnet.g.alchemy.com/v2/...", signer);
     * Erc20Contract token = BraneContract.bind(
     *         Address.from("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"),
     *         abiJson,
     *         client,
     *         Erc20Contract.class);
     *
     * BigInteger balance = token.balanceOf(myAddress);
     * TransactionReceipt receipt = token.transfer(recipient, amount);
     * }</pre>
     *
     * @param <T>               the contract interface type
     * @param address           the deployed contract address
     * @param abiJson           the contract ABI in JSON format
     * @param client            the Brane.Signer client for read and write operations
     * @param contractInterface the Java interface class representing the contract
     * @return a proxy instance implementing the contract interface
     * @throws IllegalArgumentException if validation fails (method not in ABI, type mismatch, etc.)
     * @throws NullPointerException     if any parameter is null
     * @since 0.1.0
     */
    public static <T> T bind(
            final Address address,
            final String abiJson,
            final Brane.Signer client,
            final Class<T> contractInterface) {
        return bind(address, abiJson, client, contractInterface, ContractOptions.defaults());
    }

    /**
     * Binds a Java interface to a deployed smart contract for read-write operations using the new Brane.Signer API
     * with custom options.
     *
     * <p>
     * Creates a type-safe proxy instance that implements the specified interface.
     * Method calls on the proxy are translated to contract function calls:
     * <ul>
     * <li>View/pure functions → {@code eth_call} via {@link Brane.Signer}</li>
     * <li>State-changing functions → {@code sendTransactionAndWait} via
     * {@link Brane.Signer}</li>
     * </ul>
     *
     * <p>
     * <strong>Example with custom options:</strong>
     * <pre>{@code
     * var options = ContractOptions.builder()
     *     .gasLimit(500_000L)
     *     .timeout(Duration.ofSeconds(30))
     *     .pollInterval(Duration.ofMillis(100))
     *     .build();
     *
     * Erc20Contract usdc = BraneContract.bind(
     *         address, abiJson, client, Erc20Contract.class, options);
     * }</pre>
     *
     * @param <T>               the contract interface type
     * @param address           the deployed contract address
     * @param abiJson           the contract ABI in JSON format
     * @param client            the Brane.Signer client for read and write operations
     * @param contractInterface the Java interface class representing the contract
     * @param options           the contract options for gas limit, timeouts, etc.
     * @return a proxy instance implementing the contract interface
     * @throws IllegalArgumentException if validation fails (method not in ABI, type mismatch, etc.)
     * @throws NullPointerException     if any parameter is null
     * @since 0.1.0
     */
    public static <T> T bind(
            final Address address,
            final String abiJson,
            final Brane.Signer client,
            final Class<T> contractInterface,
            final ContractOptions options) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(abiJson, "abiJson");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(contractInterface, "contractInterface");
        Objects.requireNonNull(options, "options");

        if (!contractInterface.isInterface()) {
            throw new IllegalArgumentException("contractInterface must be an interface");
        }

        final Abi abi = Abi.fromJson(abiJson);
        validateMethods(contractInterface, abi);

        final AbiBinding binding = new AbiBinding(abi, contractInterface);
        final SignerContractInvocationHandler handler = new SignerContractInvocationHandler(
                address, abi, binding, client, options);
        final Object proxy = Proxy.newProxyInstance(
                contractInterface.getClassLoader(), new Class<?>[] { contractInterface }, handler);
        return contractInterface.cast(proxy);
    }

    /**
     * Creates a deployment transaction request for a contract.
     *
     * @param abiJson  the contract ABI JSON
     * @param bytecode the contract bytecode (hex string)
     * @param args     constructor arguments
     * @return a TransactionRequest ready to be sent
     */
    public static sh.brane.core.model.TransactionRequest deployRequest(
            final String abiJson,
            final String bytecode,
            final Object... args) {
        Objects.requireNonNull(abiJson, "abiJson");
        Objects.requireNonNull(bytecode, "bytecode");

        final Abi abi = Abi.fromJson(abiJson);
        final HexData encodedArgs = abi.encodeConstructor(args);

        String deployData = bytecode.trim();
        if (!deployData.startsWith("0x")) {
            deployData = "0x" + deployData;
        }

        // Append encoded args (stripping 0x prefix from args)
        if (encodedArgs.value().length() > 2) {
            deployData += encodedArgs.value().substring(2);
        }

        return sh.brane.core.builder.TxBuilder.eip1559()
                .data(new HexData(deployData))
                .build();
    }

    private static void validateMethods(final Class<?> contractInterface, final Abi abi) {
        for (Method method : contractInterface.getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }

            final Abi.FunctionMetadata metadata = abi.getFunction(method.getName())
                    .orElseThrow(
                            () -> new IllegalArgumentException(
                                    "No ABI function named '" + method.getName() + "'"));
            validateParameters(method, metadata);
            validateReturnType(method, metadata);
        }
    }

    private static void validateReadOnlyMethods(final Class<?> contractInterface, final Abi abi) {
        for (Method method : contractInterface.getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }

            final Abi.FunctionMetadata metadata = abi.getFunction(method.getName())
                    .orElseThrow(
                            () -> new IllegalArgumentException(
                                    "No ABI function named '" + method.getName() + "'"));

            // Ensure all methods are view/pure for read-only binding
            if (!metadata.isView()) {
                throw new IllegalArgumentException(
                        "bindReadOnly only supports view/pure functions, but '"
                                + method.getName()
                                + "' has stateMutability '"
                                + metadata.stateMutability()
                                + "'");
            }

            validateParameters(method, metadata);
            validateReturnType(method, metadata);
        }
    }

    private static void validateParameters(final Method method, final Abi.FunctionMetadata metadata) {
        final Class<?>[] parameterTypes = method.getParameterTypes();
        final boolean isPayable = method.isAnnotationPresent(Payable.class);

        // For @Payable methods, first Wei parameter is the value, not a contract parameter
        final int expectedParams = metadata.inputs().size();
        final int actualParams = parameterTypes.length;
        final int offset;

        if (isPayable) {
            if (actualParams == 0 || parameterTypes[0] != sh.brane.core.types.Wei.class) {
                throw new IllegalArgumentException(
                        "@Payable method "
                                + method.getName()
                                + " must have Wei as first parameter");
            }
            offset = 1; // Skip Wei parameter when validating against ABI
        } else {
            offset = 0;
        }

        if (actualParams - offset != expectedParams) {
            throw new IllegalArgumentException(
                    "Method "
                            + method.getName()
                            + " expects "
                            + expectedParams
                            + " parameters but has "
                            + (actualParams - offset)
                            + (isPayable ? " (excluding Wei value)" : ""));
        }

        for (int i = 0; i < expectedParams; i++) {
            final String solidityType = metadata.inputs().get(i);
            final Class<?> parameterType = parameterTypes[i + offset];
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

        // Validate @Payable annotation
        if (method.isAnnotationPresent(Payable.class)) {
            if (metadata.isView()) {
                throw new IllegalArgumentException(
                        "@Payable cannot be used on view function " + method.getName());
            }
            if (!metadata.isPayable()) {
                throw new IllegalArgumentException(
                        "@Payable method "
                                + method.getName()
                                + " does not map to a payable ABI function (stateMutability is '"
                                + metadata.stateMutability()
                                + "')");
            }
        }

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

            if (returnType == String.class) {
                requireSingleOutput(method, outputs, BraneContract::isStringType);
                return;
            }

            if (returnType == byte[].class || returnType == HexData.class) {
                requireSingleOutput(method, outputs, BraneContract::isBytesType);
                return;
            }

            if (List.class.isAssignableFrom(returnType) || returnType.isArray()) {
                requireSingleOutput(method, outputs, BraneContract::isArrayType);
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
        // Handle both dynamic arrays (type[]) and fixed-size arrays (type[N])
        if (ARRAY_PATTERN.matcher(solidityType).matches()) {
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

    private static boolean isStringType(final String solidityType) {
        return "string".equals(solidityType.toLowerCase(Locale.ROOT));
    }

    private static boolean isBytesType(final String solidityType) {
        final String normalized = solidityType.toLowerCase(Locale.ROOT);
        // Match "bytes" (dynamic) or "bytesN" (fixed-size like bytes32)
        return normalized.equals("bytes") || normalized.matches("bytes\\d+");
    }

    private static boolean isArrayType(final String solidityType) {
        // Match dynamic arrays (type[]) or fixed-size arrays (type[N])
        return ARRAY_PATTERN.matcher(solidityType).matches();
    }
}
