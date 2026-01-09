package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TestNodeModeTest {

    @Test
    void anvilPrefixIsCorrect() {
        assertEquals("anvil_", TestNodeMode.ANVIL.prefix());
    }

    @Test
    void hardhatPrefixIsCorrect() {
        assertEquals("hardhat_", TestNodeMode.HARDHAT.prefix());
    }

    @Test
    void ganachePrefixIsCorrect() {
        assertEquals("evm_", TestNodeMode.GANACHE.prefix());
    }

    @Test
    void prefixCanBeUsedToConstructMethodNames() {
        String mineMethod = TestNodeMode.ANVIL.prefix() + "mine";
        assertEquals("anvil_mine", mineMethod);

        String setBalanceMethod = TestNodeMode.HARDHAT.prefix() + "setBalance";
        assertEquals("hardhat_setBalance", setBalanceMethod);

        String snapshotMethod = TestNodeMode.GANACHE.prefix() + "snapshot";
        assertEquals("evm_snapshot", snapshotMethod);
    }

    @Test
    void allEnumValuesAreDefined() {
        TestNodeMode[] values = TestNodeMode.values();
        assertEquals(3, values.length);
        assertNotNull(TestNodeMode.valueOf("ANVIL"));
        assertNotNull(TestNodeMode.valueOf("HARDHAT"));
        assertNotNull(TestNodeMode.valueOf("GANACHE"));
    }
}
