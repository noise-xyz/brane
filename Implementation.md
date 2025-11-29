# Internal Dogfooding Strategy

## Goal
Enable internal engineers to easily consume the `brane` SDK in their projects to validate functionality and ergonomics before a public Maven Central release.

## Comparison of Approaches

### Option A: JitPack (Recommended for Speed)
JitPack is a novel package repository that builds Git repositories on demand. It treats GitHub as the source of truth.

*   **How it works**: When a user requests `com.github.Atlantropaz:brane:1.0.0`, JitPack checks out the `1.0.0` tag, runs `./gradlew publishToMavenLocal`, and serves the artifacts.
*   **Pros**:
    *   **Zero Setup**: No need to configure `maven-publish` or CI/CD pipelines.
    *   **Instant Updates**: Push a commit -> it's available as `com.github.User:Repo:CommitHash`.
    *   **Zero Auth**: If the repo is public, no credentials needed. If private, uses a simple personal access token.
*   **Cons**:
    *   **Build Latency**: The *first* person to request a new version waits for the build to finish (can be slow).
    *   **"Unofficial" Feel**: The group ID is always `com.github.User`, not `io.brane`. This requires a breaking change to imports when switching to Maven Central later.

### Option B: GitHub Packages (Recommended for Stability)
GitHub's built-in Maven registry. You publish artifacts here just like you would to Maven Central.

*   **How it works**: You run `./gradlew publish` (usually in CI), which uploads JARs to `maven.pkg.github.com`.
*   **Pros**:
    *   **Official Group ID**: You can use `io.brane` as the group ID immediately.
    *   **Pre-built Artifacts**: Consumers download pre-built JARs, no waiting for builds.
    *   **Production-Like**: Simulates the real release process (versioning, changelogs).
*   **Cons**:
    *   **Auth Friction**: **EVERY** consumer (even for public repos) must configure a `GITHUB_TOKEN` in their `~/.gradle/gradle.properties` to download artifacts. This is a major friction point for quick internal testing.

### Option C: Local Maven (Recommended for Solo Dev)
Publishing to `~/.m2/repository`.

*   **Pros**: Instant, no network.
*   **Cons**: Cannot share with teammates.

---

## Recommendation

**I recommend Option A (JitPack) for the initial "dogfooding" phase.**

**Why?**
1.  **Low Friction**: Your engineers just add one line to their build file. They don't need to generate tokens or configure global gradle properties.
2.  **Rapid Iteration**: You can tell a teammate "Try the fix on branch `fix/bug-123`" and they can immediately depend on `implementation 'com.github.Atlantropaz:brane:fix-bug-123-SNAPSHOT'`.
3.  **Focus on Code**: You spend time writing SDK code, not debugging CI/CD publishing pipelines.

---

## Implementation Plan: JitPack

### 1. Configuration (`jitpack.yml`)
Create a `jitpack.yml` file in the root to tell JitPack how to build the project (since we use Java 21).

```yaml
jdk:
  - openjdk21
install:
  - ./gradlew publishToMavenLocal
```

### 2. Consumer Instructions
Give this snippet to your engineers:

**`build.gradle`**:
```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    // Target a specific tag
    implementation 'com.github.Atlantropaz:brane:0.1.0'
    
    // OR target a specific commit (great for debugging)
    implementation 'com.github.Atlantropaz:brane:a1b2c3d'
    
    // OR target a branch snapshot
    implementation 'com.github.Atlantropaz:brane:main-SNAPSHOT'
}
```

---

## Implementation Plan: GitHub Packages

### 1. Gradle Configuration
Modify `build.gradle` to include the `maven-publish` plugin and repository configuration.

```groovy
plugins {
    id 'maven-publish'
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Atlantropaz/brane")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        gpr(MavenPublication) {
            from(components.java)
        }
    }
}
```

### 2. CI/CD Pipeline
Create `.github/workflows/publish.yml` to run `./gradlew publish` on every push to `main` or tag creation.

### 3. Consumer Instructions
Engineers must add this to `~/.gradle/gradle.properties`:
```properties
gpr.user=YourGitHubUsername
gpr.key=ghp_YourPersonalAccessToken
```

And update their project's `build.gradle`:
```groovy
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/Atlantropaz/brane")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation 'io.brane:brane-core:0.1.0'
}
```
