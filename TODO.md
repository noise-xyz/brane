# TODO: brane-core Principal Engineer Code Review

**Review Date:** 2025-12-30
**Reviewer:** Principal Engineer (Opus 4.5)
**Files Reviewed:** 61 Java files in `brane-core/src/main/java/io/brane/core/`

---

## CRITICAL PRIORITY (3 Issues)

### CRIT-1: HexData Stores Raw Bytes Without Defensive Copy

**File:** `brane-core/src/main/java/io/brane/core/types/HexData.java:103-106`

**Problem:** The private constructor stores the raw byte array reference directly without making a defensive copy. Any external caller with access to the original array can mutate HexData's internal state, breaking immutability guarantees.

```java
private HexData(byte[] raw) {
    this.raw = raw;  // No defensive copy!
    this.value = null;
}
```

**Why Critical:** HexData is documented as immutable and thread-safe. External mutation could corrupt transaction data, signatures, or addresses causing incorrect blockchain operations.

**Acceptance Criteria:**
- [ ] Make a defensive copy of the byte array in the constructor: `this.raw = raw.clone();`
- [ ] Verify that `fromBytes()` callers do not rely on shared array semantics
- [ ] Add test proving mutation of original array does not affect HexData

---

### CRIT-2: Integer Overflow Risk in Signature.getRecoveryId()

**File:** `brane-core/src/main/java/io/brane/core/crypto/Signature.java:70-77`

**Problem:** The method casts `chainId * 2 + 35` to `int`, which can overflow for large chain IDs. Some L2 chains have chain IDs exceeding 2^30, and the multiplication by 2 plus 35 can exceed Integer.MAX_VALUE.

```java
public int getRecoveryId(final long chainId) {
    if (v == 0 || v == 1) {
        return v;
    }
    return v - (int) (chainId * 2 + 35);  // Overflow for large chainId
}
```

**Why Critical:** Incorrect recovery ID calculation means signature verification fails, potentially accepting invalid signatures or rejecting valid ones.

**Acceptance Criteria:**
- [ ] Change return type to `long` or keep as `int` with validation that result is 0 or 1
- [ ] Add validation that the computed result is actually 0 or 1 (valid recovery IDs)
- [ ] Add test cases with chain IDs > 2^30 (e.g., chain ID 4294967295)
- [ ] Document the valid range of chain IDs supported

---

### CRIT-3: Keccak256 ThreadLocal Never Cleaned Up

**File:** `brane-core/src/main/java/io/brane/core/crypto/Keccak256.java:26`

**Problem:** The ThreadLocal digest instance is never removed. In application server environments with thread pools, this causes memory leaks as digest instances accumulate.

```java
private static final ThreadLocal<Keccak.Digest256> DIGEST = ThreadLocal.withInitial(Keccak.Digest256::new);
```

**Why Critical:** Memory leaks in long-running applications, especially web servers using thread pools where threads are reused.

**Acceptance Criteria:**
- [ ] Document the ThreadLocal lifetime behavior in class Javadoc
- [ ] Add guidance for users on when to call `DIGEST.remove()` in servlet/application server environments
- [ ] OR provide a `Keccak256.cleanup()` method that calls `DIGEST.remove()`
- [ ] OR evaluate switching to per-call instantiation (benchmark performance trade-off)

---

## HIGH PRIORITY (10 Issues)

### HIGH-1: TransactionReceipt Missing Validation in Compact Constructor

**File:** `brane-core/src/main/java/io/brane/core/model/TransactionReceipt.java:47-57`

**Problem:** TransactionReceipt has no compact constructor, allowing null values for required fields like `transactionHash`, `from`, and `blockHash`. Also `logs` list is not defensively copied.

**Acceptance Criteria:**
- [ ] Add compact constructor with `Objects.requireNonNull()` for: transactionHash, blockHash, from, cumulativeGasUsed
- [ ] Make defensive copy of `logs` list: `logs = List.copyOf(logs)`
- [ ] Document which fields can be null (only `to` for contract creation, `contractAddress` for non-creation)
- [ ] Add test verifying null rejection for required fields

---

### HIGH-2: BlockHeader Missing All Validation

**File:** `brane-core/src/main/java/io/brane/core/model/BlockHeader.java:15`

**Problem:** BlockHeader is an empty record with no validation. All fields use boxed types (`Long`) suggesting they can be null, but there's no documentation of when.

```java
public record BlockHeader(Hash hash, Long number, Hash parentHash, Long timestamp, Wei baseFeePerGas) {}
```

