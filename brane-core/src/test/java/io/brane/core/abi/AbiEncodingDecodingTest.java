// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.abi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;

class AbiEncodingDecodingTest {

        private static final String ERC20_ABI_JSON = """
                        [
                          {
                            "inputs": [{ "internalType": "address", "name": "account", "type": "address" }],
                            "name": "balanceOf",
                            "outputs": [{ "internalType": "uint256", "name": "", "type": "uint256" }],
                            "stateMutability": "view",
                            "type": "function"
                          },
                          {
                            "inputs": [],
                            "name": "decimals",
                            "outputs": [{ "internalType": "uint8", "name": "", "type": "uint8" }],
                            "stateMutability": "view",
                            "type": "function"
                          }
                        ]
                        """;

        private static final String SIMPLE_ABI_JSON = """
                        [
                          {
                            "inputs": [],
                            "name": "getNumber",
                            "outputs": [{ "internalType": "uint256", "name": "", "type": "uint256" }],
                            "stateMutability": "view",
                            "type": "function"
                          },
                          {
                            "inputs": [],
                            "name": "isActive",
                            "outputs": [{ "internalType": "bool", "name": "", "type": "bool" }],
                            "stateMutability": "view",
                            "type": "function"
                          },
                          {
                            "inputs": [],
                            "name": "owner",
                            "outputs": [{ "internalType": "address", "name": "", "type": "address" }],
                            "stateMutability": "view",
                            "type": "function"
                          },
                          {
                            "inputs": [],
                            "name": "greeting",
                            "outputs": [{ "internalType": "string", "name": "", "type": "string" }],
                            "stateMutability": "view",
                            "type": "function"
                          }
                        ]
                        """;

        @Test
        void encodesSelectorsForErc20Functions() {
                Abi abi = Abi.fromJson(ERC20_ABI_JSON);

                Address address = new Address("0x" + "1".repeat(40));
                Abi.FunctionCall balanceOfCall = abi.encodeFunction("balanceOf", address);

                String balanceOfSelector = Abi.functionSelector("balanceOf(address)").value();
                assertTrue(
                                balanceOfCall.data().startsWith(balanceOfSelector),
                                "balanceOf selector should prefix calldata");
                assertTrue(
                                balanceOfCall.data().length() > balanceOfSelector.length(),
                                "balanceOf calldata should include encoded args");

                Abi.FunctionCall decimalsCall = abi.encodeFunction("decimals");
                assertEquals(Abi.functionSelector("decimals()").value(), decimalsCall.data());
        }

        @Test
        void decodesSimpleOutputs() {
                Abi abi = Abi.fromJson(SIMPLE_ABI_JSON);

                // uint256(42)
                String uintRaw = io.brane.primitives.Hex.encode(
                                AbiEncoder.encode(
                                                java.util.List.of(new UInt(256,
                                                                BigInteger.valueOf(42)))));
                BigInteger decodedUint = abi.encodeFunction("getNumber").decode(uintRaw, BigInteger.class);
                assertEquals(BigInteger.valueOf(42), decodedUint);

                // bool(true)
                String boolRaw = io.brane.primitives.Hex.encode(
                                AbiEncoder.encode(
                                                java.util.List.of(new Bool(true))));
                Boolean decodedBool = abi.encodeFunction("isActive").decode(boolRaw, Boolean.class);
                assertTrue(decodedBool);

                // address
                Address expected = new Address("0x" + "2".repeat(40));
                String addressRaw = io.brane.primitives.Hex.encode(
                                AbiEncoder.encode(
                                                java.util.List.of(new AddressType(expected))));
                Address decodedAddress = abi.encodeFunction("owner").decode(addressRaw, Address.class);
                assertEquals(expected, decodedAddress);

                // string("hello")
                String stringRaw = io.brane.primitives.Hex.encode(
                                AbiEncoder.encode(
                                                java.util.List.of(new Utf8String("hello"))));
                String decodedString = abi.encodeFunction("greeting").decode(stringRaw, String.class);
                assertEquals("hello", decodedString);
        }

        @Test
        void hashesEventSignatures() {
                Hash transferTopic = Abi.eventTopic("Transfer(address,address,uint256)");
                assertEquals(
                                "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                                transferTopic.value());

                HexData approvalSelector = Abi.functionSelector("approve(address,uint256)");
                assertEquals("0x095ea7b3", approvalSelector.value());
        }
}
