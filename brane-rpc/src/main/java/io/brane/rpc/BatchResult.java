package io.brane.rpc;

/**
 * The result of an individual call within a Multicall3 batch.
 * 
 * <p>
 * Each result contains three pieces of information:
 * <ul>
 * <li>{@link #data()} - The decoded return value (null if call failed)</li>
 * <li>{@link #success()} - Whether the call succeeded</li>
 * <li>{@link #revertReason()} - Human-readable error message (null if
 * succeeded)</li>
 * </ul>
 * 
 * <p>
 * <strong>Example:</strong>
 * 
 * <pre>{@code
 * BatchResult<BigInteger> result = handle.result();
 * if (result.success()) {
 *         System.out.println("Balance: " + result.data());
 * } else {
 *         System.out.println("Failed: " + result.revertReason());
 * }
 * }</pre>
 * 
 * @param <T>          the type of the result data
 * @param data         the decoded result data (null if call failed or returns
 *                     void)
 * @param success      true if the call was successful
 * @param revertReason human-readable reason for the failure (null if
 *                     successful)
 * @since 0.1.0
 */
public record BatchResult<T>(
                T data,
                boolean success,
                String revertReason) {
}
