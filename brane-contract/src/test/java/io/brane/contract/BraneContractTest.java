package io.brane.contract;

import io.brane.core.error.AbiEncodingException;
import io.brane.core.model.TransactionRequest;

import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BraneContractTest {

    private static final String ABI_JSON = """
            [
              {
                "inputs": [{"internalType": "uint256", "name": "initialSupply", "type": "uint256"}],
                "stateMutability": "nonpayable",
                "type": "constructor"
              }
            ]
            """;
    private static final String BYTECODE = "0x123456";

    @Test
    void deployRequestEncodesCorrectly() {
        BigInteger supply = BigInteger.valueOf(100);
        TransactionRequest request = BraneContract.deployRequest(ABI_JSON, BYTECODE, supply);

        assertNotNull(request);
        // Bytecode: 0x123456 (3 bytes)
        // Args: 100 -> 0x64 (padded to 32 bytes)
        // Total length: 3 + 32 = 35 bytes
        // Expected data: 0x123456 + 00...64
        String expectedData = "0x123456" + "0000000000000000000000000000000000000000000000000000000000000064";
        assertEquals(expectedData, request.data().value());
    }

    @Test
    void deployRequestHandlesMissingPrefix() {
        String bytecodeNoPrefix = "123456";
        BigInteger supply = BigInteger.valueOf(100);
        TransactionRequest request = BraneContract.deployRequest(ABI_JSON, bytecodeNoPrefix, supply);

        String expectedData = "0x123456" + "0000000000000000000000000000000000000000000000000000000000000064";
        assertEquals(expectedData, request.data().value());
    }

    @Test
    void deployRequestThrowsOnTooFewArguments() {
        // ABI expects 1 constructor argument, but we provide none
        AbiEncodingException ex = assertThrows(AbiEncodingException.class,
                () -> BraneContract.deployRequest(ABI_JSON, BYTECODE));

        assertTrue(ex.getMessage().contains("Constructor expects 1 arguments"));
        assertTrue(ex.getMessage().contains("0 were supplied"));
    }

    @Test
    void deployRequestThrowsOnTooManyArguments() {
        // ABI expects 1 constructor argument, but we provide 2
        AbiEncodingException ex = assertThrows(AbiEncodingException.class,
                () -> BraneContract.deployRequest(ABI_JSON, BYTECODE,
                        BigInteger.valueOf(100), BigInteger.valueOf(200)));

        assertTrue(ex.getMessage().contains("Constructor expects 1 arguments"));
        assertTrue(ex.getMessage().contains("2 were supplied"));
    }

    @Test
    void deployRequestWorksWithNoConstructor() {
        String abiNoConstructor = """
                [
                  {
                    "inputs": [],
                    "name": "getValue",
                    "outputs": [{"internalType": "uint256", "name": "", "type": "uint256"}],
                    "stateMutability": "view",
                    "type": "function"
                  }
                ]
                """;

        TransactionRequest request = BraneContract.deployRequest(abiNoConstructor, BYTECODE);
        assertNotNull(request);
        assertEquals("0x123456", request.data().value());
    }

    @Test
    void deployRequestThrowsWhenNoConstructorButArgsProvided() {
        String abiNoConstructor = """
                [
                  {
                    "inputs": [],
                    "name": "getValue",
                    "outputs": [{"internalType": "uint256", "name": "", "type": "uint256"}],
                    "stateMutability": "view",
                    "type": "function"
                  }
                ]
                """;

        AbiEncodingException ex = assertThrows(AbiEncodingException.class,
                () -> BraneContract.deployRequest(abiNoConstructor, BYTECODE, BigInteger.ONE));

        assertTrue(ex.getMessage().contains("Constructor not defined in ABI"));
    }
}
