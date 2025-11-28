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
 * @since 0.2.0
 */
public final class Keccak256 {

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

        final Keccak.Digest256 digest = new Keccak.Digest256();
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

        final Keccak.Digest256 digest = new Keccak.Digest256();
        for (byte[] input : inputs) {
            Objects.requireNonNull(input, "input element cannot be null");
            digest.update(input);
        }
        return digest.digest();
    }
}
