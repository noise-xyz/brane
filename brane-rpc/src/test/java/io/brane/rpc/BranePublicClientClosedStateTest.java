package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.brane.core.chain.ChainProfiles;
import org.junit.jupiter.api.Test;

class BranePublicClientClosedStateTest {

    @Test
    void throwsIllegalStateExceptionAfterClose() {
        BranePublicClient client =
                BranePublicClient.forChain(ChainProfiles.ANVIL_LOCAL).build();

        client.close();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                client::getChainId);
        assertEquals("Client is closed", ex.getMessage());
    }

    @Test
    void closeIsIdempotent() {
        BranePublicClient client =
                BranePublicClient.forChain(ChainProfiles.ANVIL_LOCAL).build();

        // Should not throw on multiple close calls
        client.close();
        client.close();
        client.close();

        // Still throws after close
        assertThrows(IllegalStateException.class, client::getChainId);
    }
}
