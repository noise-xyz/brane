// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.crypto.eip712;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;

class Eip712DomainTest {

    private static final Address VERIFYING_CONTRACT =
            new Address("0x1234567890123456789012345678901234567890");
    private static final Hash SALT =
            new Hash("0xabcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // ═══════════════════════════════════════════════════════════════
    // Builder Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class BuilderTests {

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
        void builder_chainId_supportsLargeValues() {
            // Arbitrum chain ID is quite large
            var domain = Eip712Domain.builder()
                    .chainId(42161)
                    .build();

            assertEquals(42161L, domain.chainId());
        }

        @Test
        void builder_chainId_supportsMaxLong() {
            var domain = Eip712Domain.builder()
                    .chainId(Long.MAX_VALUE)
                    .build();

            assertEquals(Long.MAX_VALUE, domain.chainId());
        }

        @Test
        void builder_versionOnly() {
            var domain = Eip712Domain.builder()
                    .version("2.0.0")
                    .build();

            assertNull(domain.name());
            assertEquals("2.0.0", domain.version());
            assertNull(domain.chainId());
        }

        @Test
        void builder_verifyingContractOnly() {
            var domain = Eip712Domain.builder()
                    .verifyingContract(VERIFYING_CONTRACT)
                    .build();

            assertNull(domain.name());
            assertEquals(VERIFYING_CONTRACT, domain.verifyingContract());
        }

        @Test
        void builder_saltOnly() {
            var domain = Eip712Domain.builder()
                    .salt(SALT)
                    .build();

            assertNull(domain.name());
            assertEquals(SALT, domain.salt());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Partial Domain Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class PartialDomainTests {

        @Test
        void partialDomain_nameVersionChainId() {
            var domain = Eip712Domain.builder()
                    .name("MyDapp")
                    .version("1")
                    .chainId(1)
                    .build();

            assertEquals("MyDapp", domain.name());
            assertEquals("1", domain.version());
            assertEquals(1L, domain.chainId());
            assertNull(domain.verifyingContract());
            assertNull(domain.salt());
        }

        @Test
        void partialDomain_nameVersionContract() {
            var domain = Eip712Domain.builder()
                    .name("MyDapp")
                    .version("1")
                    .verifyingContract(VERIFYING_CONTRACT)
                    .build();

            assertEquals("MyDapp", domain.name());
            assertEquals("1", domain.version());
            assertNull(domain.chainId());
            assertEquals(VERIFYING_CONTRACT, domain.verifyingContract());
        }

        @Test
        void partialDomain_chainIdAndContract() {
            var domain = Eip712Domain.builder()
                    .chainId(137)
                    .verifyingContract(VERIFYING_CONTRACT)
                    .build();

            assertNull(domain.name());
            assertNull(domain.version());
            assertEquals(137L, domain.chainId());
            assertEquals(VERIFYING_CONTRACT, domain.verifyingContract());
        }

        @Test
        void partialDomain_saltWithNameVersion() {
            var domain = Eip712Domain.builder()
                    .name("SaltedApp")
                    .version("1")
                    .salt(SALT)
                    .build();

            assertEquals("SaltedApp", domain.name());
            assertEquals("1", domain.version());
            assertNull(domain.chainId());
            assertNull(domain.verifyingContract());
            assertEquals(SALT, domain.salt());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Constructor Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class ConstructorTests {

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
        void constructor_allNull() {
            var domain = new Eip712Domain(null, null, null, null, null);

            assertNull(domain.name());
            assertNull(domain.version());
            assertNull(domain.chainId());
            assertNull(domain.verifyingContract());
            assertNull(domain.salt());
        }

        @Test
        void constructor_allFields() {
            var domain = new Eip712Domain("Full", "3", 1L, VERIFYING_CONTRACT, SALT);

            assertEquals("Full", domain.name());
            assertEquals("3", domain.version());
            assertEquals(1L, domain.chainId());
            assertEquals(VERIFYING_CONTRACT, domain.verifyingContract());
            assertEquals(SALT, domain.salt());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // separator() Tests - Domain Separator Computation
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class SeparatorTests {

        @Test
        void separator_returnsCorrectHash() {
            var domain = Eip712Domain.builder()
                    .name("Test")
                    .version("1")
                    .build();

            Hash separator = domain.separator();

            assertNotNull(separator);
            assertEquals(32, separator.toBytes().length);
            // Verify it matches the direct TypedDataEncoder call
            assertEquals(TypedDataEncoder.hashDomain(domain), separator);
        }

        // Additional tests use TypedDataEncoder.hashDomain() directly for thorough coverage
        @Test
        void hashDomain_fullDomain_eip712SpecVector() {
            // From EIP-712 spec example:
            // domain = { name: "Ether Mail", version: "1", chainId: 1, verifyingContract: 0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC }
            // domainSeparator = 0xf2cee375fa42b42143804025fc449deafd50cc031ca257e0b194a650a912090f
            var domain = Eip712Domain.builder()
                    .name("Ether Mail")
                    .version("1")
                    .chainId(1)
                    .verifyingContract(new Address("0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"))
                    .build();

            Hash domainSeparator = TypedDataEncoder.hashDomain(domain);

            assertEquals("0xf2cee375fa42b42143804025fc449deafd50cc031ca257e0b194a650a912090f",
                    domainSeparator.value());
        }

        @Test
        void hashDomain_minimalDomain_consistentHash() {
            var domain = Eip712Domain.builder()
                    .name("Test")
                    .version("1")
                    .build();

            Hash hash1 = TypedDataEncoder.hashDomain(domain);
            Hash hash2 = TypedDataEncoder.hashDomain(domain);

            assertEquals(hash1, hash2);
            assertEquals(32, hash1.toBytes().length);
        }

        @Test
        void hashDomain_emptyDomain_producesValidHash() {
            var domain = Eip712Domain.builder().build();

            Hash hash = TypedDataEncoder.hashDomain(domain);

            assertNotNull(hash);
            assertEquals(32, hash.toBytes().length);
        }

        @Test
        void hashDomain_differentNames_differentHash() {
            var domain1 = Eip712Domain.builder().name("App1").version("1").build();
            var domain2 = Eip712Domain.builder().name("App2").version("1").build();

            assertNotEquals(
                    TypedDataEncoder.hashDomain(domain1),
                    TypedDataEncoder.hashDomain(domain2)
            );
        }

        @Test
        void hashDomain_differentVersions_differentHash() {
            var domain1 = Eip712Domain.builder().name("App").version("1").build();
            var domain2 = Eip712Domain.builder().name("App").version("2").build();

            assertNotEquals(
                    TypedDataEncoder.hashDomain(domain1),
                    TypedDataEncoder.hashDomain(domain2)
            );
        }

        @Test
        void hashDomain_differentChainIds_differentHash() {
            var domain1 = Eip712Domain.builder().name("Test").version("1").chainId(1).build();
            var domain2 = Eip712Domain.builder().name("Test").version("1").chainId(137).build();

            assertNotEquals(
                    TypedDataEncoder.hashDomain(domain1),
                    TypedDataEncoder.hashDomain(domain2)
            );
        }

        @Test
        void hashDomain_differentContracts_differentHash() {
            var domain1 = Eip712Domain.builder()
                    .name("Test")
                    .verifyingContract(new Address("0x1111111111111111111111111111111111111111"))
                    .build();
            var domain2 = Eip712Domain.builder()
                    .name("Test")
                    .verifyingContract(new Address("0x2222222222222222222222222222222222222222"))
                    .build();

            assertNotEquals(
                    TypedDataEncoder.hashDomain(domain1),
                    TypedDataEncoder.hashDomain(domain2)
            );
        }

        @Test
        void hashDomain_withSalt_differentFromWithoutSalt() {
            var domainNoSalt = Eip712Domain.builder()
                    .name("Test")
                    .version("1")
                    .build();

            var domainWithSalt = Eip712Domain.builder()
                    .name("Test")
                    .version("1")
                    .salt(new Hash("0x0000000000000000000000000000000000000000000000000000000000000001"))
                    .build();

            assertNotEquals(
                    TypedDataEncoder.hashDomain(domainNoSalt),
                    TypedDataEncoder.hashDomain(domainWithSalt)
            );
        }

        @Test
        void hashDomain_differentSalts_differentHash() {
            var domain1 = Eip712Domain.builder()
                    .name("Test")
                    .salt(new Hash("0x0000000000000000000000000000000000000000000000000000000000000001"))
                    .build();
            var domain2 = Eip712Domain.builder()
                    .name("Test")
                    .salt(new Hash("0x0000000000000000000000000000000000000000000000000000000000000002"))
                    .build();

            assertNotEquals(
                    TypedDataEncoder.hashDomain(domain1),
                    TypedDataEncoder.hashDomain(domain2)
            );
        }

        @Test
        void hashDomain_nameOnly_producesValidHash() {
            var domain = Eip712Domain.builder()
                    .name("SimpleApp")
                    .build();

            Hash hash = TypedDataEncoder.hashDomain(domain);

            assertNotNull(hash);
            assertEquals(32, hash.toBytes().length);
        }

        @Test
        void hashDomain_chainIdOnly_producesValidHash() {
            var domain = Eip712Domain.builder()
                    .chainId(1)
                    .build();

            Hash hash = TypedDataEncoder.hashDomain(domain);

            assertNotNull(hash);
            assertEquals(32, hash.toBytes().length);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // JSON Serialization Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class JsonSerializationTests {

        @Test
        void json_roundTrip_fullDomain() throws JsonProcessingException {
            var domain = Eip712Domain.builder()
                    .name("MyDApp")
                    .version("1")
                    .chainId(1)
                    .verifyingContract(VERIFYING_CONTRACT)
                    .salt(SALT)
                    .build();

            String json = MAPPER.writeValueAsString(domain);
            Eip712Domain parsed = MAPPER.readValue(json, Eip712Domain.class);

            assertEquals(domain, parsed);
        }

        @Test
        void json_roundTrip_partialDomain() throws JsonProcessingException {
            var domain = Eip712Domain.builder()
                    .name("TestApp")
                    .version("1")
                    .build();

            String json = MAPPER.writeValueAsString(domain);
            Eip712Domain parsed = MAPPER.readValue(json, Eip712Domain.class);

            assertEquals(domain, parsed);
        }

        @Test
        void json_roundTrip_emptyDomain() throws JsonProcessingException {
            var domain = Eip712Domain.builder().build();

            String json = MAPPER.writeValueAsString(domain);
            Eip712Domain parsed = MAPPER.readValue(json, Eip712Domain.class);

            assertEquals(domain, parsed);
        }

        @Test
        void json_serialize_fullDomain_containsAllFields() throws JsonProcessingException {
            var domain = Eip712Domain.builder()
                    .name("MyDApp")
                    .version("1")
                    .chainId(1)
                    .verifyingContract(VERIFYING_CONTRACT)
                    .salt(SALT)
                    .build();

            String json = MAPPER.writeValueAsString(domain);

            assertTrue(json.contains("\"name\""));
            assertTrue(json.contains("\"MyDApp\""));
            assertTrue(json.contains("\"version\""));
            assertTrue(json.contains("\"chainId\""));
            assertTrue(json.contains("\"verifyingContract\""));
            assertTrue(json.contains("\"salt\""));
        }

        @Test
        void json_deserialize_fromStandardFormat() throws JsonProcessingException {
            String json = """
                {
                    "name": "Ether Mail",
                    "version": "1",
                    "chainId": 1,
                    "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"
                }
                """;

            Eip712Domain domain = MAPPER.readValue(json, Eip712Domain.class);

            assertEquals("Ether Mail", domain.name());
            assertEquals("1", domain.version());
            assertEquals(1L, domain.chainId());
            assertEquals(new Address("0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"), domain.verifyingContract());
            assertNull(domain.salt());
        }

        @Test
        void json_deserialize_partialFields() throws JsonProcessingException {
            String json = """
                {
                    "name": "SimpleApp",
                    "chainId": 137
                }
                """;

            Eip712Domain domain = MAPPER.readValue(json, Eip712Domain.class);

            assertEquals("SimpleApp", domain.name());
            assertNull(domain.version());
            assertEquals(137L, domain.chainId());
            assertNull(domain.verifyingContract());
            assertNull(domain.salt());
        }

        @Test
        void json_deserialize_emptyObject() throws JsonProcessingException {
            String json = "{}";

            Eip712Domain domain = MAPPER.readValue(json, Eip712Domain.class);

            assertNull(domain.name());
            assertNull(domain.version());
            assertNull(domain.chainId());
            assertNull(domain.verifyingContract());
            assertNull(domain.salt());
        }

        @Test
        void json_deserialize_withSalt() throws JsonProcessingException {
            String json = """
                {
                    "name": "SaltedApp",
                    "salt": "0xabcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
                }
                """;

            Eip712Domain domain = MAPPER.readValue(json, Eip712Domain.class);

            assertEquals("SaltedApp", domain.name());
            assertEquals(SALT, domain.salt());
        }

        @Test
        void json_deserialize_largeChainId() throws JsonProcessingException {
            String json = """
                {
                    "name": "LargeChain",
                    "chainId": 42161
                }
                """;

            Eip712Domain domain = MAPPER.readValue(json, Eip712Domain.class);

            assertEquals(42161L, domain.chainId());
        }

        @Test
        void json_deserialize_ignoresUnknownFields() throws JsonProcessingException {
            String json = """
                {
                    "name": "Test",
                    "unknownField": "should be ignored",
                    "version": "1"
                }
                """;

            Eip712Domain domain = MAPPER.readValue(json, Eip712Domain.class);

            assertEquals("Test", domain.name());
            assertEquals("1", domain.version());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Equality and HashCode Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class EqualityTests {

        @Test
        void equality_sameDomains() {
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
        void equality_emptyDomains() {
            var domain1 = Eip712Domain.builder().build();
            var domain2 = Eip712Domain.builder().build();

            assertEquals(domain1, domain2);
            assertEquals(domain1.hashCode(), domain2.hashCode());
        }

        @Test
        void equality_fullDomains() {
            var domain1 = new Eip712Domain("App", "1", 1L, VERIFYING_CONTRACT, SALT);
            var domain2 = new Eip712Domain("App", "1", 1L, VERIFYING_CONTRACT, SALT);

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
        void inequality_differentVersion() {
            var domain1 = Eip712Domain.builder().name("App").version("1").build();
            var domain2 = Eip712Domain.builder().name("App").version("2").build();

            assertNotEquals(domain1, domain2);
        }

        @Test
        void inequality_differentChainId() {
            var domain1 = Eip712Domain.builder().chainId(1).build();
            var domain2 = Eip712Domain.builder().chainId(2).build();

            assertNotEquals(domain1, domain2);
        }

        @Test
        void inequality_differentContract() {
            var domain1 = Eip712Domain.builder()
                    .verifyingContract(new Address("0x1111111111111111111111111111111111111111"))
                    .build();
            var domain2 = Eip712Domain.builder()
                    .verifyingContract(new Address("0x2222222222222222222222222222222222222222"))
                    .build();

            assertNotEquals(domain1, domain2);
        }

        @Test
        void inequality_differentSalt() {
            var domain1 = Eip712Domain.builder()
                    .salt(new Hash("0x0000000000000000000000000000000000000000000000000000000000000001"))
                    .build();
            var domain2 = Eip712Domain.builder()
                    .salt(new Hash("0x0000000000000000000000000000000000000000000000000000000000000002"))
                    .build();

            assertNotEquals(domain1, domain2);
        }

        @Test
        void inequality_nullVsPresent() {
            var domain1 = Eip712Domain.builder().name("App").build();
            var domain2 = Eip712Domain.builder().name("App").version("1").build();

            assertNotEquals(domain1, domain2);
        }

        @Test
        void inequality_presentVsNull() {
            var domain1 = Eip712Domain.builder().name("App").chainId(1).build();
            var domain2 = Eip712Domain.builder().name("App").build();

            assertNotEquals(domain1, domain2);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // toString Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class ToStringTests {

        @Test
        void toString_containsFieldValues() {
            var domain = Eip712Domain.builder()
                    .name("MyApp")
                    .version("1")
                    .chainId(1)
                    .build();

            String str = domain.toString();

            assertTrue(str.contains("MyApp"));
            assertTrue(str.contains("1"));
        }

        @Test
        void toString_handlesNullFields() {
            var domain = Eip712Domain.builder().build();

            String str = domain.toString();

            // Should not throw and should contain the record name
            assertNotNull(str);
            assertTrue(str.contains("Eip712Domain"));
        }
    }
}
