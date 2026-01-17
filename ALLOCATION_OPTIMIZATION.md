# Allocation-Conscious Brane SDK

## Benefits

### Performance Gains

| Metric | Before | After (Estimated) | Impact |
|--------|--------|-------------------|--------|
| Allocations per ABI encode | 5-8 objects | 1-2 objects | 75% reduction |
| Allocations per Address.from() | 3 objects | 1 object | 66% reduction |
| GC pause frequency | Baseline | 2-5x less frequent | Lower latency tail |
| Throughput (ops/sec) | Baseline | 10-30% higher | More work per core |

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

| Operation | Current Allocations | Root Cause |
|-----------|---------------------|------------|
| `Address.from("0x...")` | 3 | Record + substring + byte[] |
| `Hex.encode(bytes)` | 2 | char[] + String |
| `Hex.decode(hex)` | 2 | substring + byte[] |
| ABI encode uint256 | 1+ per value | BigInteger.valueOf() boxing |
| `HexData.toBytes()` | 1 | Defensive copy every call |

---

## Implementation Plan

### Phase 1: Measure Baseline

- [ ] Create `AllocationBenchmark.java` with `-prof gc` profiling
- [ ] Measure allocations for: Address.from(), Hex encode/decode, ABI encode

### Phase 2: Quick Wins

- [ ] Add singleton constants (`Address.ZERO`, `Wei.ZERO`, `Wei.ONE_ETHER`)
- [ ] Cache `toBytes()` in Address/Hash (lazy init like HexData)
- [ ] Primitive fast-paths in `FastAbiEncoder.encodeUint256(long, ByteBuffer)`
- [ ] Avoid substring in `Hex.decode` - use index-based parsing

### Phase 3: Low-Level API (Optional)

- [ ] Add `Hex.encodeTo(bytes, charBuffer, offset)` for allocation-free encoding
- [ ] Add `Hex.decodeTo(hex, offset, length, byteBuffer)` for allocation-free decoding
- [ ] Add `FastAbiEncoder.encodeTo(schema, args, byteBuffer)` for direct buffer encoding

### Phase 4: Documentation

- [ ] Add "Performance" section to README with benchmark results
- [ ] Document allocation budgets for each operation
- [ ] Compare with alloy/viem on key metrics

---

## Verification

```bash
# Run allocation benchmarks
./gradlew :brane-benchmark:jmh -Pbenchmark=Allocation

# Run all tests
./gradlew test

# Full verification
./verify_all.sh
```
