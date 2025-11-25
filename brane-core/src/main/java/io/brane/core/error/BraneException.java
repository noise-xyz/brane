package io.brane.core.error;

/**
 * Base runtime exception for all Brane SDK failures.
 * 
 * <p>
 * This sealed class forms the root of Brane's exception hierarchy, ensuring
 * all Brane-specific errors can be caught with a single catch clause while
 * maintaining type safety through sealed types.
 * 
 * <p>
 * <strong>Exception Hierarchy:</strong>
 * <ul>
 * <li>{@link RpcException} - JSON-RPC communication failures</li>
 * <li>{@link RevertException} - EVM execution reverts</li>
 * <li>{@link AbiEncodingException} - ABI encoding failures</li>
 * <li>{@link AbiDecodingException} - ABI decoding failures</li>
 * <li>{@link TxnException} - Transaction-specific failures</li>
 * </ul>
 * 
 * <p>
 * <strong>Usage:</strong>
 * 
 * <pre>{@code
 * try {
 *     client.sendTransaction(request);
 * } catch (RevertException e) {
 *     // Handle contract revert
 * } catch (RpcException e) {
 *     // Handle network/RPC error
 * } catch (BraneException e) {
 *     // Catch-all for any other Brane error
 * }
 * }</pre>
 */
public sealed class BraneException extends RuntimeException
        permits AbiDecodingException,
        AbiEncodingException,
        RevertException,
        RpcException,
        TxnException {

    public BraneException(final String message) {
        super(message);
    }

    public BraneException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
