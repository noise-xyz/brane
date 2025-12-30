package io.brane.examples;

import io.brane.contract.ReadOnlyContract;
import io.brane.core.abi.Abi;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.types.Address;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.PublicClient;
import java.math.BigInteger;

public final class Main {

    private static final String RPC_URL =
            System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
    private static final String CONTRACT_ADDRESS =
            System.getProperty(
                    "brane.examples.contract", "0x0000000000000000000000000000000000000000");
    private static final String ABI_JSON =
            """
            [
              {
                "inputs": [
                  { "internalType": "uint256", "name": "x", "type": "uint256" }
                ],
                "name": "echo",
                "outputs": [
                  { "internalType": "uint256", "name": "", "type": "uint256" }
                ],
                "stateMutability": "pure",
                "type": "function"
              }
            ]
            """;

    private Main() {}

    public static void main(final String[] args) {
        final PublicClient publicClient =
                PublicClient.from(HttpBraneProvider.builder(RPC_URL).build());
        final Abi abi = Abi.fromJson(ABI_JSON);
        final ReadOnlyContract contract =
                ReadOnlyContract.from(new Address(CONTRACT_ADDRESS), abi, publicClient);
        try {
            final BigInteger value = contract.call("echo", BigInteger.class, BigInteger.valueOf(42));
            System.out.println("echo(42) = " + value);
        } catch (RevertException e) {
            System.err.println("Contract reverted: " + e.revertReason());
        } catch (RpcException e) {
            System.err.println("RPC error (" + e.code() + "): " + e.getMessage());
        }
    }
}
