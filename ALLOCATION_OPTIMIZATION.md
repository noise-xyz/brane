# Allocation-Conscious Brane SDK

## Benefits

### Performance Gains

| Metric | Before | After (Estimated) | Impact |
|--------|--------|-------------------|--------|
| Allocations per ABI encode (long) | 2+ objects | 0 objects | 100% reduction |
| Allocations per Hex.decode() | 2 objects | 1 object | 50% reduction |
| GC pause frequency | Baseline | 2-3x less frequent | Lower latency tail |
| Throughput (ops/sec) | Baseline | 10-20% higher | More work per core |

### Competitive Differentiation

| SDK | Language | Allocation Story |
|-----|----------|------------------|
| alloy | Rust | Zero-cost abstractions (no GC) |
| viem | TypeScript | V8 optimized, but still GC-bound |
| **Brane** | Java 21 | **Allocation-conscious + virtual threads** |

**Unique selling points:**
- Only JVM SDK with documented allocation budgets
- Measurable, benchmarked performance claims
- Ergonomic high-level API + optional low-allocation paths
- Future-ready for Project Valhalla value types

### User Experience Benefits

1. **Predictable latency** - Fewer GC pauses = smoother p99 latencies
2. **Higher throughput** - Less allocation = more CPU for actual work
3. **Lower memory footprint** - Less garbage = smaller heap required
4. **Transparency** - Published allocation benchmarks build trust

---

## Current State

### Already Optimized (Keep)

| Pattern | Location | Benefit |
|---------|----------|---------|
| ThreadLocal digest reuse | `Keccak256.java:72-90` | Zero alloc per hash |
| Two-pass ABI encoding | `FastAbiEncoder.java:56` | Single pre-sized buffer |
| Lazy string caching | `HexData.java:124-135` | Deferred allocation |
| Netty pooled buffers | `WebSocketProvider.java:485` | Buffer reuse |
| Disruptor ring buffer | `WebSocketProvider.java:1472-1509` | Pre-allocated events |

### Hot Spots to Fix

| Operation | Current Allocations | Root Cause | Status |
|-----------|---------------------|------------|--------|
| `Hex.decode(hex)` | 2 | substring + byte[] | **Task 2.4** |
| ABI encode uint256 | 1+ per value | BigInteger.valueOf() boxing | **Task 2.3** |
| `Hex.encode(bytes)` | 2 | char[] + String | Phase 3 (optional) |

### Accepted Allocations (By Design)

| Operation | Allocations | Rationale |
|-----------|-------------|-----------|
| `Address.from("0x...")` | 2 | Record + toLowerCase(); records are immutable, no caching |
| `Hash.from("0x...")` | 2 | Record + toLowerCase(); same rationale as Address |
| `HexData.toBytes()` | 1 | Defensive copy preserves immutability; use `putTo(ByteBuffer)` for zero-alloc |
| `Address.toBytes()` | 1 | Returns decoded bytes; cache at application level if needed |

### Design Decision: HexData.toBytes() Defensive Copy

The `HexData.toBytes()` method allocates a defensive copy on every call. This is **intentional**
and should not be "optimized" away because:

1. **Immutability guarantee**: `HexData` is designed as an immutable type. Returning the internal
   `raw` array directly would allow callers to mutate it, breaking this guarantee.

2. **Thread safety**: Multiple threads can safely call `toBytes()` without coordination because
   each gets its own copy.

3. **API consistency**: Users expect `toBytes()` to return a "safe" array they can modify.

**For zero-allocation paths**, users who need to avoid this copy should use:
- `HexData.putTo(ByteBuffer)` - writes directly to a buffer without intermediate allocation
- Future: `HexData.toBytesDirect()` (unsafe, caller promises not to mutate) - if demand exists

---

## Implementation Plan

---

### Phase 1: Measure Baseline

**Goal:** Establish allocation baselines for all hot paths before optimization.

#### Task 1.1: Create AllocationBenchmark.java

| Field | Value |
|-------|-------|
| **Module** | `brane-benchmark` |
| **Add** | `brane-benchmark/src/jmh/java/sh/brane/benchmark/AllocationBenchmark.java` |
| **Modify** | None |

**Description:**
Create JMH benchmark class measuring allocations per operation using `-prof gc`.

