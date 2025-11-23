# Phase 2.6: Minimal Revert Decoding (P0.10)

## Goal
Extend `RevertDecoder` to support `Panic(uint256)` and custom errors, and wire it into the `RevertException` thrown by clients. Ensure `RevertException` carries the `RevertKind` to distinguish between errors, panics, and custom reverts. Custom error decoding is supported via an overload, but core clients
(`PublicClient`, `WalletClient`) will continue using the simple `decode(String)` entrypoint.

## Motivation
- **Developer Experience**: "Panic with code 0x11 (arithmetic overflow)" is much better than "EVM revert (no reason)".
- **Completeness**: Support standard Solidity revert types beyond `Error(string)`.
- **Extensibility**: Allow users to decode their own custom errors via a flexible API.

## Implementation Details

### 1. Enhanced `RevertDecoder`
- **Location**: `brane-core/src/main/java/io/brane/core/RevertDecoder.java`
- **New Types**:
  ```java
  public enum RevertKind {
      ERROR_STRING,    // Error(string)
      PANIC,           // Panic(uint256)
      CUSTOM,          // user-defined error
      UNKNOWN
  }

  public record Decoded(
      RevertKind kind,
      String reason,         // human-readable message
      String rawDataHex      // original data
  ) {}
  
  public record CustomErrorAbi(
      String name,
      List<TypeReference<? extends Type>> outputs
  ) {}
  ```
- **Panic Support**:
  - Selector: `0x4e487b71`
  - **Mapping**:
    - `0x01` → "assertion failed"
    - `0x11` → "arithmetic overflow or underflow"
    - `0x12` → "division or modulo by zero"
    - `0x21` → "enum conversion out of range"
    - `0x22` → "invalid storage byte array indexing"
    - `0x31` → "pop on empty array"
    - `0x32` → "array index out of bounds"
    - `0x41` → "memory allocation overflow"
    - `0x51` → "zero-initialized variable of internal function type"
    - Default: "panic with code 0x{hexCode}" (e.g. "panic with code 0xdeadbeef")
- **Custom Error Support**:
  - Add overload: `decode(String rawData, Map<String, CustomErrorAbi> customErrors)`.
  - `decode(String rawData)` remains as the default helper and internally delegates to `decode(rawData, Map.of())` (no custom errors).

### 2. Wired `RevertException`
- **Location**: `brane-core/src/main/java/io/brane/core/error/RevertException.java`
- **Update**: Add `RevertKind` field to the exception.
  ```java
  public final class RevertException extends BraneException {
      private final RevertKind kind;
      // ... existing fields
      
      public RevertKind kind() { return kind; }
  }
  ```
  ```java
  public RevertException(RevertKind kind, String reason, String rawDataHex, Throwable cause) {
      super(messageFor(kind, reason, rawDataHex), cause);
      this.kind = kind;
      this.revertReason = reason;
      this.rawDataHex = rawDataHex;
  }
  ```
- **Integration Logic**:
  - **Only** throw `RevertException` if revert data is present and `kind != UNKNOWN`.
  - `Contract.read(...)` / `PublicClient.call(...)`: Check for RPC error data.
  - `WalletClient`: Check receipt status (0) + revert reason, or RPC error data.

## Steps
1. [ ] Update `RevertDecoder` with `RevertKind`, `Panic` decoding, and `CustomErrorAbi` support.
2. [ ] Update `RevertException` to include `RevertKind`.
3. [ ] Add `RevertDecoderTest` in `brane-core/src/test/java/io/brane/core/RevertDecoderTest.java`.
4. [ ] Wire `RevertDecoder` into `Contract` and `WalletClient` error handling.
5. [ ] Add integration tests in `brane-examples` covering Panic and Revert scenarios.

## Testing
- **Unit Tests (`RevertDecoderTest`)**:
    - `Error(string)` → `RevertKind.ERROR_STRING` with exact message.
    - `Panic(uint256)` → `RevertKind.PANIC` with mapped human-readable reason.
    - Unknown selectors → `RevertKind.UNKNOWN` with `reason == null`.
- **Integration Tests**:
    - `willRevert("msg")` → `RevertException` with `kind == ERROR_STRING` and `revertReason == "msg"`.
    - `willPanic()` (e.g. div by zero) → `RevertException` with `kind == PANIC` and correct panic message.
