package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.crypto.Signer;

/**
 * Unit tests for {@link Brane} static factory methods.
 */
@ExtendWith(MockitoExtension.class)
class BraneTest {

    @Mock
    private BraneProvider provider;

    // Anvil default test private key
    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    @Test
    void connectWithUrlReturnsReader() {
        // When we call connect with just a URL, we get a Reader
        // We use the builder with a mock provider to avoid actual network calls
        Brane.Reader reader = Brane.builder()
                .provider(provider)
                .buildReader();

        assertNotNull(reader);
        assertInstanceOf(Brane.Reader.class, reader);
        assertFalse(reader.canSign());
    }

    @Test
    void connectWithUrlAndSignerReturnsSigner() {
        // When we call connect with URL and signer, we get a Signer
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Signer client = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildSigner();

        assertNotNull(client);
        assertInstanceOf(Brane.Signer.class, client);
        assertTrue(client.canSign());
        assertEquals(signer, client.signer());
    }

    @Test
    void connectStaticFactoryReturnsReader() throws Exception {
        // Brane.connect(url) returns a Reader
        // We can't easily test the actual URL-based factory without network,
        // but we can test that the builder path works correctly
        try (Brane.Reader reader = Brane.builder().provider(provider).buildReader()) {
            assertNotNull(reader);
            assertFalse(reader.canSign());
        }
    }

    @Test
    void connectStaticFactoryWithSignerReturnsSigner() throws Exception {
        // Brane.connect(url, signer) returns a Signer
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        try (Brane.Signer client = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildSigner()) {
            assertNotNull(client);
            assertTrue(client.canSign());
        }
    }

    @Test
    void readerCannotSign() {
        Brane.Reader reader = Brane.builder()
                .provider(provider)
                .buildReader();

        assertFalse(reader.canSign());
        assertInstanceOf(Brane.Reader.class, reader);
    }

    @Test
    void signerCanSign() {
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Signer client = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildSigner();

        assertTrue(client.canSign());
    }

    @Test
    void canSubscribeReturnsFalseForHttpProvider() {
        // HTTP provider does not support subscriptions
        Brane.Reader reader = Brane.builder()
                .provider(provider)
                .buildReader();

        assertFalse(reader.canSubscribe());
    }

    @Test
    void patternMatchingWorksWithSealed() {
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);

        // Test pattern matching with Reader
        Brane reader = Brane.builder().provider(provider).buildReader();
        String readerResult = switch (reader) {
            case Brane.Reader r -> "reader";
            case Brane.Signer s -> "signer";
        };
        assertEquals("reader", readerResult);

        // Test pattern matching with Signer
        Brane signerClient = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildSigner();
        String signerResult = switch (signerClient) {
            case Brane.Reader r -> "reader";
            case Brane.Signer s -> "signer";
        };
        assertEquals("signer", signerResult);
    }

    @Test
    void chainIdDelegatesCorrectly() throws Exception {
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x1", null, "1");
        when(provider.send(eq("eth_chainId"), any())).thenReturn(response);

        Brane.Reader reader = Brane.builder()
                .provider(provider)
                .buildReader();

        assertEquals(java.math.BigInteger.ONE, reader.chainId());
    }

    @Test
    void chainIdDelegatesCorrectlyForSigner() throws Exception {
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x1", null, "1");
        when(provider.send(eq("eth_chainId"), any())).thenReturn(response);

        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Signer client = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildSigner();

        assertEquals(java.math.BigInteger.ONE, client.chainId());
    }
}
