package io.brane.examples;

import java.util.ArrayList;
import java.util.List;

import io.brane.contract.ReadOnlyContract;
import io.brane.core.abi.Abi;
import io.brane.core.model.LogEntry;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.rpc.BraneProvider;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.LogFilter;
import io.brane.rpc.PublicClient;

/**
 * Fetches ERC-20 Transfer logs and decodes them.
 *
 * Example:
 * ./gradlew :brane-examples:run --no-daemon \
 * -PmainClass=io.brane.examples.Erc20TransferLogExample \
 * -Dbrane.examples.erc20.rpc=http://127.0.0.1:8545 \
 * -Dbrane.examples.erc20.contract=0x... \
 * -Dbrane.examples.erc20.fromBlock=0 \
 * -Dbrane.examples.erc20.toBlock=latest
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

  public static void main(String[] args) {
    final String rpcUrl = System.getProperty("brane.examples.erc20.rpc");
    final String tokenAddress = System.getProperty("brane.examples.erc20.contract");
    final String fromBlockStr = System.getProperty("brane.examples.erc20.fromBlock");
    final String toBlockStr = System.getProperty("brane.examples.erc20.toBlock");

    if (isBlank(rpcUrl) || isBlank(tokenAddress)) {
      System.out.println("Usage: set -Dbrane.examples.erc20.rpc and -Dbrane.examples.erc20.contract");
      return;
    }

    final Address token = new Address(tokenAddress);
    final BraneProvider provider = rpcUrl.startsWith("ws")
        ? io.brane.rpc.WebSocketProvider.create(rpcUrl)
        : HttpBraneProvider.builder(rpcUrl).build();
    final PublicClient publicClient = PublicClient.from(provider);

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
            + (filter.fromBlock().isPresent() ? "0x" + Long.toHexString(filter.fromBlock().get()) : "latest")
            + ", toBlock="
            + (filter.toBlock().isPresent() ? "0x" + Long.toHexString(filter.toBlock().get()) : "latest")
            + ", address="
            + token.value()
            + ", topics="
            + topics);

    final List<LogEntry> logs = publicClient.getLogs(filter);

    final Abi abi = Abi.fromJson(ERC20_ABI);
    final ReadOnlyContract ro = ReadOnlyContract.from(token, abi, publicClient);

    record Transfer(Address from, Address to, java.math.BigInteger value) {
    }

    final List<Transfer> decoded = ro.decodeEvents("Transfer", logs, Transfer.class);
    System.out.println("Raw logs size = " + logs.size());
    System.out.println("Transfer event size = " + decoded.size());
  }

  private static boolean isBlank(final String value) {
    return value == null || value.isBlank();
  }
}
