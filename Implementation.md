# Infrastructure Upgrade: Gradle 9.2.1

## Executive Summary
Upgrade the build system from Gradle 8.7 to Gradle 9.2.1. This ensures compatibility with the latest JDKs, improves build performance, and removes technical debt associated with deprecated features.

## Current State
- **Current Version**: Gradle 8.7
- **Target Version**: Gradle 9.2.1 (Latest Stable)
- **Status**: Build passes but emits deprecation warnings incompatible with Gradle 9.0+.

## Phase 1: Fix Deprecations (Gradle 8.7)
Before upgrading, we must resolve all deprecations reported by Gradle 8.7, as these will become hard errors in Gradle 9.0.

### Tasks
- [x] **Audit Deprecations**
  - Run: `./gradlew clean build --warning-mode all`
  - Capture output to identify specific deprecated features used in `build.gradle` and settings.
  
- [x] **Fix Build Scripts**
  - **`build.gradle` (Root)**: 
    - Add `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'` to dependencies (fixes test framework deprecation).
    - Replace deprecated `buildDir` with `layout.buildDirectory` in `allJavadoc` task.
    - Fix deprecated property assignment syntax in `repositories` block (Gradle 10 compatibility).
  - **`brane-core/build.gradle`**: Verify dependencies and publishing configuration.
  - **`brane-rpc/build.gradle`**: Verify dependencies.
  - **`brane-examples/build.gradle`**: Verify application plugin usage.
  
- [x] **Verify Clean Build**
  - Run `./gradlew clean build --warning-mode fail`
  - Ensure command exits successfully with zero warnings.

## Phase 2: Upgrade Wrapper
Once the build is clean on 8.7, perform the upgrade.

### Tasks
- [x] **Update Wrapper**
  - Run: `./gradlew wrapper --gradle-version 9.2.1`
  - Verify `gradle/wrapper/gradle-wrapper.properties` points to 9.2.1.
  
- [x] **Validate Upgrade**
  - Run: `./gradlew --version` (Should show 9.2.1)
  - Run: `./gradlew clean build` (Should pass without errors)

## Phase 3: Plugin Updates (If Needed)
If the build fails after upgrade due to plugin incompatibility:

### Tasks
- [x] **Check Plugin Versions**
  - Update `id 'com.github.johnrengelman.shadow'` if used.
  - Update `id 'application'` or other core plugins if behavior changed.
  
## Verification Checklist
- [x] `./gradlew --version` returns 9.2.1
- [x] `./gradlew build` passes
- [x] `./run_integration_tests.sh` passes
