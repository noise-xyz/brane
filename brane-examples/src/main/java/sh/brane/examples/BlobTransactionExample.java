// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.examples;

import java.math.BigDecimal;
import java.util.Arrays;

import sh.brane.core.AnsiColors;
import sh.brane.core.builder.Eip4844Builder;
import sh.brane.core.crypto.Kzg;
import sh.brane.core.crypto.PrivateKeySigner;
import sh.brane.core.model.BlobTransactionRequest;
import sh.brane.core.model.TransactionReceipt;
import sh.brane.core.tx.BlobDecoder;
import sh.brane.core.tx.SidecarBuilder;
import sh.brane.core.types.Address;
import sh.brane.core.types.BlobSidecar;
import sh.brane.core.types.Wei;
import sh.brane.kzg.CKzg;
import sh.brane.rpc.Brane;

/**
 * Example demonstrating EIP-4844 blob transactions with Brane SDK.
 *
 * <p>This example covers:
 * <ul>
 *   <li>Loading KZG trusted setup using {@link CKzg}</li>
 *   <li>Building blob transactions with {@link Eip4844Builder#blobData(byte[])}</li>
 *   <li>Sending blob transactions and waiting for confirmation</li>
 *   <li>Round-trip verification: encoding data into blobs and decoding it back</li>
 *   <li>Sidecar reuse for fee bumping (replacement transactions)</li>
 * </ul>
 *
 * <p><strong>Prerequisites:</strong> Anvil must be running with Cancun fork:
 * <pre>
 * anvil --hardfork cancun
 * </pre>
 *
 * <p>Usage:
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *     -PmainClass=sh.brane.examples.BlobTransactionExample \
 *     -Dbrane.examples.rpc=http://127.0.0.1:8545
 * </pre>
 *
 * @see Eip4844Builder
 * @see BlobTransactionRequest
 * @see CKzg
 */
public final class BlobTransactionExample {

    private BlobTransactionExample() {
        // Prevent instantiation
    }

