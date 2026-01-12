// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import io.brane.core.types.Address;

/**
 * Rigorous verification for FastSigner.
 * <p>
 * This test suite goes beyond standard unit tests to ensure cryptographic
 * correctness
 * by comparing against established "Gold Standard" implementations and
 * performing
 * extensive property-based fuzz testing.
 */
class FastSignerVerificationTest {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static final X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1");
    private static final ECDomainParameters CURVE = new ECDomainParameters(
            CURVE_PARAMS.getCurve(),
            CURVE_PARAMS.getG(),
            CURVE_PARAMS.getN(),
            CURVE_PARAMS.getH());

    /**
     * Strategy 1: Cross-Validation against Bouncy Castle (The "Gold Standard").
     * <p>
     * We use Bouncy Castle's standard {@link ECDSASigner} configured with
     * {@link HMacDSAKCalculator}
     * (RFC 6979). Since both implementations are deterministic, they MUST produce
     * the exact same (r, s) values for every single input.
     */
    @Test
    void verifyAgainstBouncyCastleReference() {
        Random random = new Random(12345); // Fixed seed for reproducibility

        for (int i = 0; i < 1000; i++) {
            // 1. Generate random key and message
            byte[] privateKeyBytes = new byte[32];
            random.nextBytes(privateKeyBytes);
            BigInteger privateKeyInt = new BigInteger(1, privateKeyBytes);
            // Ensure valid key range [1, N-1]
            privateKeyInt = privateKeyInt.mod(CURVE.getN().subtract(BigInteger.ONE)).add(BigInteger.ONE);

            byte[] message = new byte[32]; // Random "hash"
            random.nextBytes(message);

            // 2. Sign with Brane's FastSigner
            Signature fastSig = FastSigner.sign(message, privateKeyInt);

            // 3. Sign with Bouncy Castle Standard Signer
            ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
            signer.init(true, new ECPrivateKeyParameters(privateKeyInt, CURVE));
            BigInteger[] standardSig = signer.generateSignature(message);

            BigInteger standardR = standardSig[0];
            BigInteger standardS = standardSig[1];

            // Normalize standard S to low-s (Bouncy Castle doesn't enforce this by default,
            // but Ethereum does)
            if (standardS.compareTo(CURVE.getN().shiftRight(1)) > 0) {
                standardS = CURVE.getN().subtract(standardS);
            }

            // 4. Assert Equality
            assertEquals(standardR, new BigInteger(1, fastSig.r()), "R value mismatch at iter " + i);
            assertEquals(standardS, new BigInteger(1, fastSig.s()), "S value mismatch at iter " + i);
        }
    }

    /**
     * Strategy 2: Property-Based Fuzz Testing (Round-Trip Recovery).
     * <p>
     * We generate a massive number of random keys and messages. For each, we sign
     * and then immediately attempt to recover the public key/address.
     * <p>
     * If the signature (r, s, v) is correct, the recovered address MUST match the
     * original address.
     * This verifies the 'v' calculation and the low-s normalization logic
     * implicitly.
     */
    @Test
    void fuzzTestRecovery() {
        Random random = new Random(67890);
        int iterations = 10_000; // 10k random tests

        for (int i = 0; i < iterations; i++) {
            // 1. Generate random key
            byte[] keyBytes = new byte[32];
            random.nextBytes(keyBytes);
            // Ensure valid key
            BigInteger keyInt = new BigInteger(1, keyBytes).mod(CURVE.getN());
            if (keyInt.equals(BigInteger.ZERO))
                continue;

            PrivateKey key = PrivateKey.fromBytes(toBytes32(keyInt));
            Address expectedAddress = key.toAddress();

            // 2. Generate random message hash
            byte[] messageHash = new byte[32];
            random.nextBytes(messageHash);

            // 3. Sign
            Signature sig = FastSigner.sign(messageHash, keyInt);

            // 4. Recover
            Address recovered = PrivateKey.recoverAddress(messageHash, sig);

            // 5. Assert
            assertEquals(expectedAddress, recovered, "Recovery failed for iteration " + i);

            // Verify Low-S property explicitly
            BigInteger s = new BigInteger(1, sig.s());
            assertTrue(s.compareTo(CURVE.getN().shiftRight(1)) <= 0, "Signature S must be in lower half");
        }
    }

    /**
     * Strategy 3: Concurrent Signing Test (Thread Safety).
     * <p>
     * Verifies that the static FixedPointCombMultiplier is thread-safe by
     * running many signing operations concurrently and verifying all signatures
     * are correct.
     */
    @Test
    void concurrentSigningIsThreadSafe() throws Exception {
        int threadCount = 50;
        int signaturesPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        // Use a fixed private key for consistency
        BigInteger privateKey = new BigInteger(
                "4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318", 16);
        PrivateKey key = PrivateKey.fromBytes(toBytes32(privateKey));
        Address expectedAddress = key.toAddress();

        // Launch threads that all sign different messages concurrently
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                startLatch.await(); // Wait for all threads to be ready
                Random random = new Random(threadId); // Different seed per thread

                for (int i = 0; i < signaturesPerThread; i++) {
                    byte[] messageHash = new byte[32];
                    random.nextBytes(messageHash);

                    // Sign using FastSigner (tests the shared MULTIPLIER)
                    Signature sig = FastSigner.sign(messageHash, privateKey);

                    // Verify via recovery
                    Address recovered = PrivateKey.recoverAddress(messageHash, sig);
                    if (!expectedAddress.equals(recovered)) {
                        return false;
                    }

                    // Verify low-S property
                    BigInteger s = new BigInteger(1, sig.s());
                    if (s.compareTo(CURVE.getN().shiftRight(1)) > 0) {
                        return false;
                    }
                }
                return true;
            }));
        }

        // Start all threads at once
        startLatch.countDown();

        // Verify all threads succeeded
        for (Future<Boolean> future : futures) {
            assertTrue(future.get(), "Concurrent signing produced invalid signature");
        }

        executor.shutdown();
    }

    private static byte[] toBytes32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == 32) {
            return bytes;
        }
        byte[] result = new byte[32];
        if (bytes.length > 32) {
            // Strip leading sign byte(s)
            System.arraycopy(bytes, bytes.length - 32, result, 0, 32);
        } else {
            // Pad with leading zeros
            System.arraycopy(bytes, 0, result, 32 - bytes.length, bytes.length);
        }
        return result;
    }
}
