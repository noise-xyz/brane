package io.brane.core.crypto;

import org.bouncycastle.jcajce.provider.digest.Keccak;

import java.util.Objects;

/**
 * Keccak-256 hashing utility for Ethereum.
 *
 * <p>
 * Ethereum uses Keccak-256 (not SHA3-256) for hashing. This class provides
 * a simple interface to BouncyCastle's Keccak.Digest256 implementation.
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
 * byte[] hash = Keccak256.hash(data);
 * String hashHex = Hex.encode(hash);
 * }</pre>
 *
 * <h2>Thread Safety and Memory Management</h2>
 *
 * <p>
 * This class uses a {@link ThreadLocal} to cache digest instances for performance.
 * In long-running applications with thread pools (e.g., servlet containers, application
 * servers), the cached digest will remain in memory for the lifetime of each thread.
 *
 * <p>
 * <strong>For application server environments:</strong> Call {@link #cleanup()} when
 * undeploying your application or when a thread is about to be returned to the pool
 * after completing request processing. This is typically done in a servlet filter's
 * {@code finally} block or a {@code ServletContextListener.contextDestroyed()} handler.
 *
 * <pre>{@code
 * // In a servlet filter
 * try {
 *     chain.doFilter(request, response);
 * } finally {
 *     Keccak256.cleanup();
 * }
 * }</pre>
 *
 * @since 0.2.0
 */
public final class Keccak256 {

    private static final ThreadLocal<Keccak.Digest256> DIGEST = ThreadLocal.withInitial(Keccak.Digest256::new);

    private Keccak256() {
        // Utility class
    }

    /**
     * Computes the Keccak-256 hash of the input bytes.
     * 
     * @param input the data to hash
     * @return 32-byte hash
     * @throws NullPointerException if input is null
     */
    public static byte[] hash(final byte[] input) {
        Objects.requireNonNull(input, "input cannot be null");

        final Keccak.Digest256 digest = DIGEST.get();
        digest.reset(); // Ensure clean state
        return digest.digest(input);
    }

    /**
     * Computes the Keccak-256 hash of multiple input arrays concatenated.
     *
     * <p>
     * This is useful for hashing composed data without creating intermediate
     * byte arrays.
     *
     * @param inputs the data arrays to hash
     * @return 32-byte hash
     * @throws NullPointerException if inputs or any element is null
     */
    public static byte[] hash(final byte[]... inputs) {
        Objects.requireNonNull(inputs, "inputs cannot be null");

        final Keccak.Digest256 digest = DIGEST.get();
        digest.reset();
        for (byte[] input : inputs) {
            Objects.requireNonNull(input, "input element cannot be null");
            digest.update(input);
        }
        return digest.digest();
    }

    /**
     * Removes the cached digest instance from the current thread.
     *
     * <p>
     * Call this method to prevent memory leaks in thread pool environments
     * where threads are reused across different application contexts or
     * class loaders.
     *
     * <p>
     * This is safe to call even if the thread has never called {@link #hash};
     * it will simply be a no-op.
     *
     * @see ThreadLocal#remove()
     */
    public static void cleanup() {
        DIGEST.remove();
    }
}
