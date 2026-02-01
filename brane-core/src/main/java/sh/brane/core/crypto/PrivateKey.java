// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.crypto;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

import javax.security.auth.Destroyable;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;

import sh.brane.core.types.Address;
import sh.brane.primitives.Hex;

/**
 * Ethereum private key with secp256k1 signing capabilities.
 *
 * <p>
 * This class provides:
 * <ul>
 * <li>Private key loading from hex strings</li>
 * <li>Address derivation from public key</li>
 * <li>Deterministic ECDSA signing (RFC 6979)</li>
 * <li>Public key recovery from signatures</li>
 * <li>Low-s normalization for malleability protection</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * PrivateKey key = PrivateKey.fromHex("0x1234...");
 * Address address = key.toAddress();
 *
 * byte[] messageHash = Keccak256.hash(message);
 * Signature signature = key.sign(messageHash);
 *
 * Address recovered = PrivateKey.recoverAddress(messageHash, signature);
 * assert recovered.equals(address);
 * }</pre>
 *
 * <h2>Security Considerations</h2>
 *
 * <p>
 * This class implements {@link Destroyable} to allow clearing sensitive key material
 * from memory when no longer needed. While Java's BigInteger is immutable and cannot
 * be fully zeroed, calling {@link #destroy()} will:
 * <ul>
 * <li>Null out internal references to prevent further use</li>
 * <li>Mark the key as destroyed, causing subsequent operations to fail</li>
 * <li>Help the garbage collector reclaim memory sooner</li>
 * </ul>
 *
 * <p>
 * For maximum security, call {@link #destroy()} as soon as the key is no longer needed.
 *
 * @since 0.2.0
 */
public final class PrivateKey implements Destroyable {

    private static final int PRIVATE_KEY_SIZE = 32;
    private static final X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1");
    private static final ECDomainParameters CURVE = new ECDomainParameters(
            CURVE_PARAMS.getCurve(),
            CURVE_PARAMS.getG(),
            CURVE_PARAMS.getN(),
            CURVE_PARAMS.getH());

    private volatile BigInteger privateKeyValue;
    private volatile ECPoint publicKey;
    private volatile boolean destroyed = false;

