# brane Project PR Review Rules

## Auto-Approval Policy
AUTO_APPROVE_BLOCK_SEVERITY: HIGH
AUTO_APPROVE_MAX_FINDINGS: 0
AUTO_APPROVE_MAX_AGREEMENTS: 0

## Repository Context
- Brane is a Java 21 EVM SDK with strict layering: `brane-primitives` -> `brane-core` -> `brane-rpc` -> `brane-contract`.
- Public APIs should expose JDK + Brane domain types, not transport/vendor internals.
- Integration and smoke flows intentionally target local Anvil (`127.0.0.1:8545`) and may use the standard Anvil test key.

## CRITICAL
- Flag secrets or credentials in code, config, tests, examples, docs, or CI logs (private keys, seed phrases, API tokens, authenticated RPC URLs). Exception: the well-known Anvil default test key.
- Flag any leak of vendored web3j or other internal implementation types into public APIs (public methods, constructors, fields, record components, return types). `sh.brane.internal.web3j.*` must remain internal.
- Flag module-boundary violations where lower modules import higher-level modules.
- Flag unsafe key-material handling (`PrivateKey` not destroyed when lifecycle ends, sensitive byte arrays not zeroed after handoff).
- Flag thread-safety violations in shared/public client types or cache paths used concurrently.

## HIGH
- Flag public API use of raw `String` for addresses/hashes/calldata when `Address`, `Hash`, `HexData`, or `Wei` should be used.
- Flag incorrect `BigInteger` findings: allow `BigInteger` where Solidity ABI integer surfaces are intentional (notably contract bindings); flag only when a Brane domain type is the intended abstraction.
- Flag pre-Java-21 style in new/changed code where Java 21 equivalents are expected (records for immutable data carriers, switch expressions, pattern matching for `instanceof`).
- Flag exception-handling regressions: swallowed exceptions, lost cause chains, or generic `Exception` handling in public API paths.
- Flag null/optional correctness issues in public APIs (`Objects.requireNonNull` gaps, unsafe `Optional.get()`).
- Flag missing `Keccak256.cleanup()` in long-lived pooled/virtual-thread execution paths after hashing work.
- Flag public API changes without corresponding docs/examples updates (`README.md`, docs under `website/docs/pages/docs`, or relevant module docs) when behavior or signatures change.
- Flag feature changes without appropriate test-layer coverage per `TESTING.md` (unit for logic, integration/smoke for cross-component behavior).

## MEDIUM
- Flag raw generic types and unclear `var` usage where type intent becomes ambiguous.
- Flag allocation regressions in hot paths when existing zero-allocation alternatives exist (for example `*To()` methods, `Address.ZERO`, `Wei.ZERO`, `HexData.EMPTY`).
- Flag missing or incorrect Javadoc `<b>Allocation:</b>` tags on new public methods in performance-sensitive modules.
- Flag dead code, commented-out code blocks, stale TODO artifacts, or temporary debug output introduced by the diff.
- Flag avoidable API drift from existing project idioms (naming, record validation in compact constructors, immutable return values).

## Never Flag
- Formatting-only churn handled by formatter/IDE.
- Generated or vendored third-party updates (for example `foundry/anvil-tests/lib/**`) without behavioral edits to Brane-owned code.
- Existing baseline issues unchanged by the diff.
- Test/integration code that intentionally uses the standard Anvil default private key (`0xac0974...`).
