# Brane SDK Performance Benchmarks

This document tracks the performance of the Brane SDK's core components (ABI encoding, Transaction Signing) compared to Web3j.

**Last Updated:** 2025-11-30
**Environment:** Apple M3 Pro, OpenJDK 21

## Executive Summary

Recent optimizations have significantly improved performance across the board:
*   **ABI Encoding**: **Parity (0.94x)** with Web3j for complex data types.
*   **Constructor Encoding**: **2.5x speedup** (optimized with direct buffer encoding).
*   **Transaction Signing**: **4x faster** for EIP-1559 transactions.

## Detailed Results

### 1. ABI Encoding (Data)

Measures the throughput of encoding complex nested structures (Tuples, Arrays, dynamic bytes).

| Benchmark | Brane (ops/s) | Web3j (ops/s) | Speedup | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `encodeComplex` | **603,038** | 639,558 | **0.94x** | Optimized `HexData` and decentralized size calc. Near parity. |

### 2. ABI Encoding (Functions & Constructors)

Measures the throughput of encoding function calls and constructor arguments, including method ID generation and selector hashing.

| Benchmark | Brane (ops/s) | Web3j (ops/s) | Speedup | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `encodeFunction` | 458,689 | N/A | N/A | Includes Keccak256 overhead for selector. |
| `encodeConstructor` | **91,082,314** | 36,554,895 | **2.49x** | Optimized with direct `ByteBuffer` encoding and lazy `HexData`. |

> **Note:** Brane's `encodeConstructor` consistently outperforms Web3j due to direct buffer encoding and avoiding intermediate object allocations.

### 3. Transaction Signing

Measures the throughput of signing transactions (hashing + ECDSA signature generation).

| Benchmark | Brane (ops/s) | Web3j (ops/s) | Speedup | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `signLegacy` | **19,994** | 7,850 | **2.55x** | Legacy (pre-EIP-155) signing. |
| `signEip1559` | **19,435** | 4,820 | **4.03x** | EIP-1559 signing. |
| `signLargePayload` | **14,203** | 6,330 | **2.24x** | Signing 10KB data payload. |

## Optimization Strategies

### FastAbiEncoder
*   **Two-Pass Encoding**: Calculates exact buffer size first, then writes directly. Eliminates all intermediate byte array allocations and `System.arraycopy` calls.
*   **Zero-Copy**: Writes primitive values directly to the output buffer.

### FastSigner
*   **Direct Recovery ID**: Calculates `v` directly from the curve point `R` during signing, avoiding the expensive public key recovery step required by standard libraries.
*   **ThreadLocal Hashing**: Reuses Keccak-256 digest instances to reduce object churn.

### InternalAbi
*   **Pre-computed Converters**: Parses ABI type strings once at initialization time, creating a chain of `TypeConverter` lambdas.
*   **Trusted HexData**: Skips regex validation for internally generated hex strings (e.g., from `AbiEncoder`).
