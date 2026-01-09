package io.brane.core.crypto.hd;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;

/**
 * BIP-32 Hierarchical Deterministic (HD) key derivation.
 *
 * <p>
 * This class provides methods for deriving child keys from a master seed
 * following the BIP-32 specification. It supports both hardened and non-hardened
 * derivation.
 *
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki">BIP-32</a>
 */
final class Bip32 {

    private static final byte[] BITCOIN_SEED = "Bitcoin seed".getBytes(StandardCharsets.UTF_8);
    private static final int HARDENED_OFFSET = 0x80000000;

    private static final X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1");
    private static final ECDomainParameters CURVE = new ECDomainParameters(
            CURVE_PARAMS.getCurve(),
            CURVE_PARAMS.getG(),
            CURVE_PARAMS.getN(),
            CURVE_PARAMS.getH());

    private Bip32() {
        // Utility class
    }

    /**
     * Derives the master key from a BIP-39 seed.
     *
     * <p>
     * Uses HMAC-SHA512 with key "Bitcoin seed" to derive the master private key
     * and chain code from the seed bytes.
     *
     * @param seed 64-byte seed (typically from BIP-39 mnemonic)
     * @return extended key containing the master private key and chain code
     * @throws IllegalArgumentException if seed is null or not 64 bytes
     */
    static ExtendedKey masterKey(byte[] seed) {
        if (seed == null) {
            throw new IllegalArgumentException("Seed cannot be null");
        }
        if (seed.length != 64) {
            throw new IllegalArgumentException("Seed must be 64 bytes, got " + seed.length);
        }

        byte[] hmacResult = hmacSha512(BITCOIN_SEED, seed);

        // Split result: first 32 bytes are private key, last 32 bytes are chain code
        byte[] keyBytes = Arrays.copyOfRange(hmacResult, 0, 32);
        byte[] chainCode = Arrays.copyOfRange(hmacResult, 32, 64);

        // Clear the HMAC result
        Arrays.fill(hmacResult, (byte) 0);

        // Validate the key is valid for secp256k1
        BigInteger keyValue = new BigInteger(1, keyBytes);
        if (keyValue.compareTo(BigInteger.ZERO) == 0 || keyValue.compareTo(CURVE.getN()) >= 0) {
            Arrays.fill(keyBytes, (byte) 0);
            Arrays.fill(chainCode, (byte) 0);
            throw new IllegalArgumentException("Invalid master key derived from seed");
        }

        return new ExtendedKey(keyBytes, chainCode);
    }

    /**
     * Derives a child key at a single index level.
     *
     * <p>
     * Supports both hardened derivation (index >= 0x80000000) and normal derivation.
     * Hardened derivation uses the private key directly, while normal derivation
     * uses the public key point.
     *
     * @param parent the parent extended key
     * @param index  the child index (use HARDENED_OFFSET for hardened keys)
     * @return the derived child extended key
     * @throws IllegalArgumentException if parent is null or derivation produces invalid key
     */
    static ExtendedKey deriveChild(ExtendedKey parent, int index) {
        if (parent == null) {
            throw new IllegalArgumentException("Parent key cannot be null");
        }

        byte[] data = new byte[37];

        // In Java's signed 32-bit arithmetic, index < 0 detects hardened derivation because
        // HARDENED_OFFSET (0x80000000) sets the sign bit, making any hardened index negative.
        if (index < 0) {
            // Hardened derivation: use private key directly
            // Data format: 0x00 || ser256(kpar) || ser32(i)
            data[0] = 0x00;
            System.arraycopy(parent.keyBytes(), 0, data, 1, 32);
        } else {
            // Normal derivation
            // Use public key: serP(point(kpar)) || ser32(i)
            byte[] publicKey = derivePublicKey(parent.keyBytes());
            System.arraycopy(publicKey, 0, data, 0, 33);
        }

        // Append index in big-endian
        data[33] = (byte) (index >>> 24);
        data[34] = (byte) (index >>> 16);
        data[35] = (byte) (index >>> 8);
        data[36] = (byte) index;

        byte[] hmacResult = hmacSha512(parent.chainCode(), data);
        Arrays.fill(data, (byte) 0);

        // Split result
        byte[] il = Arrays.copyOfRange(hmacResult, 0, 32);
        byte[] ir = Arrays.copyOfRange(hmacResult, 32, 64);
        Arrays.fill(hmacResult, (byte) 0);

        // Child key = (IL + kpar) mod n
        BigInteger ilValue = new BigInteger(1, il);
        BigInteger parentKeyValue = new BigInteger(1, parent.keyBytes());
        BigInteger childKeyValue = ilValue.add(parentKeyValue).mod(CURVE.getN());

        // Validate the derived key
        if (ilValue.compareTo(CURVE.getN()) >= 0 || childKeyValue.compareTo(BigInteger.ZERO) == 0) {
            Arrays.fill(il, (byte) 0);
            Arrays.fill(ir, (byte) 0);
            throw new IllegalArgumentException("Invalid child key derived at index " + index);
        }

        byte[] childKeyBytes = toBytes32(childKeyValue);
        Arrays.fill(il, (byte) 0);

        return new ExtendedKey(childKeyBytes, ir);
    }

