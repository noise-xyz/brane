# Brane Web3j Independence - Phase 1: Hex Utils Replacement

## Goal

Replace the web3j `Numeric` class with custom hex encoding/decoding utilities to achieve:
- First step toward web3j independence
- Zero external dependencies for basic hex operations
- Foundation for future replacements (RLP, crypto)
- Maintain 100% API compatibility (internal change only)

## Current Dependencies

The `io.brane.internal.web3j.utils.Numeric` class is used in:
- `brane-core/src/main/java/io/brane/core/types/Address.java`
- `brane-core/src/main/java/io/brane/core/types/Hash.java`
- `brane-core/src/main/java/io/brane/core/types/HexData.java`

**Used methods:**
- `hexStringToByteArray(String hexString)` - Converts 0x-prefixed hex to bytes
- `toHexStringNoPrefix(byte[] bytes)` - Converts bytes to hex without 0x prefix
- `cleanHexPrefix(String hexString)` - Removes 0x prefix if present

---

## Implementation Plan

### Step 1: Create `brane-primitives` module

**Files to create:**
- `brane-primitives/build.gradle`
- `brane-primitives/src/main/java/io/brane/primitives/Hex.java`
- `brane-primitives/src/test/java/io/brane/primitives/HexTest.java`

**Tasks:**

1. Create new Gradle module `brane-primitives`:
   ```groovy
   // brane-primitives/build.gradle
   dependencies {
       // No external dependencies - pure JDK
   }
   ```

2. Add to root `settings.gradle`:
   ```groovy
   include 'brane-primitives'
   ```

3. Create `io.brane.primitives.Hex` utility class with methods:
   ```java
   public final class Hex {
       // Convert 0x-prefixed hex string to byte array
       public static byte[] decode(String hexString)
       
       // Convert byte array to hex string (with 0x prefix)
       public static String encode(byte[] bytes)
       
       // Convert byte array to hex string (without 0x prefix)
       public static String encodeNoPrefix(byte[] bytes)
       
       // Remove 0x prefix if present
       public static String cleanPrefix(String hexString)
       
       // Check if string has 0x prefix
       public static boolean hasPrefix(String hexString)
   }
   ```

4. Implementation details:
   - Use lookup tables for performance (char to nibble mapping)
   - Handle edge cases: null, empty, odd-length strings
   - Case-insensitive decoding
   - Lowercase encoding by default
   - Throw `IllegalArgumentException` for invalid hex

---

### Step 2: Comprehensive test coverage

**File:** `brane-primitives/src/test/java/io/brane/primitives/HexTest.java`

**Test cases:**

1. **Encoding tests:**
   - Empty array → `"0x"`
   - Single byte → `"0x00"`, `"0xff"`
   - Multiple bytes → proper encoding
   - No prefix variant works correctly

2. **Decoding tests:**
   - `"0x"` → empty array
   - `"0x00"` → `[0]`
   - `"0xff"` → `[-1]`
   - Uppercase hex → decodes correctly
   - Lowercase hex → decodes correctly
   - Mixed case → decodes correctly

3. **Edge cases:**
   - Null input → `IllegalArgumentException`
   - Odd-length hex (e.g., `"0x1"`) → `IllegalArgumentException` or pad with leading zero
   - Invalid characters → `IllegalArgumentException`
   - Missing prefix → handle gracefully or reject

4. **Prefix handling:**
   - `cleanPrefix("0x1234")` → `"1234"`
   - `cleanPrefix("1234")` → `"1234"`
   - `hasPrefix("0x...")` → `true`
   - `hasPrefix("...")` → `false`

5. **Round-trip tests:**
   - `decode(encode(bytes)) == bytes`
   - For various byte arrays

---

### Step 3: Update `brane-core` to use `Hex`

**Files to modify:**
- `brane-core/build.gradle`
- `brane-core/src/main/java/io/brane/core/types/Address.java`
- `brane-core/src/main/java/io/brane/core/types/Hash.java`
- `brane-core/src/main/java/io/brane/core/types/HexData.java`

**Tasks:**

1. Add dependency in `brane-core/build.gradle`:
   ```groovy
   dependencies {
       implementation project(':brane-primitives')
       // ... existing dependencies
   }
   ```

2. Update `Address.java`:
   ```java
   // Replace:
   import io.brane.internal.web3j.utils.Numeric;
   
   // With:
   import io.brane.primitives.Hex;
   
   // Replace method calls:
   Numeric.hexStringToByteArray(value) → Hex.decode(value)
   Numeric.toHexStringNoPrefix(bytes) → Hex.encodeNoPrefix(bytes)
   ```

3. Update `Hash.java` (same pattern as Address)

4. Update `HexData.java` (same pattern as Address)

---

### Step 4: Remove web3j `Numeric` usage from other modules

**Search for remaining usages:**

1. Audit all imports:
   ```bash
   grep -r "import io.brane.internal.web3j.utils.Numeric" brane-*/src
   ```

2. For each usage found:
   - If in public API → replace with `Hex` from `brane-primitives`
   - If in `internal.web3j` package → leave for now (internal only)

3. Update dependencies where needed (add `brane-primitives` dependency)

---

### Step 5: Integration testing

**Tasks:**

1. Run existing test suite:
   ```bash
   ./gradlew :brane-primitives:test --no-daemon
   ./gradlew :brane-core:test --no-daemon
   ./gradlew clean test --no-daemon
   ```

2. Run integration tests:
   ```bash
   ./run_integration_tests.sh
   ```

3. Verify all examples still work:
   - `Main.java` (Echo example)
   - `Erc20Example.java`
   - `Erc20TransferExample.java`
   - All others in integration suite

4. Performance validation (optional):
   - Benchmark encoding/decoding vs web3j `Numeric`
   - Should be comparable or faster (lookup tables)

---

### Step 6: Documentation & cleanup

**Tasks:**

1. Add module README:
   ```markdown
   # brane-primitives
   
   Core primitive utilities with zero external dependencies.
   
   ## Features
   - Hex encoding/decoding
   - Future: RLP encoding, byte utilities
   ```

2. Add JavaDoc to `Hex` class:
   - Document each method
   - Include examples
   - Specify exception behavior

3. Update main README to reflect new module structure

4. Consider adding module-info.java (Java 9+ module):
   ```java
   module io.brane.primitives {
       exports io.brane.primitives;
   }
   ```

---

## Success Criteria

✅ **All tests pass** - No regressions  
✅ **Zero `Numeric` usage** in public-facing code (`brane-core`, `brane-rpc`, `brane-contract`)  
✅ **Integration tests pass** - All examples work  
✅ **No new dependencies** - `brane-primitives` is pure JDK  
✅ **Performance maintained** - No noticeable slowdown  

---

## Rollback Plan

If issues arise:
1. Keep both implementations temporarily
2. Gate with feature flag if needed
3. Gradual migration per module
4. Web3j `Numeric` remains available in `internal` package as fallback

---

## Future Work (Phase 1 continuation)

After hex utils:
1. **RLP Encoding** - Transaction serialization independence
2. **Crypto/Signing** - Use BouncyCastle directly
3. **ABI Codec** - Largest but most isolated component

Each phase builds on the previous, reducing web3j dependency surface area incrementally.

---

## Time Estimate

- Step 1-2 (Module + Tests): 2-3 hours
- Step 3-4 (Integration): 1-2 hours  
- Step 5-6 (Testing + Docs): 1-2 hours

**Total: 4-7 hours** for complete hex utils independence.
