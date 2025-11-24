# Tech Debt Cleanup Plan

## Goal Description
The goal of this cleanup is to modernize the `brane` codebase to fully leverage Java 21 features, improve documentation coverage, and enhance test quality. This will ensure the codebase is maintainable, readable, and robust.

## User Review Required
> [!IMPORTANT]
> This plan involves significant changes to existing code to introduce Java 21 features (e.g., pattern matching) and may require refactoring of tests to use a mocking framework (e.g., Mockito) instead of manual mocks.
>
> **Guardrail Compliance**: All changes must strictly adhere to `Guardrail.md`, specifically:
> - No `web3j` types in public APIs.
> - `brane-core` must remain free of `web3j` dependencies.
> - `web3j` usage must be confined to `io.brane.internal.web3j.*` or internal adapters.

## Proposed Changes

### Documentation
- **Goal**: Ensure all public classes and methods have Javadoc.
- **Files**:
    - `brane-core`: `RevertDecoder`, `BlockHeader`, `Transaction`, etc.
    - `brane-rpc`: `PublicClient`, `DefaultPublicClient`, `BraneProvider`, etc.
- **Action**: Add Javadoc explaining the purpose, parameters, and return values.

### Java 21 Modernization
- **Goal**: Use modern Java features where applicable.
- **Files**:
    - `brane-rpc/src/main/java/io/brane/rpc/DefaultPublicClient.java`:
        - Refactor `extractErrorData` to use pattern matching for `instanceof`.
        - Use `var` for local variable type inference where it improves readability.
    - `brane-core`: Check for opportunities to use records or sealed classes if not already used.

### Guardrail Compliance
- **Goal**: Enforce strict separation of `web3j` dependencies.
- **Files**:
    - `brane-core`: Verify zero imports from `org.web3j.*`.
    - `brane-rpc`: Verify no `web3j` types in public method signatures.
    - `brane-contract`: Verify no `web3j` imports in public classes.
- **Action**:
    - Move any leaking `web3j` usage to `io.brane.internal.*`.
    - Wrap `web3j` exceptions in `RpcException` or `BraneException`.

### Code Quality & Style
- **Goal**: Remove magic strings/numbers and improve type safety.
- **Files**:
    - `brane-core/src/main/java/io/brane/core/RevertDecoder.java`: Extract "08c379a0" to a named constant.
    - `brane-rpc`: Review unchecked casts and suppressions.

### Testing
- **Goal**: Improve test coverage and quality.
- **Files**:
    - `brane-rpc/src/test/java/io/brane/rpc/DefaultPublicClientTest.java`:
        - Consider introducing Mockito to replace `FakeProvider` for cleaner tests.
        - Add tests for edge cases (e.g., null responses, error responses).

## Verification Plan

### Automated Tests
- Run all existing tests to ensure no regressions:
    ```bash
    ./gradlew test
    ```
- Verify that new tests (if added) pass.

### Manual Verification
- Review generated Javadoc (if a doc task exists, otherwise code review).
- Verify that the code compiles with Java 21.