    /**
     * Derives a key from a full BIP-32 derivation path.
     *
     * <p>
     * Path format: "m/purpose'/coin'/account'/change/index"
     * <ul>
     * <li>Path must start with "m"</li>
     * <li>Path components are separated by "/"</li>
     * <li>Hardened derivation is indicated by "'" or "h" suffix</li>
     * </ul>
     *
     * <p>
     * Example paths:
     * <ul>
     * <li>"m/44'/60'/0'/0/0" - First Ethereum address (BIP-44)</li>
     * <li>"m/44'/60'/0'/0/1" - Second Ethereum address</li>
     * </ul>
     *
     * @param masterKey the master extended key
     * @param path      the derivation path (e.g., "m/44'/60'/0'/0/0")
     * @return the derived extended key
     * @throws IllegalArgumentException if path is invalid or derivation fails
     */
    static ExtendedKey derivePath(ExtendedKey masterKey, String path) {
        if (masterKey == null) {
            throw new IllegalArgumentException("Master key cannot be null");
        }
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        String[] components = path.split("/");
        if (components.length == 0 || !components[0].equals("m")) {
            throw new IllegalArgumentException("Path must start with 'm', got: " + path);
        }

        ExtendedKey current = masterKey;
        for (int i = 1; i < components.length; i++) {
            String component = components[i];
            if (component.isEmpty()) {
                throw new IllegalArgumentException("Empty path component at position " + i);
            }

            boolean hardened = component.endsWith("'") || component.endsWith("h");
            String indexStr = hardened ? component.substring(0, component.length() - 1) : component;

            long indexLong;
            try {
                indexLong = Long.parseUnsignedLong(indexStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid path component: " + component);
            }

            // Valid index range is 0 to 2^31-1 (before hardening offset)
            if (indexLong >= Integer.toUnsignedLong(HARDENED_OFFSET)) {
                throw new IllegalArgumentException("Index out of range: " + component);
            }

            int index = (int) indexLong;
            if (hardened) {
                index |= HARDENED_OFFSET;
            }

            ExtendedKey previous = current;
            current = deriveChild(current, index);
            // Security: Zero intermediate keys immediately after use to minimize exposure window.
            // Each intermediate key in the derivation chain (e.g., m/44', m/44'/60') can derive
            // all keys below it in the hierarchy. By destroying them promptly, we reduce the risk
            // of key material being recovered from memory dumps, swap files, or cold-boot attacks.
            // Don't destroy the original masterKey passed by the caller.
            if (previous != masterKey) {
                previous.destroy();
            }
        }

        return current;
    }

    /**
     * Computes HMAC-SHA512.
     */
    private static byte[] hmacSha512(byte[] key, byte[] data) {
        var hmac = new HMac(new SHA512Digest());
        hmac.init(new KeyParameter(key));
        hmac.update(data, 0, data.length);
        byte[] result = new byte[64];
        hmac.doFinal(result, 0);
        return result;
    }

    /**
     * Derives the compressed public key from a private key.
     */
    private static byte[] derivePublicKey(byte[] privateKeyBytes) {
        BigInteger privateKeyValue = new BigInteger(1, privateKeyBytes);
        ECPoint publicKey = new FixedPointCombMultiplier()
                .multiply(CURVE.getG(), privateKeyValue)
                .normalize();
        return publicKey.getEncoded(true); // compressed format: 33 bytes
    }

    /**
     * Converts a BigInteger to a 32-byte array with proper padding.
     */
    private static byte[] toBytes32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == 32) {
            return bytes;
        }
        byte[] result = new byte[32];
        if (bytes.length < 32) {
            System.arraycopy(bytes, 0, result, 32 - bytes.length, bytes.length);
        } else {
            // Truncate leading zero byte from BigInteger's sign bit
            System.arraycopy(bytes, bytes.length - 32, result, 0, 32);
        }
        return result;
    }

    /**
     * Extended key containing private key bytes and chain code.
     */
    static final class ExtendedKey {
        private final byte[] keyBytes;
        private final byte[] chainCode;
        private boolean destroyed;

        ExtendedKey(byte[] keyBytes, byte[] chainCode) {
            if (keyBytes == null || keyBytes.length != 32) {
                throw new IllegalArgumentException("Key bytes must be 32 bytes");
            }
            if (chainCode == null || chainCode.length != 32) {
                throw new IllegalArgumentException("Chain code must be 32 bytes");
            }
            this.keyBytes = keyBytes.clone();
            this.chainCode = chainCode.clone();
        }

        byte[] keyBytes() {
            return keyBytes.clone();
        }

        byte[] chainCode() {
            return chainCode.clone();
        }

        /**
         * Zeros the internal key material for secure destruction.
         * After calling this method, the key is marked as destroyed.
         */
        void destroy() {
            if (destroyed) return;
            Arrays.fill(keyBytes, (byte) 0);
            Arrays.fill(chainCode, (byte) 0);
            destroyed = true;
        }

        /**
         * Returns whether this key has been destroyed.
         *
         * @return true if {@link #destroy()} has been called
         */
        boolean isDestroyed() {
            return destroyed;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ExtendedKey other)) {
                return false;
            }
            return Arrays.equals(keyBytes, other.keyBytes)
                    && Arrays.equals(chainCode, other.chainCode);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(keyBytes);
            result = 31 * result + Arrays.hashCode(chainCode);
            return result;
        }
    }
}
