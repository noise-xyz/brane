package io.brane.core.error;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RpcExceptionTest {

    @Test
    void detectsBlockRangeTooLarge() {
        RpcException ex = new RpcException(-32000, "block range is too large", null, null);
        assertTrue(ex.isBlockRangeTooLarge());
        assertFalse(ex.isFilterNotFound());
    }

    @Test
    void detectsFilterNotFound() {
        RpcException ex = new RpcException(-32000, "Filter not found", null, null);
        assertTrue(ex.isFilterNotFound());
        assertFalse(ex.isBlockRangeTooLarge());
    }
}
