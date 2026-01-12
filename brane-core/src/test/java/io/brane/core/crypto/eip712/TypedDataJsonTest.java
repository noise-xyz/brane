// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.crypto.eip712;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.brane.core.error.Eip712Exception;
import io.brane.core.types.Address;

class TypedDataJsonTest {

    // ═══════════════════════════════════════════════════════════════
    // Valid JSON Parsing Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class ValidJsonTests {

        @Test
        void parse_validPermitJson_returnsPayload() {
            String json = """
                {
                    "domain": {
                        "name": "MyToken",
                        "version": "1",
                        "chainId": 1,
                        "verifyingContract": "0x1234567890123456789012345678901234567890"
                    },
                    "primaryType": "Permit",
                    "types": {
                        "Permit": [
                            {"name": "owner", "type": "address"},
                            {"name": "spender", "type": "address"},
                            {"name": "value", "type": "uint256"},
                            {"name": "nonce", "type": "uint256"},
                            {"name": "deadline", "type": "uint256"}
                        ]
                    },
                    "message": {
                        "owner": "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "spender": "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        "value": "1000",
                        "nonce": "0",
                        "deadline": "1234567890"
                    }
                }
                """;

            TypedDataPayload payload = TypedDataJson.parse(json);

            assertNotNull(payload);
            assertEquals("Permit", payload.primaryType());
            assertEquals("MyToken", payload.domain().name());
            assertEquals("1", payload.domain().version());
            assertEquals(1L, payload.domain().chainId());
            assertEquals(
                    new Address("0x1234567890123456789012345678901234567890"),
                    payload.domain().verifyingContract()
            );
            assertEquals(5, payload.types().get("Permit").size());
            assertEquals("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", payload.message().get("owner"));
        }

        @Test
        void parse_validMailJson_returnsPayload() {
            String json = """
                {
                    "domain": {
                        "name": "Ether Mail",
                        "version": "1",
                        "chainId": 1,
                        "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"
                    },
                    "primaryType": "Mail",
                    "types": {
                        "Mail": [
                            {"name": "from", "type": "address"},
                            {"name": "to", "type": "address"},
                            {"name": "contents", "type": "string"}
                        ]
                    },
                    "message": {
                        "from": "0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826",
                        "to": "0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB",
                        "contents": "Hello, Bob!"
                    }
                }
                """;

            TypedDataPayload payload = TypedDataJson.parse(json);

            assertNotNull(payload);
            assertEquals("Mail", payload.primaryType());
            assertEquals("Hello, Bob!", payload.message().get("contents"));
        }

        @Test
        void parse_minimalDomain_succeeds() {
            String json = """
                {
                    "domain": {
                        "name": "MinimalApp"
                    },
                    "primaryType": "Simple",
                    "types": {
                        "Simple": [
                            {"name": "value", "type": "uint256"}
                        ]
                    },
                    "message": {
                        "value": "42"
                    }
                }
                """;

            TypedDataPayload payload = TypedDataJson.parse(json);

            assertNotNull(payload);
            assertEquals("MinimalApp", payload.domain().name());
            assertNull(payload.domain().version());
            assertNull(payload.domain().chainId());
            assertNull(payload.domain().verifyingContract());
        }

        @Test
        void parse_emptyDomain_succeeds() {
            String json = """
                {
                    "domain": {},
                    "primaryType": "Simple",
                    "types": {
                        "Simple": [
                            {"name": "value", "type": "uint256"}
                        ]
                    },
                    "message": {
                        "value": "42"
                    }
                }
                """;

            TypedDataPayload payload = TypedDataJson.parse(json);

            assertNotNull(payload);
            assertNull(payload.domain().name());
            assertNull(payload.domain().version());
        }

        @Test
        void parse_nestedTypes_succeeds() {
            String json = """
                {
                    "domain": {
                        "name": "Nested"
                    },
                    "primaryType": "Order",
                    "types": {
                        "Order": [
                            {"name": "buyer", "type": "address"},
                            {"name": "item", "type": "Item"}
                        ],
                        "Item": [
                            {"name": "name", "type": "string"},
                            {"name": "price", "type": "uint256"}
                        ]
                    },
                    "message": {
                        "buyer": "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "item": {
                            "name": "Widget",
                            "price": "100"
                        }
                    }
                }
                """;

            TypedDataPayload payload = TypedDataJson.parse(json);

            assertNotNull(payload);
            assertEquals("Order", payload.primaryType());
            assertEquals(2, payload.types().size());
            assertTrue(payload.types().containsKey("Order"));
            assertTrue(payload.types().containsKey("Item"));
        }

