````markdown
## 7. Basic multi-chain / network support (#7)

Goal: Provide simple, typed “chain profiles” (mainnet, testnets, local) and a small builder that wires a `BraneProvider` + `PublicClient` together from those profiles. This is mostly DX sugar, but it gives us a clean place to hang chainId + fee model in the future.

### 7.1 Add ChainProfile model + predefined profiles

**Files:**

- `brane-core/src/main/java/io/brane/core/chain/ChainProfile.java`
- `brane-core/src/main/java/io/brane/core/chain/ChainProfiles.java`

**Tasks:**

1. Create a simple immutable `ChainProfile` type:

   - Package: `io.brane.core.chain`
   - Fields:
     - `long chainId`
     - `String defaultRpcUrl` (nullable)
     - `boolean supportsEip1559`
   - Private constructor + `of(long, String, boolean)` factory.
   - `toString()` for debugging.

2. Create a `ChainProfiles` utility class with common chain presets:

   - Package: `io.brane.core.chain`
   - Class is `final` with private constructor, all fields `public static final ChainProfile`.

   Add at least:

   ```java
   public static final ChainProfile ETH_MAINNET = ChainProfile.of(
       1L,
       "https://ethereum.publicnode.com",
       true
   );

   public static final ChainProfile ETH_SEPOLIA = ChainProfile.of(
       11155111L,
       "https://sepolia.infura.io/v3/YOUR_KEY",
       true
   );

   public static final ChainProfile BASE = ChainProfile.of(
       8453L,
       "https://mainnet.base.org",
       true
   );

   public static final ChainProfile BASE_SEPOLIA = ChainProfile.of(
       84532L,
       "https://sepolia.base.org",
       true
   );

   public static final ChainProfile ANVIL_LOCAL = ChainProfile.of(
       31337L,
       "http://127.0.0.1:8545",
       true // Anvil behaves like an EIP-1559 chain
   );
````

Notes:

* URLs are “nice defaults” only; callers can override.
* We store the EIP-1559 flag now even if we don’t fully use it yet.

### 7.2 Add BranePublicClient wrapper + builder

**Files:**

* `brane-rpc/src/main/java/io/brane/rpc/BranePublicClient.java`

**Tasks:**

1. Create `BranePublicClient` in package `io.brane.rpc`:

   * Wraps a `PublicClient` and a `ChainProfile`.
   * Implements `PublicClient` and delegates all methods to the inner `PublicClient` so it’s drop-in compatible.

   Sketch:

   ```java
   public final class BranePublicClient implements PublicClient {
       private final PublicClient delegate;
       private final ChainProfile profile;

       private BranePublicClient(PublicClient delegate, ChainProfile profile) {
           this.delegate = delegate;
           this.profile = profile;
       }

       public ChainProfile profile() {
           return profile;
       }

       // delegate PublicClient methods to `delegate`...
   }
   ```

2. Add a nested `Builder` (or separate `BranePublicClientBuilder`) with:

   * Fields:

     * `ChainProfile profile` (required)
     * `String rpcUrlOverride` (optional)

   * Static entry point:

     ```java
     public static Builder forChain(ChainProfile profile)
     ```

   * Builder methods:

     ```java
     public Builder withRpcUrl(String rpcUrl); // override default
     public BranePublicClient build();
     ```

   * `build()` behavior:

     * Determine `rpcUrl`:

       ```java
       final String rpcUrl = rpcUrlOverride != null
           ? rpcUrlOverride
           : profile.defaultRpcUrl;
       ```

       If `rpcUrl` is `null`, throw `IllegalStateException` with a clear message (“No RPC URL configured for chain X; either configure defaultRpcUrl or call withRpcUrl(…)”).

     * Create a `BraneProvider` using the existing HTTP provider:

       ```java
       BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
       ```

       (Don’t change the existing `HttpBraneProvider` API.)

     * Build a `PublicClient` from the provider via the existing factory:

       ```java
       PublicClient publicClient = PublicClient.from(provider);
       ```

     * Return a new `BranePublicClient(publicClient, profile)`.

3. Ensure no existing code is broken:

   * Do **not** remove or change `PublicClient.from(BraneProvider)`.
   * `BranePublicClient` is additive, not a replacement.

### 7.3 Usage: client creation from chain profiles

We want the following usage pattern to compile and work:

```java
import static io.brane.core.chain.ChainProfiles.*;

BranePublicClient client = BranePublicClient
    .forChain(ChainProfiles.ETH_SEPOLIA)
    .withRpcUrl("https://sepolia.infura.io/v3/YOUR_KEY")
    .build();
```

or, when the default URL is acceptable:

```java
BranePublicClient client = BranePublicClient
    .forChain(ChainProfiles.ANVIL_LOCAL)
    .build();
```

### 7.4 Tests for ChainProfiles + builder

**Files:**

* `brane-core/src/test/java/io/brane/core/chain/ChainProfilesTest.java`
* `brane-rpc/src/test/java/io/brane/rpc/BranePublicClientBuilderTest.java`

**Tasks:**

1. **ChainProfilesTest**

   * Assert that:

     ```java
     ETH_MAINNET.chainId == 1L
     ETH_SEPOLIA.chainId == 11155111L
     BASE.chainId == 8453L
     BASE_SEPOLIA.chainId == 84532L
     ANVIL_LOCAL.chainId == 31337L
     ```

   * Assert that `supportsEip1559` is `true` for all of them.

   * Assert that `defaultRpcUrl` is non-null for all the profiles we set defaults for.

2. **BranePublicClientBuilderTest**

   * Use a fake provider or stub HTTP client if needed, or just rely on the existing `HttpBraneProvider.builder(rpcUrl).build()` as long as it doesn’t actually hit the network in tests.
   * Test that:

     * `BranePublicClient.forChain(ChainProfiles.ANVIL_LOCAL).build()`:

       * Does not throw.
       * Returns a `BranePublicClient` whose `profile().chainId` is `31337`.

     * `BranePublicClient.forChain(ChainProfile.of(1234L, null, true)).build()`:

       * Throws `IllegalStateException` because there is no `defaultRpcUrl` and no override.

     * `BranePublicClient.forChain(ChainProfiles.ETH_SEPOLIA).withRpcUrl("https://example.com").build()`:

       * Uses the override instead of the default (you can indirectly assert this by checking that no exception is thrown and the builder doesn’t complain about missing RPC URL).

### 7.5 Optional: update docs / examples

* Add a short code snippet to the README or a comment in an example showing how to create a `BranePublicClient` from a chain profile rather than manually wiring the provider.
* Do **not** change the existing example wiring yet; keep it simple and backward-compatible for now.

````