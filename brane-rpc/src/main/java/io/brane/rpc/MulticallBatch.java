package io.brane.rpc;

import io.brane.core.DebugLogger;
import io.brane.core.abi.Abi;
import io.brane.core.abi.AbiBinding;
import io.brane.core.model.Call3;
import io.brane.core.model.MulticallResult;
import io.brane.core.RevertDecoder;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates a batch of Multicall3 requests.
 * 
 * <p>
 * This class captures multiple contract calls and bundles them into a single
 * {@code eth_call} using the Multicall3 contract.
 */
public final class MulticallBatch {

    private static final String MULTICALL_ABI = "[{\"inputs\":[{\"components\":[{\"internalType\":\"address\",\"name\":\"target\",\"type\":\"address\"},{\"internalType\":\"bool\",\"name\":\"allowFailure\",\"type\":\"bool\"},{\"internalType\":\"bytes\",\"name\":\"callData\",\"type\":\"bytes\"}],\"internalType\":\"struct Multicall3.Call3[]\",\"name\":\"calls\",\"type\":\"tuple[]\"}],\"name\":\"aggregate3\",\"outputs\":[{\"components\":[{\"internalType\":\"bool\",\"name\":\"success\",\"type\":\"bool\"},{\"internalType\":\"bytes\",\"name\":\"returnData\",\"type\":\"bytes\"}],\"internalType\":\"struct Multicall3.Result[]\",\"name\":\"returnData\",\"type\":\"tuple[]\"}],\"stateMutability\":\"payable\",\"type\":\"function\"}]";

    /**
     * Default Multicall3 address.
     *
     * <p>
     * TODO: Replace with MulticallRegistry to support chain-specific overrides.
     * Currently hardcoded to the deterministic deployment address (0xcA11...),
     * which works on 100+ public EVM chains but may fail on custom/private networks.
     *
     * @see <a href="https://github.com/mds1/multicall">Multicall3 Deployments</a>
     */
    private static final Address MULTICALL_ADDRESS = new Address("0xca11bde05977b3631167028862be2a173976ca11");
    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int MAX_CHUNK_SIZE = 1000;

    private final PublicClient publicClient;
    private final List<CallContext<?>> calls = new ArrayList<>();
    private CallContext<?> pendingCall;
    private boolean globalAllowFailure = true;
    private int chunkSize = DEFAULT_CHUNK_SIZE;

    private MulticallBatch(final PublicClient publicClient) {
        this.publicClient = Objects.requireNonNull(publicClient, "publicClient");
    }

    /**
     * Creates a new multicall batch.
     * 
     * @param publicClient the public client to use for execution
     * @return a new batch instance
     */
    public static MulticallBatch create(final PublicClient publicClient) {
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
    @SuppressWarnings("unchecked")
    public <T> T bind(final Class<T> contractInterface, final Address address, final String abiJson) {
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
     */
    <T> void recordCall(final Address target, final Abi.FunctionCall call, final Class<T> returnType) {
        this.pendingCall = new CallContext<>(target, call, returnType);
    }

    /**
     * Adds a recorded call to the batch and returns a handle for the result.
     * 
     * @param <T>    the return type of the call
     * @param ignore the result of the proxy call (ignored)
     * @return a handle to the eventual result
     * @throws IllegalStateException if no call was recorded prior to calling add
     */
    @SuppressWarnings("unchecked")
    public <T> BatchHandle<T> add(T ignore) {
        if (pendingCall == null) {
            throw new IllegalStateException("No call was recorded. Call a method on a recording proxy first.");
        }
        final CallContext<T> call = (CallContext<T>) pendingCall;
        pendingCall = null;
        calls.add(call);
        final BatchHandle<T> handle = new BatchHandle<>();
        call.setHandle(handle);
        return handle;
    }

    /**
     * Executes the batch of calls in one or more RPC requests (depending on chunk
     * size).
     */
    public void execute() {
        if (calls.isEmpty()) {
            DebugLogger.log("MulticallBatch.execute() called with no calls — skipping RPC request");
            return;
        }

        for (int i = 0; i < calls.size(); i += chunkSize) {
            final int end = Math.min(i + chunkSize, calls.size());
            final List<CallContext<?>> chunk = calls.subList(i, end);
            executeChunk(chunk);
        }
    }

    private void executeChunk(final List<CallContext<?>> chunk) {
        // 1. Prepare Call3 objects as a list of tuples
        final List<Object> call3List = new ArrayList<>(chunk.size());
        for (final CallContext<?> call : chunk) {
            final Call3 call3 = call.toCall3(globalAllowFailure);
            // Each Call3 is a tuple (address, bool, bytes)
            call3List.add(List.of(
                    call3.target().value(),
                    call3.allowFailure(),
                    call3.callData()));
        }

        // 2. Encode aggregate3 call
        final Abi abi = Abi.fromJson(MULTICALL_ABI);
        final Abi.FunctionCall aggregate3Call = abi.encodeFunction("aggregate3", call3List);

        // 3. Send eth_call
        final java.util.Map<String, Object> callObject = new java.util.HashMap<>();
        callObject.put("to", MULTICALL_ADDRESS.value());
        callObject.put("data", aggregate3Call.data());

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
    static final class CallContext<T> {
        private final Address target;
        private final Abi.FunctionCall call;
        private final Class<T> returnType;
        private BatchHandle<T> handle;

        CallContext(final Address target, final Abi.FunctionCall call, final Class<T> returnType) {
            this.target = target;
            this.call = call;
            this.returnType = returnType;
        }

        void setHandle(BatchHandle<T> handle) {
            this.handle = handle;
        }

        Call3 toCall3(boolean allowFailure) {
            return new Call3(target, allowFailure, new HexData(call.data()));
        }

        void complete(MulticallResult result) {
            if (handle == null)
                return;

            T data = null;
            String revertReason = null;

            if (result.success()) {
                data = call.decode(result.returnData().value(), returnType);
            } else {
                final RevertDecoder.Decoded decoded = RevertDecoder.decode(result.returnData().value());
                revertReason = decoded.kind() != RevertDecoder.RevertKind.UNKNOWN
                        ? decoded.reason()
                        : "Reverted (raw: " + result.returnData().value() + ")";
            }

            handle.complete(new BatchResult<>(data, result.success(), revertReason));
        }
    }
}

