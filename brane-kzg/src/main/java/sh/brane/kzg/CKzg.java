// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.kzg;

import java.util.List;
import java.util.Objects;

import ethereum.ckzg4844.CKZG4844JNI;

import sh.brane.core.crypto.Kzg;
import sh.brane.core.error.KzgException;
import sh.brane.core.types.Blob;
import sh.brane.core.types.FixedSizeG1Point;
import sh.brane.core.types.KzgCommitment;
import sh.brane.core.types.KzgProof;

/**
 * KZG implementation using the c-kzg-4844 native library.
 * <p>
 * This class wraps the {@link CKZG4844JNI} native bindings to provide KZG commitment
 * operations for EIP-4844 blob transactions. A trusted setup must be loaded before
 * using any cryptographic operations.
 *
 * <h2>Global State Warning</h2>
 * <p>
 * <b>Important:</b> The c-kzg-4844 native library maintains a single global trusted setup
 * shared across all {@code CKzg} instances within the JVM. This has several implications:
 * <ul>
 *   <li>Multiple calls to {@link #loadFromClasspath()} or {@link #loadTrustedSetup(String)}
 *       will reload the global trusted setup, affecting all existing instances</li>
 *   <li>There is no per-instance state; all instances operate on the same native setup</li>
 *   <li>In tests, loading a different trusted setup will affect all code using KZG</li>
 *   <li>For typical applications, load the trusted setup once at startup and reuse the
 *       instance throughout the application lifecycle</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Load from classpath (recommended) - do this once at startup
 * Kzg kzg = CKzg.loadFromClasspath();
 *
 * // Or load from a custom path
 * Kzg kzg = CKzg.loadTrustedSetup("/path/to/trusted_setup.txt");
 *
 * // Use for blob operations
 * KzgCommitment commitment = kzg.blobToCommitment(blob);
 * KzgProof proof = kzg.computeProof(blob, commitment);
 * boolean valid = kzg.verifyBlobKzgProof(blob, commitment, proof);
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe. The underlying c-kzg-4844 library uses a single global
 * trusted setup which is initialized once. All KZG operations can be called concurrently
 * from multiple threads. The native library handles synchronization internally.
 *
 * <h2>Resource Management</h2>
 * <p>
 * The native library and trusted setup are loaded once and remain in memory for the
 * lifetime of the JVM. There is no need to explicitly free resources, but
 * {@link CKZG4844JNI#freeTrustedSetup()} can be called if needed before loading
 * a different setup.
 *
 * @since 0.2.0
 */
public final class CKzg implements Kzg {

    private static final String CLASSPATH_TRUSTED_SETUP = "/trusted_setup.txt";

    /**
     * Default precompute level for trusted setup loading.
     * Value 0 means no precomputation (fastest startup, slower operations).
     * Higher values (up to 15) trade memory for faster cryptographic operations.
     */
    private static final long DEFAULT_PRECOMPUTE = 0L;

    /**
     * Private constructor - use factory methods to create instances.
     */
    private CKzg() {
    }

    /**
     * Loads the native KZG library.
     *
     * @throws KzgException with kind SETUP_ERROR if the native library cannot be loaded
     */
    private static void loadNativeLibrary() {
        try {
            CKZG4844JNI.loadNativeLibrary();
        } catch (Exception e) {
            throw KzgException.setupError("Failed to load native KZG library", e);
        }
    }

    /**
     * Loads the trusted setup from the bundled classpath resource.
     * <p>
     * This method loads the Ethereum mainnet trusted setup file that is bundled
     * with the brane-kzg module. This is the recommended way to initialize KZG
     * for production use.
     *
     * @return a new Kzg instance
     * @throws KzgException with kind SETUP_ERROR if the native library cannot be loaded
     *         or the trusted setup cannot be initialized
     */
    public static Kzg loadFromClasspath() {
        loadNativeLibrary();

        try {
            CKZG4844JNI.loadTrustedSetupFromResource(
                    CLASSPATH_TRUSTED_SETUP, CKzg.class, DEFAULT_PRECOMPUTE);
        } catch (IllegalArgumentException e) {
            throw KzgException.setupError(
                    "Trusted setup not found on classpath: " + CLASSPATH_TRUSTED_SETUP, e);
        } catch (Exception e) {
            throw KzgException.setupError("Failed to load trusted setup", e);
        }

        return new CKzg();
    }