        @Test
        void parse_ignoresUnknownFields() {
            String json = """
                {
                    "domain": {
                        "name": "Test",
                        "unknownField": "ignored"
                    },
                    "primaryType": "Simple",
                    "types": {
                        "Simple": [
                            {"name": "value", "type": "uint256"}
                        ]
                    },
                    "message": {
                        "value": "42"
                    },
                    "extraField": "also ignored"
                }
                """;

            TypedDataPayload payload = TypedDataJson.parse(json);

            assertNotNull(payload);
            assertEquals("Test", payload.domain().name());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Invalid JSON Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class InvalidJsonTests {

        @Test
        void parse_malformedJson_throwsEip712Exception() {
            String json = "{ invalid json }";

            Eip712Exception ex = assertThrows(Eip712Exception.class, () ->
                    TypedDataJson.parse(json));

            assertTrue(ex.getMessage().contains("Invalid EIP-712 JSON"));
        }

        @Test
        void parse_emptyString_throwsEip712Exception() {
            Eip712Exception ex = assertThrows(Eip712Exception.class, () ->
                    TypedDataJson.parse(""));

            assertTrue(ex.getMessage().contains("Invalid EIP-712 JSON"));
        }

        @Test
        void parse_nullString_throwsException() {
            assertThrows(Exception.class, () ->
                    TypedDataJson.parse(null));
        }

        @Test
        void parse_jsonArray_throwsEip712Exception() {
            String json = "[1, 2, 3]";

            Eip712Exception ex = assertThrows(Eip712Exception.class, () ->
                    TypedDataJson.parse(json));

            assertTrue(ex.getMessage().contains("Invalid EIP-712 JSON"));
        }

        @Test
        void parse_jsonPrimitive_throwsEip712Exception() {
            String json = "\"just a string\"";

            Eip712Exception ex = assertThrows(Eip712Exception.class, () ->
                    TypedDataJson.parse(json));

            assertTrue(ex.getMessage().contains("Invalid EIP-712 JSON"));
        }

        @Test
        void parse_unclosedBrace_throwsEip712Exception() {
            String json = """
                {
                    "domain": {
                        "name": "Test"
                """;

            Eip712Exception ex = assertThrows(Eip712Exception.class, () ->
                    TypedDataJson.parse(json));

            assertTrue(ex.getMessage().contains("Invalid EIP-712 JSON"));
        }

        @Test
        void parse_missingQuotes_throwsEip712Exception() {
            String json = """
                {
                    domain: {
                        name: "Test"
                    }
                }
                """;

            Eip712Exception ex = assertThrows(Eip712Exception.class, () ->
                    TypedDataJson.parse(json));

            assertTrue(ex.getMessage().contains("Invalid EIP-712 JSON"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Missing Required Fields Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class MissingFieldsTests {

        @Test
        void parse_missingPrimaryType_throwsException() {
            String json = """
                {
                    "domain": {
                        "name": "Test"
                    },
                    "types": {
                        "Simple": [
                            {"name": "value", "type": "uint256"}
                        ]
                    },
                    "message": {
                        "value": "42"
                    }
                }
                """;

            assertThrows(Exception.class, () ->
                    TypedDataJson.parse(json));
        }

        @Test
        void parse_nullPrimaryType_throwsException() {
            String json = """
                {
                    "domain": {
                        "name": "Test"
                    },
                    "primaryType": null,
                    "types": {
                        "Simple": [
                            {"name": "value", "type": "uint256"}
                        ]
                    },
                    "message": {
                        "value": "42"
                    }
                }
                """;

            assertThrows(Exception.class, () ->
                    TypedDataJson.parse(json));
        }

        @Test
        void parse_missingTypes_throwsException() {
            String json = """
                {
                    "domain": {
                        "name": "Test"
                    },
                    "primaryType": "Simple",
                    "message": {
                        "value": "42"
                    }
                }
                """;

            assertThrows(Exception.class, () ->
                    TypedDataJson.parse(json));
        }

        @Test
        void parse_missingMessage_throwsException() {
            String json = """
                {
                    "domain": {
                        "name": "Test"
                    },
                    "primaryType": "Simple",
                    "types": {
                        "Simple": [
                            {"name": "value", "type": "uint256"}
                        ]
                    }
                }
                """;

            assertThrows(Exception.class, () ->
                    TypedDataJson.parse(json));
        }

        @Test
        void parse_primaryTypeNotInTypes_throwsEip712Exception() {
            String json = """
                {
                    "domain": {
                        "name": "Test"
                    },
                    "primaryType": "NonExistent",
                    "types": {
                        "Simple": [
                            {"name": "value", "type": "uint256"}
                        ]
                    },
                    "message": {
                        "value": "42"
                    }
                }
                """;

            Eip712Exception ex = assertThrows(Eip712Exception.class, () ->
                    TypedDataJson.parse(json));

            assertTrue(ex.getMessage().contains("Primary type not found"));
        }

        @Test
        void parse_emptyTypes_withPrimaryType_throwsEip712Exception() {
            String json = """
                {
                    "domain": {
                        "name": "Test"
                    },
                    "primaryType": "Simple",
                    "types": {},
                    "message": {
                        "value": "42"
                    }
                }
                """;

            Eip712Exception ex = assertThrows(Eip712Exception.class, () ->
                    TypedDataJson.parse(json));

            assertTrue(ex.getMessage().contains("Primary type not found"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // parseAndValidate() Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class ParseAndValidateTests {

        @Test
        void parseAndValidate_validJson_returnsTypedData() {
            String json = """
                {
                    "domain": {
                        "name": "MyToken",
                        "version": "1",
                        "chainId": 1,
                        "verifyingContract": "0x1234567890123456789012345678901234567890"
                    },
                    "primaryType": "Permit",
                    "types": {
                        "Permit": [
                            {"name": "owner", "type": "address"},
                            {"name": "spender", "type": "address"},
                            {"name": "value", "type": "uint256"},
                            {"name": "nonce", "type": "uint256"},
                            {"name": "deadline", "type": "uint256"}
                        ]
                    },
                    "message": {
                        "owner": "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "spender": "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        "value": "1000",
                        "nonce": "0",
                        "deadline": "1234567890"
                    }
                }
                """;

            TypedData<?> typedData = TypedDataJson.parseAndValidate(json);

            assertNotNull(typedData);
            assertEquals("Permit", typedData.primaryType());
            assertNotNull(typedData.domain());
            assertEquals("MyToken", typedData.domain().name());
        }

        @Test
        void parseAndValidate_invalidJson_throwsEip712Exception() {
            String json = "{ invalid }";

            Eip712Exception ex = assertThrows(Eip712Exception.class, () ->
                    TypedDataJson.parseAndValidate(json));

            assertTrue(ex.getMessage().contains("Invalid EIP-712 JSON"));
        }

        @Test
        void parseAndValidate_missingPrimaryType_throwsException() {
            String json = """
                {
                    "domain": {},
                    "types": {"Simple": [{"name": "x", "type": "uint256"}]},
                    "message": {"x": "1"}
                }
                """;

            assertThrows(Exception.class, () ->
                    TypedDataJson.parseAndValidate(json));
        }

        @Test
        void parseAndValidate_producesHashableTypedData() {
            String json = """
                {
                    "domain": {
                        "name": "Test",
                        "version": "1",
                        "chainId": 1
                    },
                    "primaryType": "Message",
                    "types": {
                        "Message": [
                            {"name": "content", "type": "string"}
                        ]
                    },
                    "message": {
                        "content": "Hello, World!"
                    }
                }
                """;

            TypedData<?> typedData = TypedDataJson.parseAndValidate(json);

            assertNotNull(typedData.hash());
            assertEquals(32, typedData.hash().toBytes().length);
        }

        @Test
        void parseAndValidate_hashIsConsistent() {
            String json = """
                {
                    "domain": {
                        "name": "Test",
                        "version": "1",
                        "chainId": 1
                    },
                    "primaryType": "Message",
                    "types": {
                        "Message": [
                            {"name": "content", "type": "string"}
                        ]
                    },
                    "message": {
                        "content": "Hello"
                    }
                }
                """;

            TypedData<?> typedData1 = TypedDataJson.parseAndValidate(json);
            TypedData<?> typedData2 = TypedDataJson.parseAndValidate(json);

            assertEquals(typedData1.hash(), typedData2.hash());
        }
    }
}
