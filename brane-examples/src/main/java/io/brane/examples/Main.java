package io.brane.examples;

import io.brane.contract.Abi;
import io.brane.contract.Contract;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.types.Address;
import io.brane.rpc.Client;
import io.brane.rpc.HttpClient;
import java.math.BigInteger;
import java.net.URI;

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
        final Client client = new HttpClient(URI.create(RPC_URL));
        final Abi abi = Abi.fromJson(ABI_JSON);
        final Contract contract = new Contract(new Address(CONTRACT_ADDRESS), abi, client);
        try {
            final BigInteger value = contract.read("echo", BigInteger.class, BigInteger.valueOf(42));
            System.out.println("echo(42) = " + value);
        } catch (RevertException e) {
            System.err.println("Contract reverted: " + e.revertReason());
        } catch (RpcException e) {
            System.err.println("RPC error (" + e.code() + "): " + e.getMessage());
        }
    }
}
