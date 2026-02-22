// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import sh.brane.core.abi.Abi;
import sh.brane.core.abi.AbiEncoder;
import sh.brane.core.abi.Int;
import sh.brane.core.abi.UInt;
import sh.brane.core.abi.Utf8String;
import sh.brane.core.crypto.Keccak256;
import sh.brane.core.model.LogEntry;
import sh.brane.core.types.Address;
import sh.brane.core.types.Hash;
import sh.brane.core.types.HexData;
import sh.brane.primitives.Hex;

class AbiEventDecodingTest {

  private static final String ERC20_ABI = """
      [
        {
          "anonymous": false,
          "inputs": [
            { "indexed": true, "internalType": "address", "name": "from", "type": "address" },
            { "indexed": true, "internalType": "address", "name": "to", "type": "address" },
            { "indexed": false, "internalType": "uint256", "name": "value", "type": "uint256" }
          ],
          "name": "Transfer",
          "type": "event"
        }
      ]
      """;

  @Test
  void decodesTransferEvent() {
    Abi abi = Abi.fromJson(ERC20_ABI);
    Address from = new Address("0x" + "1".repeat(40));
    Address to = new Address("0x" + "2".repeat(40));
    BigInteger amount = BigInteger.valueOf(5);

    Hash topic0 = Abi.eventTopic("Transfer(address,address,uint256)");
    Hash topicFrom = new Hash("0x" + "0".repeat(24) + from.value().substring(2));
    Hash topicTo = new Hash("0x" + "0".repeat(24) + to.value().substring(2));
    String data = Hex.encode(
        AbiEncoder.encode(List.of(new UInt(256, amount))));

    LogEntry log = new LogEntry(
        new Address("0x" + "9".repeat(40)),
        new HexData(data),
        List.of(topic0, topicFrom, topicTo),
        null,  // blockHash can be null for pending logs
        new Hash("0x" + "c".repeat(64)),  // transactionHash is required
        0L,
        false);

    record Transfer(Address from, Address to, BigInteger value) {
    }

    List<Transfer> decoded = abi.decodeEvents("Transfer", List.of(log), Transfer.class);

    assertFalse(decoded.isEmpty());
    Transfer transfer = decoded.get(0);
    assertEquals(from, transfer.from());
    assertEquals(to, transfer.to());
    assertEquals(amount, transfer.value());
  }

  /**
   * Tests that indexed and non-indexed params are interleaved in ABI parameter order.
   *
   * <p>Registered(uint256 indexed agentId, string agentURI, address indexed owner)
   * has indexed params at positions 0 and 2 with a non-indexed param at position 1.
   * The record constructor must receive values in ABI order, not indexed-first order.
   */
  @Test
  void decodesEventWithMixedIndexedNonIndexedOrder() {
    String abi = """
        [
          {
            "anonymous": false,
            "inputs": [
              { "indexed": true, "internalType": "uint256", "name": "agentId", "type": "uint256" },
              { "indexed": false, "internalType": "string", "name": "agentURI", "type": "string" },
              { "indexed": true, "internalType": "address", "name": "owner", "type": "address" }
            ],
            "name": "Registered",
            "type": "event"
          }
        ]
        """;

    Abi parsed = Abi.fromJson(abi);
    BigInteger agentId = BigInteger.valueOf(42);
    String agentURI = "https://example.com/agent.json";
    Address owner = new Address("0x" + "a".repeat(40));

    Hash topic0 = Abi.eventTopic("Registered(uint256,string,address)");
    Hash topicAgentId = new Hash("0x" + String.format("%064x", agentId));
    Hash topicOwner = new Hash("0x" + "0".repeat(24) + owner.value().substring(2));
    String data = Hex.encode(
        AbiEncoder.encode(List.of(new Utf8String(agentURI))));

    LogEntry log = new LogEntry(
        new Address("0x" + "9".repeat(40)),
        new HexData(data),
        List.of(topic0, topicAgentId, topicOwner),
        null,
        new Hash("0x" + "c".repeat(64)),
        0L,
        false);

    record Registered(BigInteger agentId, String agentURI, Address owner) {
    }

    List<Registered> decoded = parsed.decodeEvents("Registered", List.of(log), Registered.class);

    assertFalse(decoded.isEmpty());
    Registered event = decoded.get(0);
    assertEquals(agentId, event.agentId());
    assertEquals(agentURI, event.agentURI());
    assertEquals(owner, event.owner());
  }

