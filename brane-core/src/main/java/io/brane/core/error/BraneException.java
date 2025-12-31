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
 * <pre>
 * BraneException
 * ├── {@link AbiDecodingException} - ABI decoding failures
 * ├── {@link AbiEncodingException} - ABI encoding failures
 * ├── {@link RevertException} - EVM execution reverts
 * ├── {@link RpcException} - JSON-RPC communication failures
 * └── {@link TxnException} - Transaction-specific failures
 *     ├── {@link io.brane.core.builder.BraneTxBuilderException BraneTxBuilderException} - Transaction building failures
 *     ├── {@link ChainMismatchException} - Chain ID mismatch errors
 *     └── {@link InvalidSenderException} - Invalid sender address errors
 * </pre>
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
 *
 * @since 0.1.0-alpha
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
