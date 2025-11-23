# Brane Web3j Independence - Phase 2: RLP Encoding Replacement

## Goal

Replace the web3j RLP (Recursive Length Prefix) encoding/decoding implementation with a custom, zero-dependency version to achieve:
- Transaction serialization independence
- Foundation for Phase 3 (Crypto/Signing independence)
- Better understanding and control of transaction encoding
- Maintain 100% compatibility with Ethereum RLP spec

## Current RLP Implementation

The `io.brane.internal.web3j.rlp` package contains **5 files** (~16KB total):

**Core types:**
- `RlpType.java` (673 bytes) - Base interface for RLP types
- `RlpString.java` (2,592 bytes) - Represents RLP-encoded byte string
- `RlpList.java` (1,019 bytes) - Represents RLP-encoded list

**Encoders/Decoders:**
- `RlpEncoder.java` (3,726 bytes) - Encodes values to RLP format
- `RlpDecoder.java` (8,468 bytes) - Decodes RLP format to values

**Current usage:**
- `TransactionEncoder.java` - Encoding tx for signing
- `TransactionDecoder.java` - Decoding raw transactions
- `ContractUtils.java` - Contract address calculation
- All transaction types (Legacy, EIP-2930, EIP-1559, EIP-7702)

---

## RLP Specification Summary

**Ethereum RLP encoding rules:**

1. **Single byte [0x00, 0x7f]**: Encoded as itself
2. **Byte string [0-55 bytes]**: `[0x80 + length, ...bytes]`
3. **Byte string [56+ bytes]**: `[0xb7 + lengthOfLength, ...lengthBytes, ...bytes]`
4. **List [0-55 bytes total]**: `[0xc0 + length, ...items]`
5. **List [56+ bytes total]**: `[0xf7 + lengthOfLength, ...lengthBytes, ...items]`

**References:**
- Ethereum Yellow Paper (Appendix B)
- https://ethereum.org/en/developers/docs/data-structures-and-encoding/rlp/

---

## Implementation Plan

### Step 1: Create RLP types in `brane-primitives`

**Files to create:**
- `brane-primitives/src/main/java/io/brane/primitives/rlp/RlpItem.java`
- `brane-primitives/src/main/java/io/brane/primitives/rlp/RlpString.java`
- `brane-primitives/src/main/java/io/brane/primitives/rlp/RlpList.java`
- `brane-primitives/src/main/java/io/brane/primitives/rlp/Rlp.java`

**Tasks:**

1. Create `RlpItem` sealed interface:
   ```java
   public sealed interface RlpItem 
       permits RlpString, RlpList {
       byte[] encode();
   }
   ```

2. Create `RlpString` record:
   ```java
   public record RlpString(byte[] bytes) implements RlpItem {
       // Factory methods
       public static RlpString of(byte[] bytes)
       public static RlpString of(String hex)
       public static RlpString of(long value)
       public static RlpString of(BigInteger value)
       
       @Override
       public byte[] encode() { /* RLP encoding logic */ }
   }
   ```

3. Create `RlpList` record:
   ```java
   public record RlpList(List<RlpItem> items) implements RlpItem {
       public static RlpList of(RlpItem... items)
       public static RlpList of(List<RlpItem> items)
       
       @Override
       public byte[] encode() { /* RLP list encoding */ }
   }
   ```

4. Create `Rlp` utility class:
   ```java
   public final class Rlp {
       // Encoding
       public static byte[] encode(RlpItem item)
       public static byte[] encodeString(byte[] bytes)
       public static byte[] encodeList(List<RlpItem> items)
       
       // Decoding
       public static RlpItem decode(byte[] encoded)
       public static List<RlpItem> decodeList(byte[] encoded)
   }
   ```

**Design choices:**
- Use Java 21 **sealed interfaces** for type safety
- Use **records** for immutable value types
- Separate concerns: types vs. encoding logic
- Keep API simple and focused

---

### Step 2: Implement RLP encoding logic

**File:** `brane-primitives/src/main/java/io/brane/primitives/rlp/Rlp.java`

**Encoding algorithm:**