**Acceptance Criteria:**
- [ ] Add compact constructor validating required fields (hash, number, parentHash, timestamp)
- [ ] Use primitive `long` for `number` and `timestamp` if never null
- [ ] Add Javadoc explaining when `baseFeePerGas` is null (pre-London blocks)
- [ ] Add test verifying validation

---

### HIGH-3: LogEntry Missing Validation

**File:** `brane-core/src/main/java/io/brane/core/model/LogEntry.java:21-29`

**Problem:** LogEntry has no validation. Fields like `address`, `data`, `topics` should never be null.

**Acceptance Criteria:**
- [ ] Add compact constructor with null checks for: address, data, topics, transactionHash
- [ ] Make defensive copy of `topics`: `topics = List.copyOf(topics)`
- [ ] Document nullability of `blockHash` (null for pending logs)
- [ ] Add test for validation

---

### HIGH-4: Call3 and MulticallResult Missing Validation

**Files:**
- `brane-core/src/main/java/io/brane/core/model/Call3.java:13-17`
- `brane-core/src/main/java/io/brane/core/model/MulticallResult.java:11-14`

**Problem:** Neither record validates its fields.

**Acceptance Criteria:**
- [ ] Call3: Add compact constructor validating `target` and `callData` are non-null
- [ ] MulticallResult: Add compact constructor validating `returnData` is non-null
- [ ] Add tests for both

---

### HIGH-5: AccessListWithGas Missing Validation

**File:** `brane-core/src/main/java/io/brane/core/model/AccessListWithGas.java:42-44`

**Problem:** No validation, and `accessList` should be defensively copied.

**Acceptance Criteria:**
- [ ] Add compact constructor with null checks for `accessList` and `gasUsed`
- [ ] Make defensive copy: `accessList = List.copyOf(accessList)`
- [ ] Add test for validation

---

### HIGH-6: ChainProfile Missing Validation and Documentation

**File:** `brane-core/src/main/java/io/brane/core/chain/ChainProfile.java:5-15`

**Problem:** No validation for chainId (should be positive), no null check for defaultRpcUrl.

**Acceptance Criteria:**
- [ ] Add compact constructor validating `chainId > 0`
- [ ] Validate `defaultRpcUrl` is non-null and non-empty
- [ ] Validate `defaultPriorityFeePerGas` is non-null
- [ ] Add Javadoc documenting each field's purpose and constraints

---

### HIGH-7: ChainProfiles Hardcoded API Key Placeholder

**File:** `brane-core/src/main/java/io/brane/core/chain/ChainProfiles.java:12`

**Problem:** Sepolia profile has hardcoded `YOUR_KEY` placeholder which will fail at runtime.

```java
public static final ChainProfile ETH_SEPOLIA =
        ChainProfile.of(11155111L, "https://sepolia.infura.io/v3/YOUR_KEY", true, Wei.of(1_000_000_000L));
```

**Acceptance Criteria:**
- [ ] Replace with a public RPC endpoint OR remove Sepolia profile entirely
- [ ] Add Javadoc warning that users must configure their own RPC URL
- [ ] Consider making `defaultRpcUrl` nullable with documentation that users should provide their own

---

### HIGH-8: AbiDecoder Missing Array Bounds Validation

**File:** `brane-core/src/main/java/io/brane/core/abi/AbiDecoder.java:77-78`

**Problem:** When decoding dynamic types, `intValueExact()` is used on offset which can throw `ArithmeticException` without a clear error message. Also no validation that offset is within data bounds.

**Acceptance Criteria:**
- [ ] Validate `absoluteOffset < data.length` before passing to decodeDynamic
- [ ] Wrap `intValueExact()` to throw `AbiDecodingException` with context: "Offset too large for int: {value}"
- [ ] Add test for malicious/corrupted ABI data with out-of-bounds offsets
- [ ] Add test for offset that exceeds Integer.MAX_VALUE

---

### HIGH-9: InternalAbi Duplicate Conversion of Value to BigInteger

**File:** `brane-core/src/main/java/io/brane/core/abi/InternalAbi.java:650-655`

**Problem:** In the bytes converter, `getContentSize` calls `toBytes(value, true)` to get length, then `encodeContent` calls it again. This is wasteful for large byte arrays.

**Acceptance Criteria:**
- [ ] Optimize `getContentSize` to calculate byte length directly from input type without creating intermediate Bytes object
- [ ] For `byte[]` input: use `array.length` directly
- [ ] For `HexData` input: use `hexData.byteLength()` directly
- [ ] Add benchmark or test verifying no regression

---

### HIGH-10: RevertException Constructor Allows Null Kind

