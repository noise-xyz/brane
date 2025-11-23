package io.brane.examples;

import io.brane.core.BraneDebug;
import io.brane.core.model.BlockHeader;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.PublicClient;

/**
 * Minimal example demonstrating debug logging hooks for RPC calls.
 */
public final class DebugExample {

    private DebugExample() {}

    public static void main(final String[] args) {
        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");

        BraneDebug.setEnabled(true);
        final PublicClient client = PublicClient.from(HttpBraneProvider.builder(rpcUrl).build());

        final BlockHeader latest = client.getLatestBlock();
        System.out.println("Latest block: " + (latest != null ? latest.number() : "<none>"));
    }
}
