# Phase 5: Internal Dogfooding (Smoke Test)

## Goal
Ensure that the `brane` SDK can be successfully consumed as an external library by other projects.

## Strategy: Standalone Smoke Test
We will create a **standalone** consumer project within the repository to simulate a real-world user.

*   **Location**: `smoke-test/` directory.
*   **Isolation**: It will have its own `settings.gradle` and `build.gradle`. It will **NOT** be part of the main project's Gradle build tree.
*   **Dependency**: It will depend on `com.github.noise-xyz.brane:brane-core` via `mavenLocal()`.

## Implementation Plan

### 1. Structure
```
brane/
├── build.gradle (Main SDK)
├── verify_smoke_test.sh (Orchestrator)
└── smoke-test/ (Standalone Consumer)
    ├── build.gradle
    ├── settings.gradle
    └── src/main/java/io/brane/smoke/SmokeApp.java
```

### 2. `verify_smoke_test.sh`
This script will:
1.  Build the main SDK and publish to local Maven (`./gradlew publishToMavenLocal`).
2.  Switch to `smoke-test/` directory.
3.  Run the consumer app (`../gradlew run`).

### 3. Acceptance Criteria
- [ ] **Isolation**: `smoke-test` is not listed in the root `settings.gradle`.
- [ ] **Execution**: `./verify_smoke_test.sh` runs successfully.
- [ ] **Functionality**: The smoke test app successfully imports and uses a class from `brane-core`.

