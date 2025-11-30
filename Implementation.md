# Phase 5: Internal Dogfooding (JitPack)

## Goal
Enable internal engineers to easily consume the `brane` SDK in their projects immediately, using JitPack as the distribution mechanism.

## Implementation Plan

### 1. JitPack Configuration (`jitpack.yml`)
Create a `jitpack.yml` file in the root directory to configure the build environment for JitPack. Since we use Java 21, we must explicitly request it.

**Sample Code (`jitpack.yml`)**:
```yaml
jdk:
  - openjdk21
install:
  - ./gradlew publishToMavenLocal
```

### 2. Consumer Documentation (`DOGFOODING.md`)
Create a guide for internal engineers explaining how to add the dependency.

**Sample Code (`DOGFOODING.md`)**:
```markdown
# Internal Dogfooding Guide

## How to use `brane` in your project

1. Add the JitPack repository to your `build.gradle`:
   ```groovy
   repositories {
       mavenCentral()
       maven { url 'https://jitpack.io' }
   }
   ```

2. Add the dependency:
   ```groovy
   dependencies {
       // Replace 'Tag' with a git tag (e.g., '0.1.0') or commit hash
       implementation 'com.github.noise-xyz.brane:brane-core:Tag'
   }
   ```
```

## Acceptance Criteria
- [ ] **Configuration**: `jitpack.yml` exists and specifies `openjdk21`.
- [ ] **Documentation**: `DOGFOODING.md` exists with clear instructions.
- [ ] **Verification**:
    - Push changes to GitHub.
    - Verify the build log on `jitpack.io` (Manual Step).
    - Create a dummy consumer project locally to verify it *can* resolve the artifact (simulated).

## Verification Plan (Simulated)
Since we cannot "run" JitPack locally, we will verify the `publishToMavenLocal` command works, which is what JitPack runs.

1. Run `./gradlew publishToMavenLocal` locally.
2. Verify artifacts exist in `~/.m2/repository/com/github/noise-xyz/brane/...`.