**File:** `brane-core/src/main/java/io/brane/core/error/RevertException.java:44-53`

**Problem:** The constructor doesn't validate parameters. `kind` can be null which affects the message formatting.

**Acceptance Criteria:**
- [ ] Add `Objects.requireNonNull(kind, "kind")` in constructor
- [ ] Document expected nullability of `revertReason`, `rawDataHex`, and `cause` parameters
- [ ] Add test for null kind rejection

---

## MEDIUM PRIORITY (16 Issues)

### MED-1: Int Record Has Duplicated Comment Lines

**File:** `brane-core/src/main/java/io/brane/core/abi/Int.java:18-23`

**Problem:** Copy-paste error with duplicated comments.

**Acceptance Criteria:**
- [ ] Remove duplicate comment block (lines 21-23)

---

### MED-2: FastAbiEncoder Has Orphaned Javadoc Comment

**File:** `brane-core/src/main/java/io/brane/core/abi/FastAbiEncoder.java:259-266`

**Problem:** Two consecutive Javadoc comments for the same method, and a comment `// ... primitives ...` that appears leftover.

**Acceptance Criteria:**
- [ ] Remove duplicate Javadoc comment at line 259-265
- [ ] Remove orphaned `// ... primitives ...` comment at line 339 if present

---

### MED-3: InternalAbi TypeConverter Has Duplicate Comment

**File:** `brane-core/src/main/java/io/brane/core/abi/InternalAbi.java:696-697`

**Problem:** Duplicate comment in bytesN encoding.

**Acceptance Criteria:**
- [ ] Remove duplicate comment "// Static bytes are just right-padded"

---

### MED-4: Topics.fromAddress Uses String Concatenation Instead of Hex Utilities

**File:** `brane-core/src/main/java/io/brane/core/util/Topics.java:52-54`

**Problem:** Hardcoded padding string is error-prone.

```java
String padded = "000000000000000000000000" + cleanAddress;  // Magic string
```

**Acceptance Criteria:**
- [ ] Extract padding to named constant: `private static final String ZERO_PADDING_12_BYTES = "0".repeat(24);`
- [ ] Add comment explaining why 12 bytes (24 hex chars) of padding is needed

---

### MED-5: Wei.fromEther Can Throw ArithmeticException Without Context

**File:** `brane-core/src/main/java/io/brane/core/types/Wei.java:40-43`

**Problem:** `toBigIntegerExact()` throws `ArithmeticException` if the value has a fractional part after multiplication. Users get no context.

**Acceptance Criteria:**
- [ ] Wrap in try-catch and throw `IllegalArgumentException` with message: "Ether value {value} results in fractional wei"
- [ ] OR document the @throws ArithmeticException in Javadoc
- [ ] Add test for fractional ether conversion

---

### MED-6: Wei.gwei Allows Negative Values (Late Validation)

**File:** `brane-core/src/main/java/io/brane/core/types/Wei.java:47-49`

**Problem:** Validation happens in constructor, but error message won't mention "gwei" - just says "Wei value cannot be negative".

**Acceptance Criteria:**
- [ ] Add early validation: `if (gwei < 0) throw new IllegalArgumentException("gwei cannot be negative")`
- [ ] Add test for negative gwei input

---

### MED-7: Signature.bytesToHex Magic Number

**File:** `brane-core/src/main/java/io/brane/core/crypto/Signature.java:107-116`

**Problem:** Magic number 8 in toString logic is unexplained.

```java
if (bytes.length > 8) {  // Why 8?
```

**Acceptance Criteria:**
- [ ] Extract to constant: `private static final int MAX_BYTES_TO_DISPLAY = 8;`
- [ ] Add comment explaining the choice (e.g., "8 bytes = 16 hex chars, fits nicely in logs")

---

### MED-8: FastSigner Recursive Call on s == 0

**File:** `brane-core/src/main/java/io/brane/core/crypto/FastSigner.java:99-107`

**Problem:** If s == 0 (extremely rare), the method recursively calls itself with the same inputs. RFC 6979 says to continue with next K, not restart. Since k is deterministic from inputs, this would infinite loop.

**Acceptance Criteria:**
- [ ] Review RFC 6979 specification for handling s == 0
- [ ] If recursive call is correct, add comment explaining why
- [ ] If not, implement proper K iteration per RFC 6979
- [ ] Add comment explaining why this case is practically unreachable

---

### MED-9: TxBuilder.from() Allows Null Without Documentation

**File:** `brane-core/src/main/java/io/brane/core/builder/TxBuilder.java:38-40`

**Problem:** The `from()` method allows setting null, but a transaction requires a sender.

