package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.brane.core.chain.ChainProfile;
import io.brane.core.chain.ChainProfiles;

class BranePublicClientBuilderTest {

    @Test
    void buildsClientFromProfileWithDefaultRpcUrl() {
        BranePublicClient client =
                BranePublicClient.forChain(ChainProfiles.ANVIL_LOCAL).build();

        assertNotNull(client);
        assertEquals(ChainProfiles.ANVIL_LOCAL.chainId(), client.profile().chainId());
    }

    @Test
    void failsWhenNoRpcUrlConfigured() {
        ChainProfile profileWithoutUrl = ChainProfile.of(1234L, null, true, null);

        BranePublicClient.Builder builder = BranePublicClient.forChain(profileWithoutUrl);

        IllegalStateException ex = assertThrows(IllegalStateException.class, builder::build);
        assertTrue(ex.getMessage().contains("No RPC URL configured"));
    }

    @Test
    void allowsRpcUrlOverride() {
        BranePublicClient client =
                BranePublicClient.forChain(ChainProfiles.ETH_SEPOLIA)
                        .withRpcUrl("https://example.org")
                        .build();

        assertNotNull(client);
        assertEquals(ChainProfiles.ETH_SEPOLIA.chainId(), client.profile().chainId());
    }
}
