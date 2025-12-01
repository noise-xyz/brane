package io.brane.core.crypto;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;

import java.math.BigInteger;

/**
 * High-performance deterministic ECDSA signer.
 * 
 * <p>
 * This class optimizes the signing process by:
 * 1. Calculating 'v' (recovery ID) directly from the random point R, avoiding
 * expensive public key recovery.
 * 2. Using Bouncy Castle's low-level APIs efficiently.
 */
public final class FastSigner {

    private static final X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1");
    private static final ECDomainParameters CURVE = new ECDomainParameters(
            CURVE_PARAMS.getCurve(),
            CURVE_PARAMS.getG(),
            CURVE_PARAMS.getN(),
            CURVE_PARAMS.getH());
    private static final BigInteger HALF_CURVE_ORDER = CURVE_PARAMS.getN().shiftRight(1);
    private static final FixedPointCombMultiplier MULTIPLIER = new FixedPointCombMultiplier();

    private FastSigner() {
    }

    /**
     * Signs a message hash and returns the signature with recovery ID.
     * 
     * @param messageHash 32-byte hash
     * @param privateKey  private key
     * @return Signature with v (0 or 1)
     */
    public static Signature sign(byte[] messageHash, BigInteger privateKey) {
        HMacDSAKCalculator kCalculator = new HMacDSAKCalculator(new SHA256Digest());
        kCalculator.init(CURVE.getN(), privateKey, messageHash);

        BigInteger r, s;
        int v;
        BigInteger k;
        ECPoint p;

        do {
            k = kCalculator.nextK();
            p = MULTIPLIER.multiply(CURVE.getG(), k).normalize();

            // r = x1 mod n
            r = p.getAffineXCoord().toBigInteger().mod(CURVE.getN());
        } while (r.equals(BigInteger.ZERO));

        // s = k^-1 * (z + r * d) mod n
        BigInteger d = privateKey;
        BigInteger z = new BigInteger(1, messageHash);

        BigInteger kInv = k.modInverse(CURVE.getN());
        BigInteger rd = r.multiply(d);
        s = kInv.multiply(z.add(rd)).mod(CURVE.getN());

        if (s.equals(BigInteger.ZERO)) {
            // Extremely rare, but technically possible. Should retry loop in a real impl,
            // but for deterministic K, this means the key/msg combo is bad.
            // However, RFC 6979 handles this by updating K.
            // For simplicity in this fast path, we'll just recurse or throw.
            // Let's recurse (it will re-init kCalculator, which is fine but slightly
            // inefficient).
            return sign(messageHash, privateKey);
        }

        // Calculate v (recovery ID) based on the original point R = k*G.
        // v = 0 if R.y is even, 1 if R.y is odd.
        // This is the "y-parity" of the point R.
        v = p.getAffineYCoord().toBigInteger().testBit(0) ? 1 : 0;

        // Low-s normalization (EIP-2)
        // ECDSA signatures are malleable; (r, s) and (r, -s mod n) are both valid.
        // Ethereum requires s <= n/2 to prevent malleability.
        if (s.compareTo(HALF_CURVE_ORDER) > 0) {
            s = CURVE.getN().subtract(s);

            // When we flip s to (n - s), we are effectively using the inverse of k (-k).
            // This results in the point -R = (-x, -y).
            // Since the curve order n is odd, if y is even, -y (mod p) will be odd, and
            // vice versa.
            // Therefore, we must flip the parity bit v.
            v ^= 1;
        }

        return new Signature(toBytes32(r), toBytes32(s), v);
    }

    private static byte[] toBytes32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] result = new byte[32];
        if (bytes.length == 32) {
            return bytes;
        } else if (bytes.length < 32) {
            System.arraycopy(bytes, 0, result, 32 - bytes.length, bytes.length);
        } else {
            System.arraycopy(bytes, bytes.length - 32, result, 0, 32);
        }
        return result;
    }
}
