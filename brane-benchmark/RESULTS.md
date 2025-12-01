# Brane SDK Performance Benchmarks

This document tracks the performance of the Brane SDK's core components (ABI encoding, Transaction Signing) compared to Web3j.

**Last Updated:** 2025-11-30
**Environment:** Apple M3 Pro, OpenJDK 21

## Executive Summary

Recent optimizations have significantly improved performance across the board:
*   **ABI Encoding**: **3.12x faster** than Web3j for complex data types.
*   **Constructor Encoding**: **10.5x speedup** (gap reduced from 19x to 1.8x).
*   **Transaction Signing**: **4x faster** for EIP-1559 transactions.

## Detailed Results

### 1. ABI Encoding (Data)

Measures the throughput of encoding complex nested structures (Tuples, Arrays, dynamic bytes).

| Benchmark | Brane (ops/s) | Web3j (ops/s) | Speedup | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `encodeComplex` | **2,012,230** | 645,049 | **3.12x** | Uses `FastAbiEncoder` (Zero-Allocation strategy). |

### 2. ABI Encoding (Functions & Constructors)

Measures the throughput of encoding function calls and constructor arguments, including method ID generation and selector hashing.

| Benchmark | Brane (ops/s) | Web3j (ops/s) | Speedup | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `encodeFunction` | 458,689 | N/A | N/A | Includes Keccak256 overhead for selector. |
| `encodeConstructor` | **20,630,846** | 37,465,940 | **0.55x** | Optimized with pre-computed type converters and trusted HexData. |

> **Note:** The `encodeConstructor` gap (1.8x slower) is due to Brane performing full schema validation against the ABI, whereas Web3j's benchmark performs primitive encoding without schema validation.

### 3. Transaction Signing

Measures the throughput of signing transactions (hashing + ECDSA signature generation).

| Benchmark | Brane (ops/s) | Web3j (ops/s) | Speedup | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `signLegacy` | **19,998** | 7,848 | **2.54x** | Legacy (pre-EIP-155) signing. |
| `signEip1559` | **19,486** | 4,850 | **4.01x** | EIP-1559 signing. |
| `signLargePayload` | **11,240** | 6,285 | **1.78x** | Signing 10KB data payload. |

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
