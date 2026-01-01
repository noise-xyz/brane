package io.brane.rpc;

import io.brane.core.DebugLogger;
import io.brane.core.abi.Abi;
import io.brane.core.abi.AbiBinding;
import io.brane.core.model.Call3;
import io.brane.core.model.MulticallResult;
import io.brane.core.RevertDecoder;
import io.brane.core.error.AbiDecodingException;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Orchestrates a batch of Multicall3 requests.
 *
 * <p>
 * This class captures multiple contract calls and bundles them into a single
 * {@code eth_call} using the Multicall3 contract.
 *
 * <p><b>Exception Safety:</b> If an exception occurs between a proxy method call
 * and the subsequent {@code add()} call, you MUST call {@link #clearPending()} in a
 * finally block to prevent ThreadLocal leaks. Example:
 * <pre>{@code
 * var handle = batch.add(myContract.myMethod(arg));  // Safe - add() clears pending
 *
 * // If you catch exceptions, always clear pending:
 * try {
 *     var result = myContract.myMethod(arg);
 *     handle = batch.add(result);
 * } catch (Exception e) {
 *     batch.clearPending();  // Prevent ThreadLocal leak
 *     throw e;
 * }
 * }</pre>
 *
 * @implNote This class is <strong>not thread-safe</strong>. A batch instance
 *           must be used from a single thread only. The recording pattern uses
 *           ThreadLocal to temporarily store call metadata between the proxy
 *           call and the subsequent {@code add()} call.
 */
public final class MulticallBatch {

    private static final String MULTICALL_ABI_JSON = "[{\"inputs\":[{\"components\":[{\"internalType\":\"address\",\"name\":\"target\",\"type\":\"address\"},{\"internalType\":\"bool\",\"name\":\"allowFailure\",\"type\":\"bool\"},{\"internalType\":\"bytes\",\"name\":\"callData\",\"type\":\"bytes\"}],\"internalType\":\"struct Multicall3.Call3[]\",\"name\":\"calls\",\"type\":\"tuple[]\"}],\"name\":\"aggregate3\",\"outputs\":[{\"components\":[{\"internalType\":\"bool\",\"name\":\"success\",\"type\":\"bool\"},{\"internalType\":\"bytes\",\"name\":\"returnData\",\"type\":\"bytes\"}],\"internalType\":\"struct Multicall3.Result[]\",\"name\":\"returnData\",\"type\":\"tuple[]\"}],\"stateMutability\":\"payable\",\"type\":\"function\"}]";

    /** Cached parsed ABI to avoid JSON parsing on each chunk execution. */
    private static final Abi MULTICALL_ABI = Abi.fromJson(MULTICALL_ABI_JSON);

    /**
     * Default Multicall3 address.
     *
     * <p>
     * TODO: Replace with MulticallRegistry to support chain-specific overrides.
     * Currently hardcoded to the deterministic deployment address (0xcA11...),
     * which works on 100+ public EVM chains but may fail on custom/private
     * networks.
     *
     * @see <a href="https://github.com/mds1/multicall">Multicall3 Deployments</a>
     */
    private static final Address MULTICALL_ADDRESS = new Address("0xca11bde05977b3631167028862be2a173976ca11");

    /**
     * Default chunk size for batching. 500 balances RPC payload size with
     * efficiency.
     * Most RPC providers support payloads up to 1MB; 500 calls typically stays well
     * under.
     */
    private static final int DEFAULT_CHUNK_SIZE = 500;

    /**
     * Maximum allowed chunk size. 1000 calls approaches RPC provider limits for
     * most endpoints and risks timeouts or payload size errors.
     */
    private static final int MAX_CHUNK_SIZE = 1000;

    private final PublicClient publicClient;
    private final List<CallContext<?>> calls = new ArrayList<>();
    private final ThreadLocal<CallContext<?>> pendingCall = new ThreadLocal<>();
    private boolean globalAllowFailure = true;
    private int chunkSize = DEFAULT_CHUNK_SIZE;
    private boolean executed = false;

    private MulticallBatch(final PublicClient publicClient) {
        this.publicClient = Objects.requireNonNull(publicClient, "publicClient");
    }

    /**
     * Creates a new multicall batch.
     * 
     * @param publicClient the public client to use for execution
     * @return a new batch instance
     */
    static MulticallBatch create(final PublicClient publicClient) {
        return new MulticallBatch(publicClient);
    }

    /**
     * Sets whether individual calls are allowed to fail without reverting the
     * entire batch.
     * 
     * @param allowFailure true to allow failure, false to revert batch on any
     *                     failure
     * @return this batch instance
     */
    public MulticallBatch allowFailure(boolean allowFailure) {
        this.globalAllowFailure = allowFailure;
        return this;
    }

    /**
     * Sets the maximum number of calls per RPC request.
     * 
     * @param chunkSize the maximum number of calls per chunk
     * @return this batch instance
     */
    public MulticallBatch chunkSize(int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be greater than 0");
        }
        if (chunkSize > MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException("chunkSize cannot exceed " + MAX_CHUNK_SIZE);
        }
        this.chunkSize = chunkSize;
        return this;
    }

    /**
     * Binds a contract interface to a recording proxy.
     * 
     * @param <T>               the contract interface type
     * @param contractInterface the interface to bind
     * @param address           the contract address
     * @param abiJson           the contract ABI in JSON format
     * @return a recording proxy instance
     */
    @SuppressWarnings("unchecked") // Safe: Proxy creates correct type for contractInterface
    public <T> T bind(final Class<T> contractInterface, final Address address, final String abiJson) {
        Objects.requireNonNull(contractInterface, "contractInterface");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(abiJson, "abiJson");

        final Abi abi = Abi.fromJson(abiJson);
        final AbiBinding binding = new AbiBinding(abi, contractInterface);
        final MulticallInvocationHandler handler = new MulticallInvocationHandler(address, abi, binding, this);

        return (T) Proxy.newProxyInstance(
                contractInterface.getClassLoader(),
                new Class<?>[] { contractInterface },
                handler);
    }

    /**
     * Internal method used by recording proxies to record a call.
     * 
     * <p>
     * <strong>Important:</strong> Every call to this method must be followed by
     * a call to {@link #add(Object)} to consume the pending call and prevent
     * ThreadLocal leaks. The recording proxy pattern ensures this by design.
     */
    <T> void recordCall(final Address target, final Abi.FunctionCall call, final Class<T> returnType) {
        // Fail fast if user forgot to call add() after a previous proxy call
        // Clear the orphaned call BEFORE throwing to prevent ThreadLocal leaks
        if (pendingCall.get() != null) {
            pendingCall.remove(); // Clear BEFORE throwing to prevent leak
            throw new IllegalStateException(
                    "A call was already recorded but not added to the batch. " +
                            "You must call batch.add() after each proxy method call.");
        }
        pendingCall.set(new CallContext<>(target, call, returnType, null));
    }

    /**
     * Adds a recorded call to the batch and returns a handle for the result.
     * 
     * @param <T>    the return type of the call
     * @param ignore the result of the proxy call (ignored)
     * @return a handle to the eventual result
     * @throws IllegalStateException if no call was recorded prior to calling add
     */
    @SuppressWarnings("unchecked") // Safe: pendingCall always set with matching type from recordCall()
    public <T> BatchHandle<T> add(T ignore) {
        final CallContext<T> pending = (CallContext<T>) pendingCall.get();
        if (pending == null) {
            throw new IllegalStateException("No call was recorded. Call a method on a recording proxy first.");
        }
        pendingCall.remove();

        final BatchHandle<T> handle = new BatchHandle<>();
        final CallContext<T> call = new CallContext<>(
                pending.target(),
                pending.call(),
                pending.returnType(),
                handle);
        synchronized (calls) {
            calls.add(call);
        }
        return handle;
    }

    /**
     * Clears any pending call that was recorded but not added to the batch.
     *
     * <p>
     * Call this method in a finally block if you catch exceptions between
     * a proxy method call and {@link #add(Object)} to prevent ThreadLocal leaks.
     *
     * <p>
     * This method is safe to call even if there is no pending call.
     *
     * @return true if a pending call was cleared, false if there was nothing to clear
     */
    public boolean clearPending() {
        final CallContext<?> pending = pendingCall.get();
        if (pending != null) {
            pendingCall.remove();
            return true;
        }
        return false;
    }

    /**
     * Returns true if there is a pending call that was recorded but not yet added.
     *
     * <p>
     * This can be useful for debugging or asserting state in tests.
     *
     * @return true if a call is pending, false otherwise
     */
    public boolean hasPending() {
        return pendingCall.get() != null;
    }

    /**
     * Executes the batch of calls in one or more RPC requests (depending on chunk
     * size).
     *
     * @throws IllegalStateException if this batch has already been executed
     */
    public void execute() {
        // Take a snapshot of calls under lock to ensure consistency
        final List<CallContext<?>> callsSnapshot;
        synchronized (calls) {
            if (executed) {
                throw new IllegalStateException(
                        "Batch has already been executed. Create a new batch for additional calls.");
            }
            if (calls.isEmpty()) {
                DebugLogger.log("MulticallBatch.execute() called with no calls — skipping RPC request");
                return;
            }
            executed = true;
            // Take immutable snapshot to avoid concurrent modification issues
            callsSnapshot = List.copyOf(calls);
        }

        // Execute chunks outside the synchronized block for better concurrency
        for (int i = 0; i < callsSnapshot.size(); i += chunkSize) {
            final int end = Math.min(i + chunkSize, callsSnapshot.size());
            final List<CallContext<?>> chunk = callsSnapshot.subList(i, end);
            executeChunk(chunk);
        }
    }

    private void executeChunk(final List<CallContext<?>> chunk) {
        // 1. Prepare Call3 objects as a list of tuples
        final List<Object> call3List = new ArrayList<>(chunk.size());
        for (final CallContext<?> call : chunk) {
            final Call3 call3 = call.toCall3(globalAllowFailure);
            // Each Call3 is a tuple (address string, bool, HexData callData)
            // Note: address is extracted as string, but callData stays as HexData for ABI
            // encoding
            call3List.add(List.of(
                    call3.target().value(),
                    call3.allowFailure(),
                    call3.callData()));
        }

        // 2. Encode aggregate3 call
        final Abi.FunctionCall aggregate3Call = MULTICALL_ABI.encodeFunction("aggregate3", call3List);

        // 3. Send eth_call
        final Map<String, Object> callObject = Map.of(
                "to", MULTICALL_ADDRESS.value(),
                "data", aggregate3Call.data());

        final String resultHex = publicClient.call(callObject, "latest");

        // 4. Decode results and update handles
        final List<MulticallResult> results = Abi.decodeMulticallResults(resultHex);

        if (results.size() != chunk.size()) {
            throw new IllegalStateException("Multicall3 returned " + results.size()
                    + " results, but we sent " + chunk.size() + " calls");
        }

        for (int i = 0; i < chunk.size(); i++) {
            chunk.get(i).complete(results.get(i));
        }
    }

    /**
     * Context for an individual call in the batch.
     */
    record CallContext<T>(
            Address target,
            Abi.FunctionCall call,
            Class<T> returnType,
            BatchHandle<T> handle) {

        Call3 toCall3(boolean allowFailure) {
            return new Call3(target(), allowFailure, new HexData(call().data()));
        }

        void complete(MulticallResult result) {
            if (handle() == null)
                return;

            T data = null;
            String revertReason = null;
            boolean success = result.success();

            // Handle edge case: success=true but empty returnData (call to address with no
            // code)
            final HexData rd = result.returnData();
            final String returnData = (rd != null) ? rd.value() : null;
            final boolean isEmpty = returnData == null || returnData.equals("0x") || returnData.isEmpty();

            if (success && isEmpty) {
                // Multicall3 returns success=true with empty data for calls to non-existent
                // contracts
                success = false;
                revertReason = "Call returned empty data (target may not be a contract)";
            } else if (success) {
                try {
                    data = call().decode(returnData, returnType());
                } catch (ClassCastException e) {
                    throw new AbiDecodingException("Return type mismatch for function: expected " + returnType(), e);
                }
            } else {
                final RevertDecoder.Decoded decoded = RevertDecoder.decode(returnData);
                revertReason = decoded.kind() != RevertDecoder.RevertKind.UNKNOWN
                        ? decoded.reason()
                        : "Reverted (raw: " + returnData + ")";
            }

            handle().complete(new BatchResult<>(data, success, revertReason));
        }

    }
}
