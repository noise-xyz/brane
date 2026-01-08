package io.brane.core.crypto.eip712;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;

class Eip712DomainTest {

    private static final Address VERIFYING_CONTRACT =
            new Address("0x1234567890123456789012345678901234567890");
    private static final Hash SALT =
            new Hash("0xabcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");

    @Test
    void builder_allFields() {
        var domain = Eip712Domain.builder()
                .name("MyDApp")
                .version("1")
                .chainId(1)
                .verifyingContract(VERIFYING_CONTRACT)
                .salt(SALT)
                .build();

        assertEquals("MyDApp", domain.name());
        assertEquals("1", domain.version());
        assertEquals(1L, domain.chainId());
        assertEquals(VERIFYING_CONTRACT, domain.verifyingContract());
        assertEquals(SALT, domain.salt());
    }

    @Test
    void builder_minimalDomain_nameOnly() {
        var domain = Eip712Domain.builder()
                .name("SimpleApp")
                .build();

        assertEquals("SimpleApp", domain.name());
        assertNull(domain.version());
        assertNull(domain.chainId());
        assertNull(domain.verifyingContract());
        assertNull(domain.salt());
    }

    @Test
    void builder_nameAndChainId() {
        var domain = Eip712Domain.builder()
                .name("TestApp")
                .chainId(137)
                .build();

        assertEquals("TestApp", domain.name());
        assertNull(domain.version());
        assertEquals(137L, domain.chainId());
        assertNull(domain.verifyingContract());
        assertNull(domain.salt());
    }

    @Test
    void builder_emptyDomain() {
        var domain = Eip712Domain.builder().build();

        assertNull(domain.name());
        assertNull(domain.version());
        assertNull(domain.chainId());
        assertNull(domain.verifyingContract());
        assertNull(domain.salt());
    }

    @Test
    void constructor_direct() {
        var domain = new Eip712Domain("App", "2", 42L, VERIFYING_CONTRACT, null);

        assertEquals("App", domain.name());
        assertEquals("2", domain.version());
        assertEquals(42L, domain.chainId());
        assertEquals(VERIFYING_CONTRACT, domain.verifyingContract());
        assertNull(domain.salt());
    }

    @Test
    void builder_chainId_supportsLargeValues() {
        // Arbitrum chain ID is quite large
        var domain = Eip712Domain.builder()
                .chainId(42161)
                .build();

        assertEquals(42161L, domain.chainId());
    }

    @Test
    void separator_throwsUntilImplemented() {
        var domain = Eip712Domain.builder()
                .name("Test")
                .version("1")
                .build();

        var ex = assertThrows(UnsupportedOperationException.class, domain::separator);
        assertTrue(ex.getMessage().contains("TypedDataEncoder"));
    }

    @Test
    void equality() {
        var domain1 = Eip712Domain.builder()
                .name("App")
                .version("1")
                .chainId(1)
                .build();

        var domain2 = Eip712Domain.builder()
                .name("App")
                .version("1")
                .chainId(1)
                .build();

        assertEquals(domain1, domain2);
        assertEquals(domain1.hashCode(), domain2.hashCode());
    }

    @Test
    void inequality_differentName() {
        var domain1 = Eip712Domain.builder().name("App1").build();
        var domain2 = Eip712Domain.builder().name("App2").build();

        assertNotEquals(domain1, domain2);
    }

    @Test
    void inequality_nullVsPresent() {
        var domain1 = Eip712Domain.builder().name("App").build();
        var domain2 = Eip712Domain.builder().name("App").version("1").build();

        assertNotEquals(domain1, domain2);
    }
}