    /**
     * Loads the trusted setup from a file path.
     * <p>
     * This method loads a custom trusted setup file. Use this for testing with
     * alternative setups or when the bundled setup is not appropriate.
     *
     * @param path the path to the trusted setup file
     * @return a new Kzg instance
     * @throws KzgException with kind SETUP_ERROR if the native library cannot be loaded
     *         or the trusted setup file cannot be read
     * @throws NullPointerException if path is null
     */
    public static Kzg loadTrustedSetup(String path) {
        Objects.requireNonNull(path, "path");

        loadNativeLibrary();

        try {
            CKZG4844JNI.loadTrustedSetup(path, DEFAULT_PRECOMPUTE);
        } catch (Exception e) {
            throw KzgException.setupError("Failed to load trusted setup from: " + path, e);
        }

        return new CKzg();
    }

    @Override
    public KzgCommitment blobToCommitment(Blob blob) {
        Objects.requireNonNull(blob, "blob");

        try {
            // toBytes() performs a defensive copy. The allocation overhead is acceptable since
            // it is negligible compared to the native KZG cryptographic operations.
            byte[] commitment = CKZG4844JNI.blobToKzgCommitment(blob.toBytes());
            return new KzgCommitment(commitment);
        } catch (Exception e) {
            throw KzgException.commitmentError("Failed to compute KZG commitment: " + e.getMessage(), e);
        }
    }

    @Override
    public KzgProof computeProof(Blob blob, KzgCommitment commitment) {
        Objects.requireNonNull(blob, "blob");
        Objects.requireNonNull(commitment, "commitment");

        try {
            // toBytes() performs defensive copies. The allocation overhead is acceptable since
            // it is negligible compared to the native KZG cryptographic operations.
            byte[] proof = CKZG4844JNI.computeBlobKzgProof(blob.toBytes(), commitment.toBytes());
            return new KzgProof(proof);
        } catch (Exception e) {
            throw KzgException.proofError("Failed to compute KZG proof: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyBlobKzgProof(Blob blob, KzgCommitment commitment, KzgProof proof) {
        Objects.requireNonNull(blob, "blob");
        Objects.requireNonNull(commitment, "commitment");
        Objects.requireNonNull(proof, "proof");

        try {
            return CKZG4844JNI.verifyBlobKzgProof(
                    blob.toBytes(),
                    commitment.toBytes(),
                    proof.toBytes());
        } catch (Exception e) {
            throw KzgException.proofError("Failed to verify KZG proof: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyBlobKzgProofBatch(
            List<Blob> blobs,
            List<KzgCommitment> commitments,
            List<KzgProof> proofs) {
        Objects.requireNonNull(blobs, "blobs");
        Objects.requireNonNull(commitments, "commitments");
        Objects.requireNonNull(proofs, "proofs");

        if (blobs.size() != commitments.size() || blobs.size() != proofs.size()) {
            throw new IllegalArgumentException(
                    "Lists must have same size: blobs=" + blobs.size() +
                    ", commitments=" + commitments.size() +
                    ", proofs=" + proofs.size());
        }

        if (blobs.isEmpty()) {
            return true;
        }

        try {
            // Flatten lists into single byte arrays as expected by the native library.
            // toBytes() performs defensive copies. The allocation overhead is acceptable since
            // it is negligible compared to the native KZG cryptographic operations.
            byte[] blobsFlat = new byte[blobs.size() * Blob.SIZE];
            byte[] commitmentsFlat = new byte[commitments.size() * FixedSizeG1Point.SIZE];
            byte[] proofsFlat = new byte[proofs.size() * FixedSizeG1Point.SIZE];

            for (int i = 0; i < blobs.size(); i++) {
                Blob blob = Objects.requireNonNull(blobs.get(i), "blobs[" + i + "]");
                KzgCommitment commitment = Objects.requireNonNull(commitments.get(i), "commitments[" + i + "]");
                KzgProof proof = Objects.requireNonNull(proofs.get(i), "proofs[" + i + "]");

                System.arraycopy(blob.toBytes(), 0, blobsFlat, i * Blob.SIZE, Blob.SIZE);
                System.arraycopy(commitment.toBytes(), 0, commitmentsFlat,
                        i * FixedSizeG1Point.SIZE, FixedSizeG1Point.SIZE);
                System.arraycopy(proof.toBytes(), 0, proofsFlat,
                        i * FixedSizeG1Point.SIZE, FixedSizeG1Point.SIZE);
            }

            return CKZG4844JNI.verifyBlobKzgProofBatch(
                    blobsFlat,
                    commitmentsFlat,
                    proofsFlat,
                    blobs.size());
        } catch (Exception e) {
            throw KzgException.proofError("Failed to verify KZG proof batch: " + e.getMessage(), e);
        }
    }
}
