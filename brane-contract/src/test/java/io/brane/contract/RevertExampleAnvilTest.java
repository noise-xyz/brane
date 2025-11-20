package io.brane.contract;

import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.types.Address;
import io.brane.rpc.Client;
import io.brane.rpc.HttpClient;
import java.math.BigInteger;
import java.net.URI;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RevertExampleAnvilTest {

    private static final String RPC_URL =
            System.getProperty("brane.anvil.rpc", "http://127.0.0.1:8545");
    private static final String CONTRACT_ADDRESS =
            System.getProperty(
                    "brane.anvil.revertExample.address", "0x0000000000000000000000000000000000000000");

    private static final String REVERT_EXAMPLE_ABI =
            """
            [
              {
                "inputs": [],
                "name": "alwaysRevert",
                "outputs": [],
                "stateMutability": "pure",
                "type": "function"
              },
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

    @Test
    void echoRoundTripsValue() throws RpcException, RevertException {
        final Contract contract = newContract();
        Assumptions.assumeFalse(isPlaceholderAddress(CONTRACT_ADDRESS), missingAddressMessage());

        final BigInteger result = contract.read("echo", BigInteger.class, BigInteger.valueOf(42));
        assertEquals(BigInteger.valueOf(42), result);
    }

    @Test
    void alwaysRevertThrowsRevertException() {
        final Contract contract = newContract();
        Assumptions.assumeFalse(isPlaceholderAddress(CONTRACT_ADDRESS), missingAddressMessage());

        final RevertException ex =
                assertThrows(RevertException.class, () -> contract.read("alwaysRevert", Void.class));
        assertEquals("simple reason", ex.revertReason());
        assertNotNull(ex.rawDataHex());
        assertTrue(ex.rawDataHex().startsWith("0x"));
    }

    private Contract newContract() {
        final Client client = new HttpClient(URI.create(RPC_URL));
        final Abi abi = Abi.fromJson(REVERT_EXAMPLE_ABI);
        return new Contract(new Address(CONTRACT_ADDRESS), abi, client);
    }

    private boolean isPlaceholderAddress(final String address) {
        return "0x0000000000000000000000000000000000000000".equalsIgnoreCase(address);
    }

    private String missingAddressMessage() {
        return "Set -Dbrane.anvil.revertExample.address to the deployed contract address before running";
    }
}