1. **String encoding:**
   ```java
   private static byte[] encodeString(byte[] bytes) {
       if (bytes.length == 1 && bytes[0] >= 0x00 && bytes[0] <= 0x7f) {
           return bytes; // Single byte [0x00, 0x7f]
       }
       if (bytes.length <= 55) {
           return concat((byte)(0x80 + bytes.length), bytes);
       }
       byte[] lengthBytes = toMinimalBytes(bytes.length);
       return concat(
           (byte)(0xb7 + lengthBytes.length),
           lengthBytes,
           bytes
       );
   }
   ```

2. **List encoding:**
   ```java
   private static byte[] encodeList(List<RlpItem> items) {
       byte[] encodedItems = concatenate(items.stream()
           .map(RlpItem::encode)
           .toArray(byte[][]::new));
           
       if (encodedItems.length <= 55) {
           return concat((byte)(0xc0 + encodedItems.length), encodedItems);
       }
       byte[] lengthBytes = toMinimalBytes(encodedItems.length);
       return concat(
           (byte)(0xf7 + lengthBytes.length),
           lengthBytes,
           encodedItems
       );
   }
   ```

3. **Helper methods:**
   - `toMinimalBytes(int length)` - Convert integer to minimal byte array
   - `concat(byte[]... arrays)` - Concatenate byte arrays
   - Reuse `Hex` from Phase 1 for debugging/testing

---

### Step 3: Implement RLP decoding logic

**Decoding algorithm:**

1. **Decode entry point:**
   ```java
   public static RlpItem decode(byte[] data) {
       return decode(data, 0).item;
   }
   
   private record DecodeResult(RlpItem item, int consumed) {}
   
   private static DecodeResult decode(byte[] data, int offset) {
       byte prefix = data[offset];
       
       if (prefix <= 0x7f) {
           // Single byte
           return new DecodeResult(
               new RlpString(new byte[]{prefix}), 1
           );
       }
       if (prefix <= 0xb7) {
           // Short string
           int length = prefix - 0x80;
           return decodeString(data, offset, length, 1);
       }
       if (prefix <= 0xbf) {
           // Long string
           int lengthOfLength = prefix - 0xb7;
           return decodeLongString(data, offset, lengthOfLength);
       }
       if (prefix <= 0xf7) {
           // Short list
           int length = prefix - 0xc0;
           return decodeList(data, offset, length, 1);
       }
       // Long list
       int lengthOfLength = prefix - 0xf7;
       return decodeLongList(data, offset, lengthOfLength);
   }
   ```

2. **Handle edge cases:**
   - Empty lists: `0xc0`
   - Empty strings: `0x80`
   - Leading zeros in integers
   - Nested lists

---

### Step 4: Comprehensive test coverage

**File:** `brane-primitives/src/test/java/io/brane/primitives/rlp/RlpTest.java`

**Test vectors from Ethereum RLP spec:**

1. **String encoding:**
   - `""` → `0x80`
   - `"dog"` → `0x83646f67`
   - Single byte `0x00` → `0x00`
   - Single byte `0x0f` → `0x0f`
   - Single byte `0x400` (1024) → `0x820400`

2. **List encoding:**
   - `[]` → `0xc0`
   - `["cat", "dog"]` → `0xc88363617483646f67`
   - `[[]]` → `0xc1c0`

3. **Round-trip tests:**
   - Encode then decode = identity
   - For various data types (strings, ints, lists, nested)

4. **Transaction encoding tests:**
   - Legacy transaction structure
   - EIP-1559 transaction structure
   - Verify against known transaction hashes

5. **Edge cases:**
   - Large lists (>55 bytes)
   - Long byte strings
   - Deeply nested structures
   - Maximum values

---

### Step 5: Integration with transaction encoding

**Files to update:**
- Update `TransactionEncoder` to use new RLP (in later step)
- Keep web3j RLP for now (internal only)

**Tasks:**

1. **Add RLP dependency to brane-core:**
   ```groovy
   // brane-core/build.gradle
   dependencies {
       implementation project(':brane-primitives')
   }
   ```

