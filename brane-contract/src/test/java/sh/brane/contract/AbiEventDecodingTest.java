// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

import sh.brane.core.abi.Abi;
import sh.brane.core.model.LogEntry;
import sh.brane.core.types.Address;
import sh.brane.core.types.Hash;
import sh.brane.core.types.HexData;

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
    String data = sh.brane.primitives.Hex.encode(
        sh.brane.core.abi.AbiEncoder.encode(
            java.util.List.of(new sh.brane.core.abi.UInt(256, amount))));

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
}