**Acceptance Criteria:**
- [ ] Benchmark includes: `Hex.encode()`, `Hex.decode()` (operations being optimized in Phase 2-3)
- [ ] Each benchmark method annotated with `@Benchmark` and `@BenchmarkMode(Mode.AverageTime)`
- [ ] Uses `@State(Scope.Thread)` for test data setup
- [ ] Can run via `./gradlew :brane-benchmark:jmh -Pbenchmark=Allocation`
- [ ] Output includes `gc.alloc.rate.norm` (bytes allocated per operation)

**Example:**
```java
@Benchmark
public byte[] hexDecode() {
    return Hex.decode("0x742d35Cc6634C0532925a3b844Bc454e4438f44e");
}

@Benchmark
public String hexEncode() {
    return Hex.encode(testBytes); // testBytes set up in @State
}
```

#### Task 1.2: Create AbiAllocationBenchmark.java

| Field | Value |
|-------|-------|
| **Module** | `brane-benchmark` |
| **Add** | `brane-benchmark/src/jmh/java/sh/brane/benchmark/AbiAllocationBenchmark.java` |
| **Modify** | None |

**Description:**
Dedicated benchmark for ABI encoding/decoding allocation measurement.

**Acceptance Criteria:**
- [ ] Benchmarks: `encodeUint256`, `encodeAddress`, `encodeBytes`, `encodeTuple`, `decodeUint256`
- [ ] Tests both `FastAbiEncoder` and `InternalAbi` paths
- [ ] Measures allocation for simple (single arg) and complex (nested struct) cases
- [ ] Documents baseline allocations in comments after first run

#### Task 1.3: Document Baseline Results

| Field | Value |
|-------|-------|
| **Module** | Root |
| **Modify** | `ALLOCATION_OPTIMIZATION.md` |

**Description:**
Run benchmarks and record baseline allocation rates in this document.

**Acceptance Criteria:**
- [ ] Table updated with actual measured allocations per operation
- [ ] Benchmark run with JDK 21, G1GC, on consistent hardware
- [ ] Results reproducible (variance < 10%)

---

### Phase 2: Quick Wins

**Goal:** Reduce allocations with minimal API changes.

#### Task 2.1: Add Address.ZERO Constant

| Field | Value |
|-------|-------|
| **Module** | `brane-core` |
| **Modify** | `brane-core/src/main/java/sh/brane/core/types/Address.java` |

**Description:**
Add static constant for zero address to avoid repeated allocations.

**Acceptance Criteria:**
- [ ] `public static final Address ZERO = new Address("0x0000000000000000000000000000000000000000");`
- [ ] Javadoc documents the constant
- [ ] Existing tests pass
- [ ] Add test verifying `Address.ZERO.value()` returns expected string

#### Task 2.2: Add Wei Constants

| Field | Value |
|-------|-------|
| **Module** | `brane-core` |
| **Modify** | `brane-core/src/main/java/sh/brane/core/types/Wei.java` |

**Description:**
Add commonly used Wei constants. Note: `Wei.ZERO` already exists.

**Acceptance Criteria:**
- [ ] Add `public static final Wei ONE_WEI = new Wei(BigInteger.ONE);`
- [ ] Add `public static final Wei ONE_GWEI = Wei.gwei(1);`
- [ ] Add `public static final Wei ONE_ETHER = Wei.fromEther(BigDecimal.ONE);`
- [ ] Javadoc documents each constant
- [ ] Add tests verifying constant values

### Design Decision: Address/Hash toBytes() Allocation

**Decision:** Keep `Address` and `Hash` as pure records without internal caching.

**Rationale:**
- Records cannot have mutable instance fields (Java language constraint)
- Static caches would introduce mutable state, violating Brane's "no mutable state in public types" principle
- Static caches would still require defensive copies (1 allocation per call) - no actual allocation reduction
- Adding caches would complicate testing (cache pollution between tests)

**For allocation-sensitive code paths**, users should:

1. **Use `HexData` instead** - Already has raw bytes internally + `putTo(ByteBuffer)` for zero-allocation writes
2. **Cache at application level** - If repeatedly calling `toBytes()` on same addresses, cache the result yourself
3. **Use the low-level API** (Phase 3) - `Hex.decodeTo()` writes directly to a provided buffer

**Example - Allocation-conscious pattern:**
```java
// Instead of repeatedly calling address.toBytes()
byte[] addressBytes = address.toBytes(); // Cache this if needed multiple times

// Or use HexData for hot paths
HexData data = new HexData(address.value());
data.putTo(buffer); // Zero allocation write to buffer
```

