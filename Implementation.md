## 1️⃣ Top Priority: Make the Gradle build clean and reproducible

**Goal:** `./gradlew clean check` runs successfully with no compilation errors.

### Instructions for Codex

1. **Open `settings.gradle` and confirm modules:**

   Make sure it looks like:

   ```groovy
   rootProject.name = 'brane'

   include 'brane-core'
   include 'brane-rpc'
   include 'brane-contract'
   ```

2. **Root `build.gradle`: define shared config**

   In `build.gradle` at the repo root, ensure something like:

   ```groovy
   plugins {
       id 'java-library' apply false
   }

   allprojects {
       group = 'io.brane'
       version = '0.1.0-alpha'

       repositories {
           mavenCentral()
       }
   }

   subprojects {
       apply plugin: 'java-library'

       java {
           toolchain {
               languageVersion = JavaLanguageVersion.of(17)
           }
       }

       tasks.withType(Test).configureEach {
           useJUnitPlatform()
       }

       dependencies {
           testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
       }
   }
   ```

3. **Module `brane-core/build.gradle`**

   * Should NOT depend on other Brane modules.
   * Only needs JUnit (already supplied from root) and maybe nothing else.

   Example:

   ```groovy
   dependencies {
       // No external deps here unless needed
       // testImplementation already inherited
   }
   ```

4. **Module `brane-rpc/build.gradle`**

   * Must depend on `brane-core`.
   * Needs Jackson for JSON.

   ```groovy
   dependencies {
       api project(':brane-core')
       implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.0'
   }
   ```

5. **Module `brane-contract/build.gradle`**

   * Must depend on `brane-core` and `brane-rpc`.

   ```groovy
   dependencies {
       api project(':brane-core')
       api project(':brane-rpc')
   }
   ```

6. **Now run the build (locally):**

   ```bash
   ./gradlew clean check
   ```

7. **Fix all compilation errors**:

   * If any imports are missing (e.g. Jackson, HttpClient, web3j internals), update them.
   * If `internal` classes are wrongly referenced in public APIs, move those references behind `io.brane.contract.InternalAbi` or helper methods.
   * Keep iterating until `./gradlew clean check` passes.

---

## 2️⃣ High Priority: Unit tests for `RevertDecoder` (no network needed)

**Goal:** Prove that `RevertDecoder` correctly decodes `Error(string)` and safely handles non-Error data.

### Instructions for Codex

1. **Create test class in `brane-core`:**

   File: `brane-core/src/test/java/io/brane/core/RevertDecoderTest.java`

2. **Write two tests:**

   **a) Decodes `Error(string)` correctly**

   ```java
   package io.brane.core;

   import io.brane.internal.web3j.abi.datatypes.Utf8String;
   import io.brane.internal.web3j.abi.TypeEncoder;
   import org.junit.jupiter.api.Test;

   import static org.junit.jupiter.api.Assertions.*;

   class RevertDecoderTest {

       @Test
       void decodesErrorString() {
           Utf8String msg = new Utf8String("simple reason");
           String encodedArg = TypeEncoder.encode(msg); // "0x" + 32-byte length + data

           String rawData = "0x08c379a0" + encodedArg.substring(2);

           RevertDecoder.Decoded decoded = RevertDecoder.decode(rawData);

           assertEquals("simple reason", decoded.reason());
           assertEquals(rawData, decoded.rawDataHex());
       }
   }
   ```

   **b) Non-Error data returns null reason**

   ```java
       @Test
       void nonErrorDataReturnsNullReason() {
           String rawData = "0x12345678deadbeef";
           RevertDecoder.Decoded decoded = RevertDecoder.decode(rawData);

           assertNull(decoded.reason());
           assertEquals(rawData, decoded.rawDataHex());
       }
   }
   ```

3. **Run tests again:**

   ```bash
   ./gradlew :brane-core:test
   ```

   Fix any issues (e.g. wrong package for `TypeEncoder`, small API mismatches) until tests pass.

---

## 3️⃣ High Priority: Unit tests for `Contract.read` using a mock `Client`

**Goal:** Verify that `Contract.read`:

* Returns decoded value on success.
* Throws `RevertException` when `Client.call` fails with revert data in `RpcException.data()`.

This does **not** require a real node or Foundry yet; just a fake `Client`.

### Instructions for Codex

1. **Create test class in `brane-contract`:**

   File: `brane-contract/src/test/java/io/brane/contract/ContractReadTest.java`

2. **Create a `FakeClient` in the test to simulate RPC responses:**

   ```java
   package io.brane.contract;

   import io.brane.core.RpcException;
   import io.brane.rpc.Client;

   final class FakeClient implements Client {

       private final Object result;
       private final RpcException toThrow;

       FakeClient(Object result, RpcException toThrow) {
           this.result = result;
           this.toThrow = toThrow;
       }

       @Override
       @SuppressWarnings("unchecked")
       public <T> T call(String method, Class<T> responseType, Object... params) throws RpcException {
           if (toThrow != null) throw toThrow;
           return (T) result;
       }
   }
   ```