    private PrivateKey(final byte[] keyBytes) {
        if (keyBytes.length != PRIVATE_KEY_SIZE) {
            throw new IllegalArgumentException("Private key must be " + PRIVATE_KEY_SIZE + " bytes, got " + keyBytes.length);
        }

        try {
            this.privateKeyValue = new BigInteger(1, keyBytes);

            if (privateKeyValue.compareTo(BigInteger.ZERO) == 0) {
                throw new IllegalArgumentException("Private key cannot be zero");
            }
            if (privateKeyValue.compareTo(CURVE.getN()) >= 0) {
                throw new IllegalArgumentException("Private key must be less than curve order");
            }

            // Compute public key: G * privateKey
            this.publicKey = new FixedPointCombMultiplier().multiply(CURVE.getG(), privateKeyValue);
        } finally {
            // Zero the input byte array to minimize exposure of key material
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    /**
     * Creates a private key from a hex string.
     *
     * @param hexString hex-encoded private key (with or without 0x prefix)
     * @return private key instance
     * @throws IllegalArgumentException if hex string is invalid or key is out of
     *                                  range
     */
    public static PrivateKey fromHex(final String hexString) {
        Objects.requireNonNull(hexString, "hex string cannot be null");
        final byte[] keyBytes = Hex.decode(hexString);
        return new PrivateKey(keyBytes);
    }

    /**
     * Creates a private key from raw bytes.
     *
     * <p>
     * <b>Security note:</b> The input byte array will be zeroed after the key is created
     * to minimize exposure of key material in memory. Callers should not rely on the
     * contents of the array after this method returns.
     *
     * @apiNote This method takes ownership of the provided byte array and will zero it
     *          after extracting the key material. If you need to retain the original bytes,
     *          pass a copy: {@code PrivateKey.fromBytes(keyBytes.clone())}
     *
     * @param keyBytes 32-byte private key (will be zeroed after use)
     * @return private key instance
     * @throws IllegalArgumentException if key bytes are invalid
     */
    public static PrivateKey fromBytes(final byte[] keyBytes) {
        Objects.requireNonNull(keyBytes, "key bytes cannot be null");
        return new PrivateKey(keyBytes);
    }

    /**
     * Derives the Ethereum address from this private key's public key.
     *
     * <p>
     * Algorithm:
     * <ol>
     * <li>Compute uncompressed public key (65 bytes: 0x04 || x || y)</li>
     * <li>Take Keccak256 hash of the last 64 bytes (skip 0x04 prefix)</li>
     * <li>Take last 20 bytes of the hash</li>
     * </ol>
     *
     * @return Ethereum address
     * @throws IllegalStateException if the key has been destroyed
     */
    public Address toAddress() {
        final ECPoint pubKey;
        synchronized (this) {
            checkNotDestroyed();
            pubKey = publicKey;
        }
        final byte[] pubKeyBytes = pubKey.getEncoded(false); // uncompressed: 0x04 || x || y

        // Hash public key (skip first byte 0x04)
        final byte[] hash = Keccak256.hash(Arrays.copyOfRange(pubKeyBytes, 1, pubKeyBytes.length));

        // Take last 20 bytes
        final byte[] addressBytes = Arrays.copyOfRange(hash, 12, 32);
        return Address.fromBytes(addressBytes);
    }

    /**
     * Signs a message hash using deterministic ECDSA (RFC 6979).
     *
     * <p>
     * The signature is automatically normalized to low-s to prevent malleability.
     *
     * @param messageHash 32-byte Keccak256 hash of the message
     * @return ECDSA signature with v=0 or v=1 (use with EIP-155 encoding for
     *         transactions)
     * @throws IllegalArgumentException if message hash is not 32 bytes
     * @throws IllegalStateException if the key has been destroyed
     */
    public Signature sign(final byte[] messageHash) {
        return signFast(messageHash);
    }

    /**
     * Optimized signing that avoids public key recovery.
     *
     * @param messageHash 32-byte hash
     * @return Signature with v
     * @throws IllegalStateException if the key has been destroyed
     */
    public Signature signFast(final byte[] messageHash) {
        Objects.requireNonNull(messageHash, "message hash cannot be null");
        if (messageHash.length != 32) {
            throw new IllegalArgumentException("Message hash must be 32 bytes, got " + messageHash.length);
        }
        final BigInteger key;
        synchronized (this) {
            checkNotDestroyed();
            key = privateKeyValue;
        }
        return FastSigner.sign(messageHash, key);
    }

    /**
     * Recovers the Ethereum address from a signature and message hash.
     *
     * @param messageHash 32-byte hash that was signed
     * @param signature   the signature
     * @return recovered Ethereum address
     * @throws IllegalArgumentException if recovery fails
     */
    public static Address recoverAddress(final byte[] messageHash, final Signature signature) {
        Objects.requireNonNull(messageHash, "message hash cannot be null");
        Objects.requireNonNull(signature, "signature cannot be null");

        if (messageHash.length != 32) {
            throw new IllegalArgumentException("Message hash must be 32 bytes");
        }

        final BigInteger r = new BigInteger(1, signature.r());
        final BigInteger s = new BigInteger(1, signature.s());

        // Get recovery ID from signature (handles both simple v and EIP-155 v)
        final int recoveryId;
        if (signature.v() == 0 || signature.v() == 1) {
            recoveryId = signature.v();
        } else if (signature.v() == 27 || signature.v() == 28) {
            recoveryId = signature.v() - 27;
        } else {
            // EIP-155: v = chainId * 2 + 35 + recoveryId
            // We derive recoveryId (0 or 1) from the parity of (v - 35).
            // This works even if we don't know the chainId.
            recoveryId = (signature.v() - 35) & 1;
        }

        try {
            final ECPoint publicKey = recoverPublicKey(r, s, messageHash, recoveryId);
            if (publicKey != null) {
                final byte[] pubKeyBytes = publicKey.getEncoded(false);
                final byte[] hash = Keccak256.hash(Arrays.copyOfRange(pubKeyBytes, 1, pubKeyBytes.length));
                final byte[] addressBytes = Arrays.copyOfRange(hash, 12, 32);
                return Address.fromBytes(addressBytes);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to recover public key from signature", e);
        }

        throw new IllegalArgumentException("Failed to recover public key from signature");
    }

    /**
     * Recovers public key from signature components.
     */
    private static ECPoint recoverPublicKey(
            final BigInteger r,
            final BigInteger s,
            final byte[] messageHash,
            final int recoveryId) {

        if (r.signum() <= 0 || s.signum() <= 0) {
            return null;
        }
        if (r.compareTo(CURVE.getN()) >= 0 || s.compareTo(CURVE.getN()) >= 0) {
            return null;
        }

        // R = (r, y) where y's parity matches recoveryId
        final BigInteger x = r;
        final ECPoint R = decompressKey(x, (recoveryId & 1) == 1);
        if (R == null || !R.multiply(CURVE.getN()).isInfinity()) {
            return null;
        }

        // e = messageHash as BigInteger
        final BigInteger e = new BigInteger(1, messageHash);

        // Q = r^-1 * (s*R - e*G)
        final BigInteger rInv = r.modInverse(CURVE.getN());
        final BigInteger srInv = rInv.multiply(s).mod(CURVE.getN());
        final BigInteger eInv = rInv.multiply(e).mod(CURVE.getN());

        final ECPoint q = R.multiply(srInv).subtract(CURVE.getG().multiply(eInv));
        return q.normalize();
    }

    /**
     * Decompresses an EC point from x coordinate and y parity.
     */
    private static ECPoint decompressKey(final BigInteger x, final boolean yBit) {
        final ECPoint point = CURVE.getCurve().decodePoint(encodeCompressed(x, yBit));
        return point.isValid() ? point : null;
    }

    /**
     * Encodes x coordinate and y parity into compressed point format.
     */
    private static byte[] encodeCompressed(final BigInteger x, final boolean yBit) {
        final byte[] encoded = new byte[33];
        encoded[0] = (byte) (yBit ? 0x03 : 0x02);
        final byte[] xBytes = x.toByteArray();
        final int off = xBytes.length > 32 ? 1 : 0;
        System.arraycopy(xBytes, off, encoded, 33 - (xBytes.length - off), xBytes.length - off);
        return encoded;
    }

    /**
     * Destroys this private key by clearing internal references.
     *
     * <p>
     * After calling this method, any attempt to use the key (sign, toAddress, etc.)
     * will throw an {@link IllegalStateException}.
     *
     * <p>
     * <b>Note:</b> Due to Java's BigInteger being immutable, this cannot guarantee
     * complete removal of key material from memory. However, it nullifies references
     * to help garbage collection and prevents accidental reuse of the key.
     *
     * <p>
     * This method is thread-safe. Concurrent calls to destroy and sign/toAddress
     * will either complete the operation or throw {@link IllegalStateException}.
     */
    @Override
    public void destroy() {
        synchronized (this) {
            destroyed = true;
            privateKeyValue = null;
            publicKey = null;
        }
    }

    /**
     * Returns whether this key has been destroyed.
     *
     * @return true if {@link #destroy()} has been called
     */
    @Override
    public boolean isDestroyed() {
        return destroyed;
    }

    /**
     * Checks that the key has not been destroyed.
     *
     * @throws IllegalStateException if the key has been destroyed
     */
    private void checkNotDestroyed() {
        if (destroyed) {
            throw new IllegalStateException("PrivateKey has been destroyed");
        }
    }

    /**
     * Returns a string representation of this private key.
     *
     * <p>
     * For security, the actual private key bytes are never included.
     * Instead, the derived address is shown to help identify the key.
     * If the key has been destroyed, returns "PrivateKey[destroyed]".
     *
     * <p>
     * This method is thread-safe with respect to concurrent {@link #destroy()} calls.
     *
     * @return string representation showing address or destroyed status
     */
    @Override
    public String toString() {
        try {
            return "PrivateKey[address=" + toAddress() + "]";
        } catch (IllegalStateException e) {
            return "PrivateKey[destroyed]";
        }
    }
}
