package io.brane.core.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * SHA-256 hashing utility.
 *
 * <p>
 * Provides a simple interface to Java's standard SHA-256 implementation.
 * SHA-256 is commonly used in Bitcoin and various cryptographic protocols.
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
 * byte[] hash = Sha256.hash(data);
 * String hashHex = Hex.encode(hash);
 * }</pre>
 *
 * <h2>ThreadLocal Memory Management</h2>
 *
 * <p>
 * <b>Memory Leak Warning:</b> This class uses {@link ThreadLocal} to cache digest
 * instances for performance. In thread pool environments (servlet containers,
 * application servers, executors), failure to call {@link #cleanup()} can cause
 * memory leaks and classloader leaks during hot redeployment.
 *
 * <h3>When to Call cleanup()</h3>
 * <ul>
 * <li><b>Servlet/Web Applications:</b> In a filter's {@code finally} block or
 *     {@code ServletContextListener.contextDestroyed()}</li>
 * <li><b>Custom Thread Pools:</b> When returning threads to the pool or shutting down</li>
 * <li><b>Application Shutdown:</b> In shutdown hooks or lifecycle callbacks</li>
 * </ul>
 *
 * <h3>Example: Servlet Filter</h3>
 * <pre>{@code
 * public class BraneCleanupFilter implements Filter {
 *     public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
 *             throws IOException, ServletException {
 *         try {
 *             chain.doFilter(req, res);
 *         } finally {
 *             Sha256.cleanup();  // Prevent memory leak
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h3>Example: ExecutorService</h3>
 * <pre>{@code
 * executor.submit(() -> {
 *     try {
 *         // ... use Sha256.hash() ...
 *     } finally {
 *         Sha256.cleanup();
 *     }
 * });
 * }</pre>
 *
 * <p>
 * <b>Note:</b> For simple CLI applications or short-lived processes, cleanup is
 * not strictly necessary as ThreadLocal values are released when the JVM exits.
 *
 * @since 0.2.0
 */
public final class Sha256 {

    private static final String ALGORITHM = "SHA-256";

    private static final ThreadLocal<MessageDigest> DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by Java spec, this should never happen
            throw new AssertionError("SHA-256 algorithm not available", e);
        }
    });

    private Sha256() {
        // Utility class
    }

    /**
     * Computes the SHA-256 hash of the input bytes.
     *
     * @param input the data to hash
     * @return 32-byte hash
     * @throws NullPointerException if input is null
     */
    public static byte[] hash(final byte[] input) {
        Objects.requireNonNull(input, "input cannot be null");

        final MessageDigest digest = DIGEST.get();
        digest.reset(); // Ensure clean state
        return digest.digest(input);
    }

    /**
     * Computes the SHA-256 hash of multiple input arrays concatenated.
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

        final MessageDigest digest = DIGEST.get();
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
