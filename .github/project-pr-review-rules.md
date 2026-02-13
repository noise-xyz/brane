# brane Project PR Review Rules

## Auto-Approval Policy
AUTO_APPROVE_BLOCK_SEVERITY: HIGH
AUTO_APPROVE_MAX_FINDINGS: 0
AUTO_APPROVE_MAX_AGREEMENTS: 0

## CRITICAL
- Flag secrets or credentials in code, config, tests, or examples (private keys, API tokens, RPC endpoint credentials). Exception: the well-known Anvil default test key.
- Flag web3j types leaked into public API surfaces (public methods, constructors, fields, return types); web3j is vendored under `sh.brane.internal.web3j.*` and must never be exposed.
- Flag thread-safety violations in public API types that are documented as thread-safe (race conditions, unsynchronized shared mutable state).

## HIGH
- Flag raw `String` or `BigInteger` usage in public API where Brane domain types exist (`Address`, `Wei`, `Hash`, `HexData`).
- Flag pre-Java-21 patterns where Java 21 equivalents are required: mutable classes instead of records for data types, if-else chains instead of switch expressions, separate instanceof+cast instead of pattern matching.
- Flag exception handling violations: swallowed exceptions (empty catch), catching generic `Exception` in public API, lost cause chains (missing cause parameter in re-thrown exceptions).
- Flag null-safety gaps: missing `Objects.requireNonNull` on public API parameters, `Optional.get()` without presence check.
- Flag module dependency violations: lower modules (`brane-primitives`, `brane-core`) importing from higher modules (`brane-rpc`, `brane-contract`).
- Flag missing Keccak256.cleanup() in code paths that run on pooled or virtual threads.
- Flag PrivateKey lifecycle issues: missing `key.destroy()` calls, or failing to zero input byte arrays.

## MEDIUM
- Flag raw types (`List` instead of `List<LogEntry>`, `Map` instead of `Map<String, Object>`).
- Flag `var` usage where the type is not obvious from the right-hand side expression.
- Flag allocation regressions in hot paths: unnecessary object creation where zero-allocation `*To()` variants or singleton constants (`Address.ZERO`, `Wei.ZERO`) exist.
- Flag missing or incorrect Javadoc `<b>Allocation:</b>` tags on new public methods in performance-sensitive modules.
- Flag dead code, commented-out blocks, or debug artifacts introduced by the change.

## Never Flag
- Formatting-only churn handled by IDE/formatter.
- Generated files or Gradle lockfile updates without behavioral changes.
- Existing baseline issues unchanged by the diff.
- Test code using the well-known Anvil default private key (`0xac0974...`).