2. **Do NOT replace web3j RLP yet** - This phase is just about creating the replacement
   - Keeps risk low
   - Allows parallel testing
   - Easy rollback if issues found

3. **Create adapter/compatibility layer:**
   ```java
   // brane-core/.../RlpAdapter.java (temporary)
   public class RlpAdapter {
       public static RlpItem fromWeb3j(io.brane.internal.web3j.rlp.RlpType web3jType) {
           // Convert web3j RLP to our RLP
       }
       
       public static io.brane.internal.web3j.rlp.RlpType toWeb3j(RlpItem item) {
           // Convert our RLP to web3j RLP
       }
   }
   ```

---

### Step 6: Verification & benchmarking

**Tasks:**

1. **Unit tests:**
   ```bash
   ./gradlew :brane-primitives:test --no-daemon
   ```

2. **Compatibility tests:**
   - Encode same data with both implementations
   - Compare byte-for-byte equality
   - Test with real transaction data

3. **Performance benchmark (optional):**
   ```java
   @Test
   void benchmarkEncoding() {
       // Compare encoding performance
       // Web3j RLP vs. custom RLP
       // Should be similar or faster
   }
   ```

4. **Integration tests:**
   ```bash
   ./run_integration_tests.sh
   ```
   All should still pass (using web3j RLP internally)

---

### Step 7: Documentation

**Tasks:**

1. Update `brane-primitives/README.md`:
   ```markdown
   # brane-primitives
   
   ## Features
   - Hex encoding/decoding ✓
   - RLP encoding/decoding ✓
   
   ## RLP Usage
   
   ```java
   import io.brane.primitives.rlp.*;
   
   // Encode a string
   RlpString str = RlpString.of("hello");
   byte[] encoded = str.encode();
   
   // Encode a list
   RlpList list = RlpList.of(
       RlpString.of("cat"),
       RlpString.of("dog")
   );
   byte[] encodedList = list.encode();
   
   // Decode
   RlpItem decoded = Rlp.decode(encodedList);
   ```

2. Add JavaDoc to all public methods

3. Document RLP spec references

---

## Success Criteria

✅ **RLP encoding matches Ethereum spec** - All test vectors pass  
✅ **RLP decoding works correctly** - Round-trip tests pass  
✅ **No new dependencies** - Pure JDK implementation  
✅ **Performance acceptable** - No significant slowdown  
✅ **Existing tests pass** - No regression (web3j RLP still in use)  
✅ **Documentation complete** - Usage examples and spec references  

---

## Risk Mitigation

🛡️ **Low risk approach:**
1. Build RLP implementation independently
2. Test extensively with spec vectors
3. Keep web3j RLP in place (no breaking changes)
4. Phase 3 will do the actual replacement

🛡️ **Rollback plan:**
- If issues arise, simply don't migrate from web3j RLP
- New RLP code is isolated in `brane-primitives`
- Zero impact on existing functionality

---

## Next Steps After Phase 2

### Phase 3: Replace web3j RLP usage
**After** Phase 2 is complete and verified:
1. Update `TransactionEncoder` to use `io.brane.primitives.rlp`
2. Update all transaction type classes
3. Remove `io.brane.internal.web3j.rlp` package
4. Verify transaction signing still works

This will be a separate, deliberate phase to ensure safety.

---

## Time Estimate

- Step 1-2 (Types + Encoding): 3-4 hours
- Step 3 (Decoding): 2-3 hours
- Step 4 (Tests): 2-3 hours
- Step 5-7 (Integration + Docs): 1-2 hours

**Total: 8-12 hours** for complete RLP independence (implementation only, not replacement)

---

## Resources

**RLP Specification:**
- [Ethereum RLP Encoding](https://ethereum.org/en/developers/docs/data-structures-and-encoding/rlp/)
- [Ethereum Yellow Paper - Appendix B](https://ethereum.github.io/yellowpaper/paper.pdf)
- [EIP-2718](https://eips.ethereum.org/EIPS/eip-2718) - Typed transaction envelope

**Test vectors:**
- https://github.com/ethereum/tests/tree/develop/RLPTests
- Built into web3j's existing tests (can cross-reference)
