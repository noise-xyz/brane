// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.examples;

import sh.brane.core.builder.TxBuilder;
import sh.brane.core.model.TransactionRequest;
import sh.brane.core.types.Address;
import sh.brane.core.types.HexData;
import sh.brane.core.types.Wei;

/** Simple demo showcasing the unified transaction builder API. */
public final class TxBuilderExample {

    private TxBuilderExample() {}

    public static void main(final String[] args) {
        TransactionRequest eip1559Tx =
                TxBuilder.eip1559()
                        .to(new Address("0x" + "1".repeat(40)))
                        .value(Wei.of(1_000_000_000_000_000_000L))
                        .maxFeePerGas(Wei.of(30_000_000_000L))
                        .maxPriorityFeePerGas(Wei.of(3_000_000_000L))
                        .data(HexData.EMPTY)
                        .build();

        TransactionRequest legacyTx =
                TxBuilder.legacy()
                        .to(new Address("0x" + "2".repeat(40)))
                        .value(Wei.of(500_000_000_000_000_000L))
                        .gasPrice(Wei.of(20_000_000_000L))
                        .gasLimit(21_000)
                        .build();

        System.out.println("EIP-1559 tx maxFeePerGas: " + eip1559Tx.maxFeePerGas());
        System.out.println("EIP-1559 tx maxPriorityFeePerGas: " + eip1559Tx.maxPriorityFeePerGas());
        System.out.println("Legacy tx gasPrice: " + legacyTx.gasPrice());
        System.out.println("Legacy tx gasLimit: " + legacyTx.gasLimit());
    }
}