#### Task 2.3: Primitive Fast-Path in FastAbiEncoder

| Field | Value |
|-------|-------|
| **Module** | `brane-core` |
| **Modify** | `brane-core/src/main/java/sh/brane/core/abi/FastAbiEncoder.java` |

**Description:**
Add overloaded methods accepting primitives instead of `BigInteger` to avoid boxing.

**Changes:**
```java
// Existing (allocates BigInteger)
public static void encodeUint256(BigInteger value, ByteBuffer buffer)

// New (no boxing for values <= Long.MAX_VALUE)
public static void encodeUint256(long value, ByteBuffer buffer) {
    // Direct primitive encoding without BigInteger allocation
}

// Optional: int overload for convenience (common for array indices, counts)
public static void encodeUint256(int value, ByteBuffer buffer) {
    encodeUint256((long) value, buffer);
}
```

**Acceptance Criteria:**
- [ ] New `encodeUint256(long, ByteBuffer)` method added
- [ ] Optional: `encodeUint256(int, ByteBuffer)` convenience overload
- [ ] Handles full range of non-negative long values
- [ ] Zero allocations for encoding primitive values
- [ ] Existing BigInteger path unchanged (backwards compatible)
- [ ] Unit tests for edge cases: 0, 1, Integer.MAX_VALUE, Long.MAX_VALUE
- [ ] Benchmark shows allocation reduction for common uint256 values

#### Task 2.4: Avoid Substring in Hex.decode

| Field | Value |
|-------|-------|
| **Module** | `brane-primitives` |
| **Modify** | `brane-primitives/src/main/java/sh/brane/primitives/Hex.java` |

**Description:**
Replace `substring(2)` with index-based parsing to avoid String allocation.

**Current code (allocates):**
```java
String hex = Hex.cleanPrefix(input); // creates substring
```

**Optimized (no allocation):**
```java
int start = Hex.hasPrefix(input) ? 2 : 0;
// parse from index 'start' directly
```

**Acceptance Criteria:**
- [ ] `decode(String)` no longer calls `substring()`
- [ ] Uses index-based character access: `input.charAt(start + i)`
- [ ] Benchmark shows 1 fewer allocation per decode
- [ ] All existing Hex tests pass
- [ ] Edge cases: empty string, "0x" only, odd-length hex

---

### Phase 3: Low-Level API (Optional)

**Goal:** Provide allocation-free paths for performance-critical users.

#### Task 3.1: Add Hex.encodeTo()

| Field | Value |
|-------|-------|
| **Module** | `brane-primitives` |
| **Modify** | `brane-primitives/src/main/java/sh/brane/primitives/Hex.java` |
| **Add** | Unit tests in `brane-primitives/src/test/java/sh/brane/primitives/HexTest.java` |

**Description:**
Add method that writes hex chars to a pre-allocated buffer.

**Signature:**
```java
/**
 * Encodes bytes to hex characters in the provided buffer.
 * @param bytes source bytes
 * @param dest destination char array
 * @param destOffset starting position in dest
 * @param withPrefix if true, writes "0x" prefix
 * @return number of characters written
 */
public static int encodeTo(byte[] bytes, char[] dest, int destOffset, boolean withPrefix)
```

**Acceptance Criteria:**
- [ ] Zero allocations (writes to provided buffer)
- [ ] Throws if buffer too small
- [ ] Returns number of chars written
- [ ] Works with and without "0x" prefix
- [ ] Benchmark shows zero `gc.alloc.rate.norm`

#### Task 3.2: Add Hex.decodeTo()

| Field | Value |
|-------|-------|
| **Module** | `brane-primitives` |
| **Modify** | `brane-primitives/src/main/java/sh/brane/primitives/Hex.java` |

**Description:**
Add method that decodes hex directly into a pre-allocated byte array.

**Signature:**
```java
/**
 * Decodes hex characters to bytes in the provided buffer.
 * @param hex source hex string
 * @param hexOffset starting position in hex (after any prefix)
 * @param hexLength number of hex chars to decode
 * @param dest destination byte array
 * @param destOffset starting position in dest
 * @return number of bytes written
 */
public static int decodeTo(CharSequence hex, int hexOffset, int hexLength, byte[] dest, int destOffset)
```

