package io.brane.primitives.rlp;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Compatibility tests that verify our RLP implementation produces
 * byte-for-byte identical output to web3j's RLP implementation.
 */
class RlpCompatibilityTest {

    @Test
    void emptyString_matchesWeb3j() {
        // Encode empty string
        byte[] ourEncoding = RlpString.of(new byte[0]).encode();
        
        // web3j encoding
        io.brane.internal.web3j.rlp.RlpString web3jString = 
            io.brane.internal.web3j.rlp.RlpString.create(new byte[0]);
        byte[] web3jEncoding = io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jString);
        
        assertArrayEquals(web3jEncoding, ourEncoding, 
            "Empty string RLP encoding should match web3j");
    }

    @Test
    void singleByte_matchesWeb3j() {
        // Test various single bytes
        byte[] testBytes = {0x00, 0x0f, 0x7f, (byte) 0x80, (byte) 0xff};
        
        for (byte b : testBytes) {
            byte[] data = new byte[]{b};
            
            byte[] ourEncoding = RlpString.of(data).encode();
            
            io.brane.internal.web3j.rlp.RlpString web3jString = 
                io.brane.internal.web3j.rlp.RlpString.create(data);
            byte[] web3jEncoding = io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jString);
            
            assertArrayEquals(web3jEncoding, ourEncoding, 
                String.format("Single byte 0x%02x RLP encoding should match web3j", b));
        }
    }

    @Test
    void shortString_matchesWeb3j() {
        // Test string "dog"
        byte[] dogBytes = "dog".getBytes();
        
        byte[] ourEncoding = RlpString.of(dogBytes).encode();
        
        io.brane.internal.web3j.rlp.RlpString web3jString = 
            io.brane.internal.web3j.rlp.RlpString.create(dogBytes);
        byte[] web3jEncoding = io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jString);
        
        assertArrayEquals(web3jEncoding, ourEncoding, 
            "Short string 'dog' RLP encoding should match web3j");
    }

    @Test
    void longString_matchesWeb3j() {
        // Create a string longer than 55 bytes
        byte[] longData = new byte[60];
        for (int i = 0; i < longData.length; i++) {
            longData[i] = (byte) (i % 256);
        }
        
        byte[] ourEncoding = RlpString.of(longData).encode();
        
        io.brane.internal.web3j.rlp.RlpString web3jString = 
            io.brane.internal.web3j.rlp.RlpString.create(longData);
        byte[] web3jEncoding = io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jString);
        
        assertArrayEquals(web3jEncoding, ourEncoding, 
            "Long string (>55 bytes) RLP encoding should match web3j");
    }

    @Test
    void emptyList_matchesWeb3j() {
        byte[] ourEncoding = RlpList.of().encode();
        
        io.brane.internal.web3j.rlp.RlpList web3jList = 
            new io.brane.internal.web3j.rlp.RlpList(java.util.Collections.emptyList());
        byte[] web3jEncoding = io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jList);
        
        assertArrayEquals(web3jEncoding, ourEncoding, 
            "Empty list RLP encoding should match web3j");
    }

    @Test
    void simpleList_matchesWeb3j() {
        // List containing ["cat", "dog"]
        byte[] catBytes = "cat".getBytes();
        byte[] dogBytes = "dog".getBytes();
        
        RlpList ourList = RlpList.of(
            RlpString.of(catBytes),
            RlpString.of(dogBytes)
        );
        byte[] ourEncoding = ourList.encode();
        
        java.util.List<io.brane.internal.web3j.rlp.RlpType> web3jItems = java.util.Arrays.asList(
            io.brane.internal.web3j.rlp.RlpString.create(catBytes),
            io.brane.internal.web3j.rlp.RlpString.create(dogBytes)
        );
        io.brane.internal.web3j.rlp.RlpList web3jList = 
            new io.brane.internal.web3j.rlp.RlpList(web3jItems);
        byte[] web3jEncoding = io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jList);
        
        assertArrayEquals(web3jEncoding, ourEncoding, 
            "Simple list ['cat', 'dog'] RLP encoding should match web3j");
    }

    @Test
    void nestedList_matchesWeb3j() {
        // Nested list [[]]
        RlpList ourInnerList = RlpList.of();
        RlpList ourOuterList = RlpList.of(ourInnerList);
        byte[] ourEncoding = ourOuterList.encode();
        
        io.brane.internal.web3j.rlp.RlpList web3jInnerList = 
            new io.brane.internal.web3j.rlp.RlpList(java.util.Collections.emptyList());
        java.util.List<io.brane.internal.web3j.rlp.RlpType> web3jItems = 
            java.util.Collections.singletonList(web3jInnerList);
        io.brane.internal.web3j.rlp.RlpList web3jOuterList = 
            new io.brane.internal.web3j.rlp.RlpList(web3jItems);
        byte[] web3jEncoding = io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jOuterList);
        
        assertArrayEquals(web3jEncoding, ourEncoding, 
            "Nested list [[]] RLP encoding should match web3j");
    }

    @Test
    void integerEncoding_matchesWeb3j() {
        // Test various integer values
        // Note: 0 is skipped here because it requires empty byte array encoding
        long[] testValues = {1, 127, 128, 255, 256, 65535, 1000000};
        
        for (long value : testValues) {
            byte[] ourEncoding = RlpString.of(value).encode();
            
            byte[] valueBytes = toMinimalByteArray(value);
            io.brane.internal.web3j.rlp.RlpString web3jString = 
                io.brane.internal.web3j.rlp.RlpString.create(valueBytes);
            byte[] web3jEncoding = io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jString);
            
            assertArrayEquals(web3jEncoding, ourEncoding, 
                String.format("Integer %d RLP encoding should match web3j", value));
        }
    }
    
    @Test
    void zeroEncoding_matchesWeb3j() {
        // Zero is a special case - encoded as empty byte array (0x80)
        byte[] ourEncoding = RlpString.of(BigInteger.ZERO).encode();
        
        io.brane.internal.web3j.rlp.RlpString web3jString = 
            io.brane.internal.web3j.rlp.RlpString.create(new byte[0]);
        byte[] web3jEncoding = io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jString);
        
        assertArrayEquals(web3jEncoding, ourEncoding, 
            "Integer 0 should be encoded as empty byte array (0x80)");
    }

    @Test
    void bigIntegerEncoding_matchesWeb3j() {
        // Test BigInteger values
        BigInteger[] testValues = {
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.valueOf(127),
            BigInteger.valueOf(255),
            BigInteger.valueOf(65535),
            new BigInteger("1000000000000000000"), // 1 ETH in wei
            new BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935") // max uint256
        };
        
        for (BigInteger value : testValues) {
            byte[] ourEncoding = RlpString.of(value).encode();
            
            byte[] valueBytes = value.toByteArray();
            // Remove leading zero bytes (BigInteger adds sign byte)
            int firstNonZero = 0;
            while (firstNonZero < valueBytes.length && valueBytes[firstNonZero] == 0) {
                firstNonZero++;
            }
            if (firstNonZero > 0) {
                valueBytes = java.util.Arrays.copyOfRange(valueBytes, firstNonZero, valueBytes.length);
            }
            if (valueBytes.length == 0) {
                valueBytes = new byte[0];
            }
            
            io.brane.internal.web3j.rlp.RlpString web3jString = 
                io.brane.internal.web3j.rlp.RlpString.create(valueBytes);
            byte[] web3jEncoding = io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jString);
            
            assertArrayEquals(web3jEncoding, ourEncoding, 
                String.format("BigInteger %s RLP encoding should match web3j", value.toString()));
        }
    }

    @Test
    void complexList_matchesWeb3j() {
        // Complex mixed list: ["cat", 42, ["dog", 127]]
        RlpList ourInnerList = RlpList.of(
            RlpString.of("dog".getBytes()),
            RlpString.of(127)
        );
        RlpList ourOuterList = RlpList.of(
            RlpString.of("cat".getBytes()),
            RlpString.of(42),
            ourInnerList
        );
        byte[] ourEncoding = ourOuterList.encode();
        
        io.brane.internal.web3j.rlp.RlpList web3jInnerList = 
            new io.brane.internal.web3j.rlp.RlpList(java.util.Arrays.asList(
                io.brane.internal.web3j.rlp.RlpString.create("dog".getBytes()),
                io.brane.internal.web3j.rlp.RlpString.create(new byte[]{127})
            ));
        io.brane.internal.web3j.rlp.RlpList web3jOuterList = 
            new io.brane.internal.web3j.rlp.RlpList(java.util.Arrays.asList(
                io.brane.internal.web3j.rlp.RlpString.create("cat".getBytes()),
                io.brane.internal.web3j.rlp.RlpString.create(new byte[]{42}),
                web3jInnerList
            ));
        byte[] web3jEncoding = io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jOuterList);
        
        assertArrayEquals(web3jEncoding, ourEncoding, 
            "Complex mixed list RLP encoding should match web3j");
    }

    @Test
    void roundTripDecoding_matchesWeb3j() {
        // Encode with web3j, decode with our implementation
        byte[] originalData = "Hello, Ethereum!".getBytes();
        
        io.brane.internal.web3j.rlp.RlpString web3jString = 
            io.brane.internal.web3j.rlp.RlpString.create(originalData);
        byte[] web3jEncoded = io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jString);
        
        // Decode with our implementation
        RlpItem decoded = Rlp.decode(web3jEncoded);
        
        // Should be an RlpString
        assertEquals(RlpString.class, decoded.getClass(), 
            "Decoded item should be RlpString");
        
        RlpString decodedString = (RlpString) decoded;
        assertArrayEquals(originalData, decodedString.bytes(), 
            "Decoded data should match original data");
    }
    
    @Test
    void zeroLongEncoding_matchesWeb3j() {
        byte[] ourEncoding = RlpString.of(0L).encode();

        io.brane.internal.web3j.rlp.RlpString web3jString =
            io.brane.internal.web3j.rlp.RlpString.create(new byte[0]);
        byte[] web3jEncoding = io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jString);

        assertArrayEquals(web3jEncoding, ourEncoding,
            "Long 0 should be encoded as empty byte array (0x80)");
    }

    @Test
    void roundTripDecoding_longString_matchesWeb3j() {
        byte[] data = new byte[60];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i & 0xFF);
        }

        io.brane.internal.web3j.rlp.RlpString web3jString =
            io.brane.internal.web3j.rlp.RlpString.create(data);
        byte[] web3jEncoded = io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jString);

        RlpItem decoded = Rlp.decode(web3jEncoded);
        assertEquals(RlpString.class, decoded.getClass());

        RlpString decodedString = (RlpString) decoded;
        assertArrayEquals(data, decodedString.bytes(),
            "Decoded long string should match original data");
    }

    /**
     * Helper method to convert long to minimal byte array (no leading zeros)
     */
    private byte[] toMinimalByteArray(long value) {
        if (value == 0) {
            return new byte[0];
        }
        
        byte[] bytes = BigInteger.valueOf(value).toByteArray();
        
        // Remove leading zero bytes
        int firstNonZero = 0;
        while (firstNonZero < bytes.length && bytes[firstNonZero] == 0) {
            firstNonZero++;
        }
        
        if (firstNonZero == 0) {
            return bytes;
        }
        
        return java.util.Arrays.copyOfRange(bytes, firstNonZero, bytes.length);
    }
}