**Acceptance Criteria:**
- [ ] Document that `from` must be non-null for transaction to build successfully
- [ ] Ensure error message in `build()` is clear: "from address is required"
- [ ] Add @param Javadoc with nullability info

---

### MED-10: LegacyBuilder and Eip1559Builder Have Identical validateTarget()

**Files:**
- `brane-core/src/main/java/io/brane/core/builder/LegacyBuilder.java:104-112`
- `brane-core/src/main/java/io/brane/core/builder/Eip1559Builder.java:138-146`

**Problem:** Both builders have identical `validateTarget()` methods, violating DRY.

**Acceptance Criteria:**
- [ ] Extract to a static utility method or default method in shared interface
- [ ] OR document why duplication is intentional (if it is)

---

### MED-11: Eip1559Builder.validateTarget Has Inconsistent Formatting

**File:** `brane-core/src/main/java/io/brane/core/builder/Eip1559Builder.java:138-146`

**Problem:** Empty line before `if (to == null && data == null)` that's not in LegacyBuilder.

**Acceptance Criteria:**
- [ ] Remove empty line for consistency with LegacyBuilder

---

### MED-12: Array Record byteSize() Returns 0 for Empty Static Arrays

**File:** `brane-core/src/main/java/io/brane/core/abi/Array.java:48-56`

**Problem:** `byteSize()` returns 0 for empty static arrays. Need to verify if this is correct per ABI spec.

**Acceptance Criteria:**
- [ ] Verify ABI spec behavior for empty static arrays (`T[0]`)
- [ ] Add test case for `T[0]` type arrays
- [ ] Document the edge case behavior

---

### MED-13: TransactionRequest Does Not Validate from in Record

**File:** `brane-core/src/main/java/io/brane/core/model/TransactionRequest.java:63-74`

**Problem:** While `toUnsignedTransaction()` validates `from`, the record itself allows null `from` which is documented as "(required)".

**Acceptance Criteria:**
- [ ] Either add compact constructor with `Objects.requireNonNull(from, "from is required")`
- [ ] OR update documentation to clarify when null is acceptable

---

### MED-14: InternalAbi Redundant Null Check After asText("")

**File:** `brane-core/src/main/java/io/brane/core/abi/InternalAbi.java:833`

**Problem:** `asText("")` never returns null, making the null check dead code.

```java
if (stateMutability == null || stateMutability.isBlank()) {
```

**Acceptance Criteria:**
- [ ] Remove redundant null check: `if (stateMutability.isBlank())`

---

### MED-15: BraneDebug.isEnabled() Non-Atomic Read

**File:** `brane-core/src/main/java/io/brane/core/BraneDebug.java:14-16`

**Problem:** While fields are volatile, the compound check `rpcLogging || txLogging` is not atomic.

**Acceptance Criteria:**
- [ ] Document that this is acceptable (best-effort logging, no correctness impact)
- [ ] OR use AtomicInteger with bit flags for atomic check

---

### MED-16: LogSanitizer Regex Not Precompiled

**File:** `brane-core/src/main/java/io/brane/core/LogSanitizer.java:34-45`

**Problem:** Regex patterns are compiled on every call to `sanitize()`.

**Acceptance Criteria:**
- [ ] Precompile patterns as `private static final Pattern` constants
- [ ] Use `pattern.matcher(input).replaceAll(replacement)` for better performance
- [ ] Add benchmark or test verifying improvement

---

## LOW PRIORITY (10 Issues)

### LOW-1: Missing Javadoc on Several Public Records

**Files:**
- `ChainProfile.java` - No class-level Javadoc
- `AccessListWithGas.java` - Has Javadoc but missing `@param` tags
- `Call3.java` - Minimal documentation

**Acceptance Criteria:**
- [ ] Add comprehensive Javadoc with `@param` tags to all three files

---

### LOW-2: AnsiColors.hash() Method Does Nothing Useful

**File:** `brane-core/src/main/java/io/brane/core/AnsiColors.java:105-109`

**Problem:** The method claims to "possibly shorten" the hash but always returns it unchanged.

**Acceptance Criteria:**
- [ ] Either implement shortening logic (first 8 chars + ... + last 4)
- [ ] OR rename to `nullSafe()` if that's the intent
- [ ] OR remove misleading comment

---

### LOW-3: DebugLogger Duplicates TTY Check from AnsiColors

**File:** `brane-core/src/main/java/io/brane/core/DebugLogger.java:14-15`

**Problem:** Same TTY detection logic exists in both classes.

**Acceptance Criteria:**
- [ ] Extract to shared constant in AnsiColors: `public static final boolean IS_TTY`
- [ ] Reuse in DebugLogger

