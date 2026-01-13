// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.examples;

import java.util.ArrayList;
import java.util.List;

import io.brane.core.abi.Abi;
import io.brane.core.model.LogEntry;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.rpc.Brane;
import io.brane.rpc.LogFilter;

/**
 * Fetches ERC-20 Transfer logs and decodes them using the new Brane.connect() API.
 *
 * <p>Example:
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *   -PmainClass=io.brane.examples.Erc20TransferLogExample \
 *   -Dbrane.examples.erc20.rpc=http://127.0.0.1:8545 \
 *   -Dbrane.examples.erc20.contract=0x... \
 *   -Dbrane.examples.erc20.fromBlock=0 \
 *   -Dbrane.examples.erc20.toBlock=latest
 * </pre>
 */
public final class Erc20TransferLogExample {

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

  private Erc20TransferLogExample() {
  }

  public static void main(String[] args) throws Exception {
    final String rpcUrl = System.getProperty("brane.examples.erc20.rpc");
    final String tokenAddress = System.getProperty("brane.examples.erc20.contract");
    final String fromBlockStr = System.getProperty("brane.examples.erc20.fromBlock");
    final String toBlockStr = System.getProperty("brane.examples.erc20.toBlock");

    if (isBlank(rpcUrl) || isBlank(tokenAddress)) {
      System.out.println("Usage: set -Dbrane.examples.erc20.rpc and -Dbrane.examples.erc20.contract");
      return;
    }

    final Address token = new Address(tokenAddress);
    final Long fromBlock = fromBlockStr != null && !fromBlockStr.isBlank() ? Long.parseLong(fromBlockStr) : null;
    final Long toBlock = toBlockStr != null && !toBlockStr.isBlank() && !"latest".equalsIgnoreCase(toBlockStr)
        ? Long.parseLong(toBlockStr)
        : null;

    final List<Hash> topics = new ArrayList<>();
    topics.add(Abi.eventTopic("Transfer(address,address,uint256)"));
    final LogFilter filter = new LogFilter(
        java.util.Optional.ofNullable(fromBlock),
        java.util.Optional.ofNullable(toBlock),
        java.util.Optional.of(List.of(token)),
        java.util.Optional.of(topics));

    System.out.println(
        "eth_getLogs filter: fromBlock="
            + filter.fromBlock().map(b -> "0x" + Long.toHexString(b)).orElse("latest")
            + ", toBlock="
            + filter.toBlock().map(b -> "0x" + Long.toHexString(b)).orElse("latest")
            + ", address="
            + token.value()
            + ", topics="
            + topics);

    // Use Brane.connect() for read-only operations
    Brane client = Brane.connect(rpcUrl);
    try {
      final List<LogEntry> logs = client.getLogs(filter);

      // Decode events using Abi directly (no RPC required for decoding)
      final Abi abi = Abi.fromJson(ERC20_ABI);

      record Transfer(Address from, Address to, java.math.BigInteger value) {
      }

      final List<Transfer> decoded = abi.decodeEvents("Transfer", logs, Transfer.class);
      System.out.println("Raw logs size = " + logs.size());
      System.out.println("Transfer event size = " + decoded.size());
    } finally {
      client.close();
    }
  }

  private static boolean isBlank(final String value) {
    return value == null || value.isBlank();
  }
}