3. **Prepare a trivial ABI for a function `echo(uint256) returns (uint256)`**

   Add a string constant in the test:

   ```java
   private static final String ECHO_ABI = """
   [
     {
       "inputs": [{ "internalType": "uint256", "name": "x", "type": "uint256" }],
       "name": "echo",
       "outputs": [{ "internalType": "uint256", "name": "", "type": "uint256" }],
       "stateMutability": "pure",
       "type": "function"
     }
   ]
   """;
   ```

4. **Test: successful read**

   ```java
   import io.brane.core.RevertException;
   import org.junit.jupiter.api.Test;

   import java.math.BigInteger;

   import static org.junit.jupiter.api.Assertions.*;

   class ContractReadTest {

       @Test
       void readSuccessDecodesReturnValue() throws RpcException, RevertException {
           // Result will be hex-encoded return from eth_call
           // For uint256 42, the encoded result is 32-byte padded hex.
           String encoded = "0x" + "0".repeat(63) + "2a";

           Client client = new FakeClient(encoded, null);
           Abi abi = Abi.fromJson(ECHO_ABI);
           Contract contract = new Contract("0x1234", abi, client);

           BigInteger result = contract.read("echo", BigInteger.class, BigInteger.valueOf(42));

           assertEquals(BigInteger.valueOf(42), result);
       }
   }
   ```

5. **Test: revert path**

   ```java
       @Test
       void readRevertThrowsRevertException() {
           // Make revert data using same technique as in RevertDecoderTest
           Utf8String msg = new Utf8String("simple reason");
           String encodedArg = TypeEncoder.encode(msg);
           String rawData = "0x08c379a0" + encodedArg.substring(2);

           RpcException rpcEx = new RpcException(
               3, // arbitrary JSON-RPC code
               "execution reverted",
               rawData,
               null
           );

           Client client = new FakeClient(null, rpcEx);
           Abi abi = Abi.fromJson(ECHO_ABI);
           Contract contract = new Contract("0x1234", abi, client);

           RevertException ex = assertThrows(
               RevertException.class,
               () -> contract.read("echo", BigInteger.class, BigInteger.valueOf(42))
           );

           assertEquals("simple reason", ex.revertReason());
           assertEquals(rawData, ex.rawDataHex());
       }
   }
   ```

6. **Run tests:**

   ```bash
   ./gradlew :brane-contract:test
   ```

   Fix any decoding or ABI wiring issues in `InternalAbi` / `Contract.read` until tests pass.

---

## 4️⃣ Medium Priority: Sanity-test against a real node (Foundry + Anvil)

Once unit tests pass, we want to confirm that the **real RPC error format** from a node matches our assumptions.

**Goal:** With `anvil` running and a simple contract deployed, calling a reverting function through Brane should produce a `RevertException` with the correct `revertReason()`.

### Instructions for Codex

1. **Create a `foundry/` directory (manually, you can do this yourself).**

   Inside it:

   ```bash
   forge init brane-foundry-test
   ```

2. **Add `RevertExample.sol` to the Foundry project:**

   ```solidity
   // SPDX-License-Identifier: MIT
   pragma solidity ^0.8.20;

   contract RevertExample {
       function alwaysRevert() external pure {
           revert("simple reason");
       }
   }
   ```

3. **Run Anvil in a terminal:**

   ```bash
   anvil
   ```

4. **In a JUnit test (`brane-contract` module), hardcode the deployed contract address** (for alpha, you can deploy once manually via Foundry script or use Anvil’s default pre-funded account + raw tx, then paste the address).

   Then:

   ```java
   @Test
   void alwaysRevertAgainstAnvil() {
       Client client = new HttpClient(URI.create("http://127.0.0.1:8545"));
       Abi abi = Abi.fromJson(REVERT_EXAMPLE_ABI_JSON);
       Contract contract = new Contract(REVERT_EXAMPLE_ADDRESS, abi, client);

       RevertException ex = assertThrows(
           RevertException.class,
           () -> contract.read("alwaysRevert", Void.class)
       );

       assertEquals("simple reason", ex.revertReason());
   }
   ```

5. **Fix any mismatch between actual `error.data` format and our assumptions.**

   * If `RpcException.data()` is not exactly the raw revert payload, adjust `HttpClient` / error parsing to extract the right field.
   * Ensure we’re always passing the actual revert data into `RevertDecoder.decode(...)`.

---

## 5️⃣ Lower Priority: Add `brane-examples` module and polish docs

Once 1–4 are stable, you can:

1. **Add `brane-examples` module** with:

   * Small `Main` class that:

     * Connects to a node
     * Calls a non-reverting function
     * Calls a reverting function and prints `RevertException`

2. **Cross-check README code samples**:

   * Ensure the Quickstart snippet compiles and runs against your actual API.
   * Fix any signature mismatches.

3. **Add basic Javadoc to public APIs** (`BraneException`, `RpcException`, `RevertException`, `Client`, `Abi`, `Contract`).