    public static void main(final String[] args) {
        System.out.println("=== EIP-4844 Blob Transaction Example ===\n");

        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
        // Anvil Default Account #0
        final String privateKey = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
        final Address recipient = new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");

        final PrivateKeySigner signer = new PrivateKeySigner(privateKey);

        try (Brane.Tester tester = Brane.connectTest(rpcUrl, signer)) {
            // ---------------------------------------------------------
            // 1. Load KZG Trusted Setup
            // ---------------------------------------------------------
            System.out.println("[1] Loading KZG Trusted Setup...");
            System.out.println("    Using CKzg.loadFromClasspath() to load bundled setup");

            // CKzg uses a global native setup that remains in memory for the JVM lifetime.
            // No cleanup is needed - the Kzg interface does not extend AutoCloseable.
            Kzg kzg = CKzg.loadFromClasspath();

            System.out.println(AnsiColors.success("KZG trusted setup loaded successfully"));

            // ---------------------------------------------------------
            // 2. Build and Send Blob Transaction using blobData()
            // ---------------------------------------------------------
            System.out.println("\n[2] Building Blob Transaction with blobData()...");

            byte[] originalData = "Hello, EIP-4844 blobs! This is test data for Brane SDK.".getBytes();
            System.out.println("    Original data: \"" + new String(originalData) + "\"");
            System.out.println("    Data size: " + originalData.length + " bytes");

            BlobTransactionRequest request = Eip4844Builder.create()
                    .to(recipient)
                    .value(Wei.fromEther(new BigDecimal("0.001")))
                    .blobData(originalData)
                    .build(kzg);

            System.out.println("    Blob count: " + request.sidecar().size());
            System.out.println("    Versioned hashes: " + request.blobVersionedHashes().size());

            System.out.println("\n    Sending blob transaction...");
            TransactionReceipt receipt = tester.asSigner().sendBlobTransactionAndWait(request);

            System.out.println(AnsiColors.success("Blob transaction confirmed!"));
            System.out.println("    Transaction hash: " + receipt.transactionHash().value());
            System.out.println("    Block number: " + receipt.blockNumber());
            System.out.println("    Gas used: " + receipt.cumulativeGasUsed().value());

            // ---------------------------------------------------------
            // 3. Round-Trip Decode Verification
            // ---------------------------------------------------------
            System.out.println("\n[3] Round-Trip Decode Verification...");
            System.out.println("    Decoding data from blobs to verify integrity...");

            byte[] decodedData = BlobDecoder.decode(request.sidecar().blobs());

            boolean dataMatches = Arrays.equals(originalData, decodedData);
            if (dataMatches) {
                System.out.println(AnsiColors.success("Round-trip verification successful!"));
                System.out.println("    Decoded data: \"" + new String(decodedData) + "\"");
            } else {
                System.err.println("Round-trip verification FAILED!");
                System.err.println("    Original: " + Arrays.toString(originalData));
                System.err.println("    Decoded: " + Arrays.toString(decodedData));
            }

            // ---------------------------------------------------------
            // 4. Sidecar Reuse for Fee Bumping
            // ---------------------------------------------------------
            System.out.println("\n[4] Sidecar Reuse for Fee Bumping...");
            System.out.println("    Demonstrating how to reuse a sidecar for replacement transactions");

            // Build a sidecar that can be reused
            byte[] feeBumpData = "Fee bump test data".getBytes();
            BlobSidecar reusableSidecar = SidecarBuilder.from(feeBumpData).build(kzg);
            System.out.println("    Created reusable sidecar with " + reusableSidecar.size() + " blob(s)");

            // First transaction with the sidecar (low fees)
            BlobTransactionRequest lowFeeTx = Eip4844Builder.create()
                    .to(recipient)
                    .sidecar(reusableSidecar)
                    .maxFeePerGas(Wei.gwei(10))
                    .maxPriorityFeePerGas(Wei.gwei(1))
                    .maxFeePerBlobGas(Wei.gwei(5))
                    .build();

            System.out.println("\n    Sending initial transaction with low fees...");
            TransactionReceipt lowFeeReceipt = tester.asSigner().sendBlobTransactionAndWait(lowFeeTx);
            System.out.println(AnsiColors.success("Initial transaction confirmed: " + lowFeeReceipt.transactionHash().value()));

            // Demonstrate that the same sidecar can be reused for a new transaction
            // (In production, you'd use the same nonce for fee bumping)
            BlobTransactionRequest higherFeeTx = Eip4844Builder.create()
                    .to(recipient)
                    .sidecar(reusableSidecar) // Reusing the same sidecar
                    .maxFeePerGas(Wei.gwei(50))
                    .maxPriorityFeePerGas(Wei.gwei(5))
                    .maxFeePerBlobGas(Wei.gwei(20))
                    .build();

            System.out.println("\n    Sending second transaction with same sidecar (higher fees)...");
            TransactionReceipt higherFeeReceipt = tester.asSigner().sendBlobTransactionAndWait(higherFeeTx);
            System.out.println(AnsiColors.success("Second transaction confirmed: " + higherFeeReceipt.transactionHash().value()));

            // Verify the sidecar produces the same versioned hashes
            boolean hashesMatch = lowFeeTx.blobVersionedHashes().equals(higherFeeTx.blobVersionedHashes());
            if (hashesMatch) {
                System.out.println(AnsiColors.success("Versioned hashes match - sidecar reuse successful!"));
            } else {
                System.err.println("Versioned hashes mismatch!");
            }

            // ---------------------------------------------------------
            // 5. KZG Proof Validation
            // ---------------------------------------------------------
            System.out.println("\n[5] KZG Proof Validation...");
            System.out.println("    Validating KZG proofs for the sidecar...");

            reusableSidecar.validate(kzg);
            System.out.println(AnsiColors.success("All KZG proofs validated successfully!"));

            System.out.println(AnsiColors.success("\nAll blob transaction operations completed successfully!"));

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