  /**
   * Tests that indexed dynamic types (string) are skipped from decoded values.
   *
   * <p>NewFeedback has an indexed string (indexedTag1) which is stored as keccak256 hash
   * in the topic. The original value is unrecoverable, so it must be skipped when
   * mapping to the event record.
   */
  @Test
  void decodesEventWithIndexedDynamicTypeSkipped() {
    String abi = """
        [
          {
            "anonymous": false,
            "inputs": [
              { "indexed": true, "internalType": "uint256", "name": "agentId", "type": "uint256" },
              { "indexed": true, "internalType": "address", "name": "clientAddress", "type": "address" },
              { "indexed": false, "internalType": "uint64", "name": "feedbackIndex", "type": "uint64" },
              { "indexed": false, "internalType": "int128", "name": "value", "type": "int128" },
              { "indexed": false, "internalType": "uint8", "name": "valueDecimals", "type": "uint8" },
              { "indexed": true, "internalType": "string", "name": "indexedTag1", "type": "string" },
              { "indexed": false, "internalType": "string", "name": "tag1", "type": "string" },
              { "indexed": false, "internalType": "string", "name": "tag2", "type": "string" },
              { "indexed": false, "internalType": "string", "name": "endpoint", "type": "string" },
              { "indexed": false, "internalType": "string", "name": "feedbackURI", "type": "string" },
              { "indexed": false, "internalType": "bytes32", "name": "feedbackHash", "type": "bytes32" }
            ],
            "name": "NewFeedback",
            "type": "event"
          }
        ]
        """;

    Abi parsed = Abi.fromJson(abi);
    BigInteger agentId = BigInteger.valueOf(7);
    Address client = new Address("0x" + "b".repeat(40));
    BigInteger feedbackIndex = BigInteger.valueOf(3);
    BigInteger value = BigInteger.valueOf(985);
    BigInteger valueDecimals = BigInteger.valueOf(2);
    String tag1 = "quality";
    String tag2 = "speed";
    String endpoint = "/api/chat";
    String feedbackURI = "https://example.com/feedback/3";
    byte[] feedbackHash = new byte[32];
    feedbackHash[0] = (byte) 0xAB;

    Hash topic0 = Abi.eventTopic(
        "NewFeedback(uint256,address,uint64,int128,uint8,string,string,string,string,string,bytes32)");
    Hash topicAgentId = new Hash("0x" + String.format("%064x", agentId));
    Hash topicClient = new Hash("0x" + "0".repeat(24) + client.value().substring(2));
    // Indexed string: topic is keccak256 of the string value
    Hash topicTag1 = new Hash(Hex.encode(
        Keccak256.hash(tag1.getBytes(StandardCharsets.UTF_8))));

    String data = Hex.encode(AbiEncoder.encode(List.of(
        new UInt(64, feedbackIndex),
        new Int(128, value),
        new UInt(8, valueDecimals),
        new Utf8String(tag1),
        new Utf8String(tag2),
        new Utf8String(endpoint),
        new Utf8String(feedbackURI),
        sh.brane.core.abi.Bytes.ofStatic(feedbackHash))));

    LogEntry log = new LogEntry(
        new Address("0x" + "9".repeat(40)),
        new HexData(data),
        List.of(topic0, topicAgentId, topicClient, topicTag1),
        null,
        new Hash("0x" + "c".repeat(64)),
        0L,
        false);

    // Record has 10 fields — indexedTag1 is excluded (unrecoverable hash).
    // feedbackHash uses HexData since toJavaValue maps Bytes → HexData.
    record NewFeedback(
        BigInteger agentId,
        Address clientAddress,
        BigInteger feedbackIndex,
        BigInteger value,
        BigInteger valueDecimals,
        String tag1,
        String tag2,
        String endpoint,
        String feedbackURI,
        HexData feedbackHash) {
    }

    List<NewFeedback> decoded = parsed.decodeEvents("NewFeedback", List.of(log), NewFeedback.class);

    assertFalse(decoded.isEmpty());
    NewFeedback event = decoded.get(0);
    assertEquals(agentId, event.agentId());
    assertEquals(client, event.clientAddress());
    assertEquals(feedbackIndex, event.feedbackIndex());
    assertEquals(value, event.value());
    assertEquals(valueDecimals, event.valueDecimals());
    assertEquals(tag1, event.tag1());
    assertEquals(tag2, event.tag2());
    assertEquals(endpoint, event.endpoint());
    assertEquals(feedbackURI, event.feedbackURI());
    assertEquals(HexData.fromBytes(feedbackHash), event.feedbackHash());
  }
}
