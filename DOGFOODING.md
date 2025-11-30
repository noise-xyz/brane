# Internal Dogfooding Guide

## How to use `brane` in your project

We use [JitPack](https://jitpack.io) for internal distribution. This allows you to pull any branch, tag, or commit hash as a dependency.

### 1. Add the Repository
Add `maven { url 'https://jitpack.io' }` to your `repositories` block in `build.gradle`:

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
```

### 2. Add the Dependency
Add the dependency using the format `com.github.noise-xyz.brane:Module:Tag`.

#### Target a specific Release (Recommended)
```groovy
dependencies {
    implementation 'com.github.noise-xyz.brane:brane-core:0.1.0'
}
```

#### Target a specific Commit (For debugging)
```groovy
dependencies {
    implementation 'com.github.noise-xyz.brane:brane-core:a1b2c3d'
}
```

#### Target a Branch Snapshot (For testing latest changes)
```groovy
dependencies {
    implementation 'com.github.noise-xyz.brane:brane-core:main-SNAPSHOT'
}
```
