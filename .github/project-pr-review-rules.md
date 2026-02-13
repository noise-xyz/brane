# brane Project PR Review Rules

## Auto-Approval Policy
AUTO_APPROVE_BLOCK_SEVERITY: HIGH

## CRITICAL
- Flag secrets, private keys, or mnemonics committed to any file (including test fixtures that aren't the documented default test key).
- Flag PrivateKey instances not being destroyed after use, or MnemonicWallet instances held longer than necessary.
- Flag Keccak256 ThreadLocal not being cleaned up in pooled/web thread contexts.

## HIGH
- Flag violations of sealed type hierarchies (unsealed interfaces/classes where sealed is expected).
- Flag allocation-heavy patterns in hot paths where zero-allocation `*To()` variants exist (e.g., `Hex.decode()` instead of `Hex.decodeTo()`, `Hex.encode()` instead of `Hex.encodeTo()`).
- Flag missing or incorrect Javadoc `<b>Allocation:</b>` tags on new public methods.
- Flag direct byte array manipulation without using singleton constants (`Address.ZERO`, `Wei.ZERO`) where applicable.
- Flag missing tests for behavior changes (unit tests at minimum; integration/smoke tests for chain interaction).

## MEDIUM
- Flag inconsistent use of Java 21 patterns (records, pattern matching, virtual threads) where they would simplify code.
- Flag readability issues in large files (500+ lines) without clear section organization.

## Never Flag
- Formatting-only changes.
- Existing baseline issues unchanged by the diff.
- The default Anvil test key `0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80` in test code.