---

### LOW-4: LogFormatter.CONTINUATION_INDENT Documentation Incorrect

**File:** `brane-core/src/main/java/io/brane/core/LogFormatter.java:128`

**Problem:** The 10-space indent documentation doesn't match the actual alignment calculation.

**Acceptance Criteria:**
- [ ] Fix documentation to accurately explain the indent purpose
- [ ] OR make indent dynamically calculated based on prefix length

---

### LOW-5: RevertDecoder mapPanicReason Uses Ambiguous Hex Codes

**File:** `brane-core/src/main/java/io/brane/core/RevertDecoder.java:208-222`

**Problem:** Using string comparison for hex codes is ambiguous (is "11" hex 0x11=17 or decimal 11?).

**Acceptance Criteria:**
- [ ] Compare against BigInteger constants for clarity
- [ ] OR compare integer values: `switch (code.intValue())`
- [ ] Add comment clarifying these are hex values (0x01, 0x11, 0x12, etc.)

---

### LOW-6: TypeSchema.ArraySchema Missing fixedLength Validation

**File:** `brane-core/src/main/java/io/brane/core/abi/TypeSchema.java:149-152`

**Problem:** `fixedLength` can be any negative number, but only -1 is documented as meaning "dynamic".

**Acceptance Criteria:**
- [ ] Validate `fixedLength >= -1`: `if (fixedLength < -1) throw new IllegalArgumentException()`
- [ ] OR use a separate boolean `isDynamic` instead of magic value

---

### LOW-7: Bytes.typeName() Recalculates Length Each Call

**File:** `brane-core/src/main/java/io/brane/core/abi/Bytes.java:37-39`

**Problem:** Calculates byte length from hex string length every time instead of using `value.byteLength()`.

**Acceptance Criteria:**
- [ ] Use `value.byteLength()` instead of manual calculation
- [ ] Consider caching typeName for static bytes types if called frequently

---

### LOW-8: Transaction.nonce Uses Boxed Long Despite Being Required

**File:** `brane-core/src/main/java/io/brane/core/model/Transaction.java:54`

**Problem:** `nonce` is documented as required and validated non-null, but uses `Long` instead of primitive `long`.

**Acceptance Criteria:**
- [ ] Use primitive `long nonce` since it's always required
- [ ] Keep `Long blockNumber` since it can be null for pending transactions

---

### LOW-9: Missing @since Tags on Public Classes

**Files:** Most files in `io.brane.core.model`, `io.brane.core.builder`, `io.brane.core.util`

**Problem:** Inconsistent use of `@since` Javadoc tags.

**Acceptance Criteria:**
- [ ] Add `@since 0.1.0` (or appropriate version) to all public classes

---

### LOW-10: Inconsistent Collectors.joining() Usage

**File:** `brane-core/src/main/java/io/brane/core/abi/Tuple.java:33`

**Problem:** Uses `Collectors.joining()` which is fine, but pattern varies across codebase.

**Acceptance Criteria:**
- [ ] Standardize on `Collectors.joining()` pattern throughout codebase
- [ ] OR document preferred approach in style guide

---

## Summary

| Severity | Count | Status |
|----------|-------|--------|
| Critical | 3 | TODO |
| High | 10 | TODO |
| Medium | 16 | TODO |
| Low | 10 | TODO |
| **Total** | **39** | |

---

## What's Well Done

1. **Strong Java 21 Adoption** - Excellent use of records, sealed interfaces, pattern matching in switch expressions, and text blocks.

2. **Well-Designed Exception Hierarchy** - `BraneException` as a sealed base class with specific subclasses enables exhaustive handling.

3. **Thread-Safety Considerations** - ThreadLocal for Keccak256, volatile fields in BraneDebug, defensive copies in Signature.

4. **Immutability Focus** - Most types are records or have immutable semantics.

5. **Security Awareness** - LogSanitizer for redacting private keys, PrivateKey implementing Destroyable, zeroing key material.

6. **Performance Optimizations** - FastAbiEncoder's two-pass encoding, HexData's lazy string generation, FastSigner avoiding recovery.

7. **Good Documentation** - Several classes have excellent Javadoc with examples (Abi, Signature, PrivateKey, RevertDecoder).

8. **Consistent Validation Patterns** - Types like Address, Hash, Wei all validate in compact constructors.

9. **Clean Builder Pattern** - TxBuilder interface with LegacyBuilder and Eip1559Builder implementations.

10. **Comprehensive Logging** - BraneDebug, LogFormatter, LogSanitizer provide good observability.
