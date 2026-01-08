package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SnapshotIdTest {

    @Test
    void constructorValidatesNull() {
        assertThrows(NullPointerException.class, () -> new SnapshotId(null));
    }

    @Test
    void constructorValidates0xPrefix() {
        // Missing 0x prefix
        assertThrows(IllegalArgumentException.class, () -> new SnapshotId("1"));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotId("abc"));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotId("0X1")); // uppercase X not allowed
    }

    @Test
    void constructorRejectsInvalidHexCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new SnapshotId("0xGHI"));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotId("0x1g2"));
    }

    @Test
    void constructorAcceptsValid0xPrefixedValues() {
        // Typical snapshot IDs
        SnapshotId id1 = new SnapshotId("0x1");
        assertEquals("0x1", id1.value());

        SnapshotId id2 = new SnapshotId("0xa");
        assertEquals("0xa", id2.value());

        SnapshotId id3 = new SnapshotId("0xABCDEF");
        assertEquals("0xABCDEF", id3.value());

        // Empty hex after prefix (edge case, but valid)
        SnapshotId id4 = new SnapshotId("0x");
        assertEquals("0x", id4.value());

        // Longer hex values
        SnapshotId id5 = new SnapshotId("0x1234567890abcdef");
        assertEquals("0x1234567890abcdef", id5.value());
    }

    @Test
    void fromFactoryMethod() {
        SnapshotId id = SnapshotId.from("0x1");
        assertEquals("0x1", id.value());
    }

    @Test
    void fromFactoryMethodValidatesNull() {
        assertThrows(NullPointerException.class, () -> SnapshotId.from(null));
    }

    @Test
    void fromFactoryMethodValidates0xPrefix() {
        assertThrows(IllegalArgumentException.class, () -> SnapshotId.from("1"));
    }

    @Test
    void revertUsingValidatesTesterNotNull() {
        SnapshotId id = new SnapshotId("0x1");
        assertThrows(NullPointerException.class, () -> id.revertUsing(null));
    }

    @Test
    void revertUsingCallsTesterRevert() {
        SnapshotId id = new SnapshotId("0x1");
        MockTester tester = new MockTester(true);

        boolean result = id.revertUsing(tester);

        assertTrue(result);
        assertEquals(id, tester.lastRevertedSnapshot);
    }

    @Test
    void revertUsingReturnsFalseWhenTesterReturnsFlse() {
        SnapshotId id = new SnapshotId("0x1");
        MockTester tester = new MockTester(false);

        boolean result = id.revertUsing(tester);

        assertFalse(result);
        assertEquals(id, tester.lastRevertedSnapshot);
    }

    @Test
    void recordEquality() {
        SnapshotId id1 = new SnapshotId("0x1");
        SnapshotId id2 = new SnapshotId("0x1");
        SnapshotId id3 = new SnapshotId("0x2");

        assertEquals(id1, id2);
        assertNotEquals(id1, id3);
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void toStringIncludesValue() {
        SnapshotId id = new SnapshotId("0x1");
        assertTrue(id.toString().contains("0x1"));
    }

    /**
     * Mock implementation of Brane.Tester for testing.
     */
    private static class MockTester implements Brane.Tester {
        private final boolean revertResult;
        SnapshotId lastRevertedSnapshot;

        MockTester(boolean revertResult) {
            this.revertResult = revertResult;
        }

        @Override
        public SnapshotId snapshot() {
            return new SnapshotId("0x1");
        }

        @Override
        public boolean revert(SnapshotId snapshotId) {
            this.lastRevertedSnapshot = snapshotId;
            return revertResult;
        }

        @Override
        public Brane.Signer asSigner() {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public io.brane.core.types.Hash sendTransaction(io.brane.core.model.TransactionRequest request) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public io.brane.core.model.TransactionReceipt sendTransactionAndWait(
                io.brane.core.model.TransactionRequest request, long timeoutMillis, long pollIntervalMillis) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public io.brane.core.crypto.Signer signer() {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public ImpersonationSession impersonate(io.brane.core.types.Address address) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void stopImpersonating(io.brane.core.types.Address address) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void enableAutoImpersonate() {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void disableAutoImpersonate() {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void setBalance(io.brane.core.types.Address address, io.brane.core.types.Wei balance) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void setCode(io.brane.core.types.Address address, io.brane.core.types.HexData code) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void setNonce(io.brane.core.types.Address address, long nonce) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void setStorageAt(io.brane.core.types.Address address, io.brane.core.types.Hash slot, io.brane.core.types.Hash value) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void mine() {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void mine(long blocks) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void mine(long blocks, long intervalSeconds) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void mineAt(long timestamp) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public boolean getAutomine() {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void setAutomine(boolean enabled) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void setIntervalMining(long intervalMs) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void setNextBlockTimestamp(long timestamp) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void increaseTime(long seconds) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void setNextBlockBaseFee(io.brane.core.types.Wei baseFee) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void setBlockGasLimit(java.math.BigInteger gasLimit) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void setCoinbase(io.brane.core.types.Address coinbase) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void reset() {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public void reset(String forkUrl, long blockNumber) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public io.brane.core.types.HexData dumpState() {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public boolean loadState(io.brane.core.types.HexData state) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public boolean dropTransaction(io.brane.core.types.Hash txHash) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public io.brane.core.model.TransactionReceipt waitForReceipt(
                io.brane.core.types.Hash txHash, long timeoutMillis, long pollIntervalMillis) {
            throw new UnsupportedOperationException("Mock tester");
        }

        // Required Brane interface methods - throw UnsupportedOperationException for mock
        @Override
        public java.math.BigInteger chainId() {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public java.math.BigInteger getBalance(io.brane.core.types.Address address) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public io.brane.core.model.BlockHeader getLatestBlock() {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public io.brane.core.model.BlockHeader getBlockByNumber(long blockNumber) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public io.brane.core.model.Transaction getTransactionByHash(io.brane.core.types.Hash hash) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public io.brane.core.model.TransactionReceipt getTransactionReceipt(io.brane.core.types.Hash hash) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public io.brane.core.types.HexData call(CallRequest request) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public io.brane.core.types.HexData call(CallRequest request, BlockTag blockTag) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public java.util.List<io.brane.core.model.LogEntry> getLogs(LogFilter filter) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public java.math.BigInteger estimateGas(io.brane.core.model.TransactionRequest request) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public io.brane.core.model.AccessListWithGas createAccessList(io.brane.core.model.TransactionRequest request) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public SimulateResult simulate(SimulateRequest request) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public MulticallBatch batch() {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public Subscription onNewHeads(java.util.function.Consumer<io.brane.core.model.BlockHeader> callback) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public Subscription onLogs(LogFilter filter, java.util.function.Consumer<io.brane.core.model.LogEntry> callback) {
            throw new UnsupportedOperationException("Mock tester");
        }

        @Override
        public java.util.Optional<io.brane.core.chain.ChainProfile> chain() {
            return java.util.Optional.empty();
        }

        @Override
        public boolean canSubscribe() {
            return false;
        }

        @Override
        public void close() {
            // No-op for mock
        }
    }
}
