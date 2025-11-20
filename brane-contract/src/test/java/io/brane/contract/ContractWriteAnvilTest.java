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

class ContractWriteAnvilTest {

    private static final String RPC_URL =
            System.getProperty("brane.anvil.rpc", "http://127.0.0.1:8545");
    private static final String CONTRACT_ADDRESS =
            System.getProperty(
                    "brane.anvil.storage.address", "0x0000000000000000000000000000000000000000");
    private static final String PRIVATE_KEY =
            System.getProperty(
                    "brane.anvil.signer.privateKey",
                    "0x0000000000000000000000000000000000000000000000000000000000000000");

    private static final String STORAGE_ABI =
            """
            [
              {
                "inputs": [
                  { "internalType": "uint256", "name": "value", "type": "uint256" }
                ],
                "name": "setValue",
                "outputs": [],
                "stateMutability": "nonpayable",
                "type": "function"
              },
              {
                "inputs": [],
                "name": "value",
                "outputs": [
                  { "internalType": "uint256", "name": "", "type": "uint256" }
                ],
                "stateMutability": "view",
                "type": "function"
              }
            ]
            """;

    @Test
    void writeUpdatesState() throws RpcException, RevertException {
        Assumptions.assumeFalse(isPlaceholderAddress(CONTRACT_ADDRESS), missingStorageMessage());
        Assumptions.assumeFalse(isZeroPrivateKey(PRIVATE_KEY), missingKeyMessage());

        final Client client = new HttpClient(URI.create(RPC_URL));
        final Abi abi = Abi.fromJson(STORAGE_ABI);
        final Contract contract = new Contract(new Address(CONTRACT_ADDRESS), abi, client);
        final Signer signer = new PrivateKeySigner(PRIVATE_KEY);

        final String txHash = contract.write(signer, "setValue", BigInteger.valueOf(1337));
        assertNotNull(txHash);
        assertTrue(txHash.startsWith("0x"));

        final BigInteger stored = contract.read("value", BigInteger.class);
        assertEquals(BigInteger.valueOf(1337), stored);
    }

    private boolean isPlaceholderAddress(final String address) {
        return "0x0000000000000000000000000000000000000000".equalsIgnoreCase(address);
    }

    private boolean isZeroPrivateKey(final String key) {
        return "0x0000000000000000000000000000000000000000000000000000000000000000".equalsIgnoreCase(
                key);
    }

    private String missingStorageMessage() {
        return "Set -Dbrane.anvil.storage.address to the deployed storage contract address";
    }

    private String missingKeyMessage() {
        return "Set -Dbrane.anvil.signer.privateKey to an Anvil private key";
    }
}