**Acceptance Criteria:**
- [ ] Zero allocations
- [ ] Accepts CharSequence (works with String, StringBuilder, etc.)
- [ ] Throws on invalid hex characters
- [ ] Benchmark shows zero `gc.alloc.rate.norm`

#### Task 3.3: Add FastAbiEncoder.encodeTo()

| Field | Value |
|-------|-------|
| **Module** | `brane-core` |
| **Modify** | `brane-core/src/main/java/sh/brane/core/abi/FastAbiEncoder.java` |

**Description:**
Add method that encodes ABI directly to a provided ByteBuffer.

**Signature:**
```java
/**
 * Encodes function call directly to buffer.
 * @param selector 4-byte function selector
 * @param args encoded arguments
 * @param buffer destination buffer (must have sufficient remaining capacity)
 */
public static void encodeTo(byte[] selector, Object[] args, ByteBuffer buffer)
```

**Acceptance Criteria:**
- [ ] Zero allocations beyond the provided buffer
- [ ] Buffer position advanced by encoded length
- [ ] Throws BufferOverflowException if insufficient space
- [ ] Works with direct and heap ByteBuffers

---

### Phase 4: Documentation

**Goal:** Document allocation characteristics for users.

#### Task 4.1: Add Performance Section to README

| Field | Value |
|-------|-------|
| **Module** | Root |
| **Modify** | `README.md` |

**Description:**
Add "Performance" section documenting allocation-conscious design.

**Acceptance Criteria:**
- [ ] Section titled "## Performance"
- [ ] Table of allocation budgets per operation
- [ ] Comparison with alloy/viem where applicable
- [ ] Instructions for running benchmarks
- [ ] Link to detailed `ALLOCATION_OPTIMIZATION.md`

#### Task 4.2: Add Allocation Javadoc

| Field | Value |
|-------|-------|
| **Module** | `brane-core`, `brane-primitives` |
| **Modify** | `Address.java`, `Wei.java`, `Hash.java`, `Hex.java`, `FastAbiEncoder.java` |

**Description:**
Document allocation behavior in Javadoc for key methods.

**Example:**
```java
/**
 * Decodes this address to a 20-byte array.
 * <p>
 * <b>Allocation:</b> First call allocates and caches result.
 * Subsequent calls return cached copy (1 allocation for defensive copy).
 *
 * @return 20-byte array representation
 */
public byte[] toBytes()
```

**Acceptance Criteria:**
- [ ] All public methods in hot paths have `<b>Allocation:</b>` documentation
- [ ] Allocation count documented (e.g., "0 allocations", "1 allocation")
- [ ] Special cases noted (e.g., "cached after first call")

#### Task 4.3: Update CLAUDE.md

| Field | Value |
|-------|-------|
| **Module** | Root |
| **Modify** | `CLAUDE.md` |

**Description:**
Add allocation-conscious patterns to developer guidelines.

**Acceptance Criteria:**
- [ ] New section "## Allocation Guidelines"
- [ ] Documents patterns: singleton constants, lazy caching, primitive fast-paths
- [ ] Lists methods with zero-allocation variants
- [ ] Warns about common allocation pitfalls

---

## Verification

### Running Benchmarks

```bash
# Run all allocation benchmarks
./gradlew :brane-benchmark:jmh -Pbenchmark=Allocation

# Run specific benchmark with GC profiling
./gradlew :brane-benchmark:jmh -Pbenchmark=AllocationBenchmark -PjmhArgs="-prof gc"

# Compare before/after (run on same machine)
./gradlew :brane-benchmark:jmh -Pbenchmark=Allocation -PjmhArgs="-rf json -rff before.json"
# ... make changes ...
./gradlew :brane-benchmark:jmh -Pbenchmark=Allocation -PjmhArgs="-rf json -rff after.json"
```

### Test Commands

```bash
# Unit tests only
./gradlew test

# Full verification (requires Anvil)
./verify_all.sh

# Specific module tests
./gradlew :brane-core:test --tests "sh.brane.core.types.*"
./gradlew :brane-primitives:test --tests "sh.brane.primitives.HexTest"
```

### Acceptance Checklist

- [ ] All Phase 1 benchmarks created and baseline documented
- [ ] All Phase 2 optimizations implemented
- [ ] All existing tests pass (`./gradlew test`)
- [ ] Benchmark shows measurable allocation reduction
- [ ] No API breaking changes (additive only)
- [ ] Javadoc updated for modified methods
- [ ] README performance section added
