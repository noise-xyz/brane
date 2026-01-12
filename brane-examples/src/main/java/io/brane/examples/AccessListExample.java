// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.examples;

import java.util.List;

import io.brane.core.builder.TxBuilder;
import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.model.AccessListEntry;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.Wei;
import io.brane.rpc.Brane;

/**
 * Sends an EIP-1559 transaction with a small access list and prints the result.
 */
public final class AccessListExample {

    private AccessListExample() {}

    public static void main(final String[] args) {
        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
        final String privateKey = System.getProperty("brane.examples.pk");

        if (privateKey == null || privateKey.isBlank()) {
            System.err.println("Error: -Dbrane.examples.pk must be set to a funded private key");
            System.exit(1);
        }

        final PrivateKeySigner signer = new PrivateKeySigner(privateKey);
        final Brane.Signer client = Brane.connect(rpcUrl, signer);

        final Address target = signer.address();
        final List<AccessListEntry> accessList =
                List.of(new AccessListEntry(target, List.of(new Hash("0x" + "0".repeat(64)))));

        final TransactionRequest request =
                TxBuilder.eip1559()
                        .to(target)
                        .value(Wei.of(0))
                        .accessList(accessList)
                        .build();

        System.out.println("Sending EIP-1559 transaction with access list...");
        final TransactionReceipt receipt = client.sendTransactionAndWait(request, 15_000, 1_000);
        System.out.println("Tx hash: " + receipt.transactionHash().value());
        System.out.println("Access list set: " + !request.accessListOrEmpty().isEmpty());
    }
}
