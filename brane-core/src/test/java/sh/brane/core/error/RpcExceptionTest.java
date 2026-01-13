// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RpcExceptionTest {

    @Test
    void detectsBlockRangeTooLarge() {
        RpcException ex = new RpcException(-32000, "block range is too large", null, null, null);
        assertTrue(ex.isBlockRangeTooLarge());
        assertFalse(ex.isFilterNotFound());
    }

    @Test
    void detectsFilterNotFound() {
        RpcException ex = new RpcException(-32000, "Filter not found", null, null, null);
        assertTrue(ex.isFilterNotFound());
        assertFalse(ex.isBlockRangeTooLarge());
    }

    @Test
    void capturesRequestIdInMessage() {
        RpcException ex = new RpcException(-32000, "test", null, 42L, null);

        assertEquals(42L, ex.requestId());
        assertTrue(ex.getMessage().contains("[requestId=42]"));
        assertEquals(-32000, ex.code());
    }

    @Test
    void supportsNullRequestId() {
        RpcException ex = new RpcException(-32000, "test", null, null, null);

        assertNull(ex.requestId());
        assertEquals("test", ex.getMessage());
    }
}
