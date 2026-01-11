package io.brane.kzg;

import java.util.List;
import java.util.Objects;

import ethereum.ckzg4844.CKZG4844JNI;

import io.brane.core.crypto.Kzg;
import io.brane.core.error.KzgException;
import io.brane.core.types.Blob;
import io.brane.core.types.KzgCommitment;
import io.brane.core.types.KzgProof;

/**
 * KZG implementation using the c-kzg-4844 native library.
 * <p>
 * This class wraps the {@link CKZG4844JNI} native bindings to provide KZG commitment
 * operations for EIP-4844 blob transactions. A trusted setup must be loaded before
 * using any cryptographic operations.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Load from classpath (recommended)
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
        try {
            CKZG4844JNI.loadNativeLibrary();
        } catch (Exception e) {
            throw KzgException.setupError("Failed to load native KZG library", e);
        }

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

        try {
            CKZG4844JNI.loadNativeLibrary();
        } catch (Exception e) {
            throw KzgException.setupError("Failed to load native KZG library", e);
        }

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
            // TODO: Use blob.toBytesUnsafe() for performance - native library doesn't modify input.
            // toBytesUnsafe() is package-private in io.brane.core.types; would need internal API.
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
            // TODO: Use toBytesUnsafe() for performance - native library doesn't modify input.
            // toBytesUnsafe() is package-private in io.brane.core.types; would need internal API.
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
            // Flatten lists into single byte arrays as expected by the native library
            byte[] blobsFlat = new byte[blobs.size() * Blob.SIZE];
            byte[] commitmentsFlat = new byte[commitments.size() * KzgCommitment.SIZE];
            byte[] proofsFlat = new byte[proofs.size() * KzgProof.SIZE];

            // TODO: Use toBytesUnsafe() for performance - native library doesn't modify input.
            // toBytesUnsafe() is package-private in io.brane.core.types; would need internal API.
            for (int i = 0; i < blobs.size(); i++) {
                Blob blob = Objects.requireNonNull(blobs.get(i), "blobs[" + i + "]");
                KzgCommitment commitment = Objects.requireNonNull(commitments.get(i), "commitments[" + i + "]");
                KzgProof proof = Objects.requireNonNull(proofs.get(i), "proofs[" + i + "]");

                System.arraycopy(blob.toBytes(), 0, blobsFlat, i * Blob.SIZE, Blob.SIZE);
                System.arraycopy(commitment.toBytes(), 0, commitmentsFlat,
                        i * KzgCommitment.SIZE, KzgCommitment.SIZE);
                System.arraycopy(proof.toBytes(), 0, proofsFlat,
                        i * KzgProof.SIZE, KzgProof.SIZE);
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
