````md
## Step 3 – Basic ABI encoding/decoding primitives

We now have:

- BraneProvider / HttpBraneProvider (Step 1)
- PublicClient / DefaultPublicClient (Step 2)
- A working `Abi` + `Contract.read(...)` path for `echo(uint256)` calls.

Step 3 is about turning the ad-hoc ABI bits into **deliberate, table-stakes ABI primitives**, aligned with viem/alloy’s core capabilities:

- Function encoding (selector + arguments)
- Basic output decoding to Java types
- Event signature hashing (`topic0`)

Target milestone:

> **“Call ERC-20 `decimals()` and `balanceOf(address)` from Java using Brane.”**

That means, by the end of this step, we should be able to:

1. Parse an ERC-20 ABI JSON.
2. Encode:
   - `decimals()` as call data.
   - `balanceOf(address)` as call data.
3. Use `PublicClient.call` (or `Contract.read`) to perform `eth_call`.
4. Decode:
   - `decimals()` → `Integer` or `BigInteger` = 18.
   - `balanceOf(address)` → `BigInteger` balance.

### 1. Where ABI logic lives

ABI remains in **brane-contract**, not brane-rpc:

```text
brane-contract/src/main/java/io/brane/contract/
  Abi.java               // ABI entrypoint (public surface)
  Contract.java          // High-level wrapper around Abi + Client
  ...
````

Requirements:

* `Abi` is the **only** public ABI entrypoint.
* `Abi` may use `io.brane.internal.web3j.abi.*` internally.
* **No `org.web3j` imports** in public packages (`io.brane.contract.*`) – internal web3j only under `io.brane.internal.web3j.*` (guardrails).

### 2. Abi surface – function calls

We already have (from earlier work):

* `Abi.fromJson(String json)`
* `Abi.FunctionCall abi.encodeFunction(String fn, Object... args)`
* `call.data()` (encoded data)
* `call.decode(String rawResultHex, Class<T> returnType)`

Step 3 is about making these **real and well-defined**, not just “whatever web3j did”.

#### 2.1 `Abi.fromJson`

Keep the existing method, but make sure it:

* Parses a JSON ABI array string.
* Internally builds a map of:

  * function name → function definition (including inputs/outputs, types).
* Supports at least:

  * `function` entries with:

    * `name`
    * `inputs` (type + name)
    * `outputs` (type + name)
  * `event` entries (for hashing later).

Implementation detail:

* You can use internal web3j ABI JSON parser if already present (under `io.brane.internal.web3j`), but keep that **internal** to `Abi`.

#### 2.2 `Abi.FunctionCall` encoding

`Abi.encodeFunction(fnName, Object... args)` must:

1. Look up the function definition by `name` (and number/types of inputs).
2. Map Java arguments → ABI `Type` objects, based on function input types.

Supported input types (P0):

* `uint256`:

  * Java: `BigInteger`, `Long`, `Integer`.
* `int256` (optional P0 but nice):

  * Java: `BigInteger`, `Long`, `Integer` (allow negative).
* `address`:

  * Java: `io.brane.core.types.Address`.
* `bool`:

  * Java: `Boolean`.
* `string`:

  * Java: `String`.
* `bytes` / `bytesN`:

  * Java: `byte[]`, `io.brane.core.types.HexData`.

Array support (P0):

* Static: `uint256[]`, `address[]`, `bool[]`, `string[]`.
* Java: `List<?>` or `Object[]` (we can keep it simple; support `List<T>` for now).
* We don’t need multi-dimensional arrays yet.

`Abi.FunctionCall` should:

* Hold:

  * The function metadata (inputs/outputs).
  * The encoded data (function selector + arguments) as a hex string.

Public methods:

```java
public final class Abi {

    public static Abi fromJson(String json) { ... }

    public FunctionCall encodeFunction(String name, Object... args) { ... }

    public static final class FunctionCall {
        private final String name;
        private final String data; // "0x..."

        public String name() { return name; }

        public String data() { return data; }

        public <T> T decode(String rawResultHex, Class<T> returnType) { ... }
    }
}
```

#### 2.3 Decoding outputs

`FunctionCall.decode(rawResultHex, returnType)` must:

1. Use the ABI outputs for this function to determine output types.
2. Decode the raw hex (e.g. `"0x000...002a"`) into a list of ABI values.
3. If there is:

   * A **single output**, map it to `returnType`:

     * `uint256` → `BigInteger` / `Long` / `Integer`
     * `address` → `Address`
     * `bool` → `Boolean`
     * `string` → `String`
     * `bytes` → `HexData` or `byte[]`
     * array types → `List<T>` (P0).
   * Multiple outputs (tuple):

     * P0: we can throw `AbiDecodingException` unless `returnType` is `Object[]` or `List<?>`. (We don’t need multi-return for ERC-20.)

Error handling:

* If the raw hex is `"0x"` or too short:

  * Throw `AbiDecodingException` (a `BraneException` subclass you’ll add if not present yet), not return `null`.
* If types don’t match `returnType`:

  * Throw `AbiDecodingException`.

Again, implementation may use internal web3j `FunctionReturnDecoder` under the hood, but wrap all errors into `AbiEncodingException`/`AbiDecodingException`.

### 3. Event signature hashing

Add simple event helpers to `Abi`:

```java
public final class Abi {

    // existing stuff...

    /**
     * Computes topic0 (Keccak-256) for an event signature string,
     * e.g. "Transfer(address,address,uint256)".
     */
    public static Hash eventTopic(String eventSignature) { ... }

    /**
     * Computes function selector (first 4 bytes) for a function signature string,
     * e.g. "balanceOf(address)".
     */
    public static HexData functionSelector(String functionSignature) { ... }
}
```

Implementation:

* Use whatever Keccak-256 you already have (likely via internal web3j `org.web3j.crypto.Hash.keccak256(...)` or vendored equivalent under `io.brane.internal.web3j.crypto.Hash`).
* `eventTopic`:

  * Input: `"Transfer(address,address,uint256)"`.
  * Compute Keccak-256 of UTF-8 bytes.
  * Return as `Hash` (`0x` + 64 hex chars).
* `functionSelector`:

  * Same hash as above, but return first 4 bytes as `HexData` (`"0x" + 8 hex chars`).

### 4. Tests for Abi

Create or extend:

```text
brane-contract/src/test/java/io/brane/contract/AbiEncodingDecodingTest.java
```

Tests to add:

1. **Function encoding – simple types**

   * Given ABI JSON for:

     ```solidity
     function balanceOf(address account) view returns (uint256);
     function decimals() view returns (uint8);
     ```

   * `Abi.fromJson(ERC20_ABI_JSON)`

   * `Abi.FunctionCall call = abi.encodeFunction("balanceOf", new Address("0x" + "1".repeat(40)));`

   * Assert:

     * `call.data()` starts with correct selector (use known fn selector from ERC-20).
     * Encoded args length is > selector length.

   * `Abi.FunctionCall decimalsCall = abi.encodeFunction("decimals");`

     * `data()` should be just selector (no args).

2. **Decoding – uint256 / address / bool / string**

   * Build known hex outputs and ensure round-trip decode:

     * `uint256` → `BigInteger`:

       * e.g. raw hex for `42` as single return.
     * `string`:

       * raw hex representing `"hello"`.
     * `address`:

       * raw hex representing an address.
     * `bool`:

       * raw hex for true / false.

   * You can either:

     * Encode via internal ABI, then decode via Brane `Abi.FunctionCall.decode`.
     * Or use hard-coded test vectors.

3. **Event topic hashing**

   * `Abi.eventTopic("Transfer(address,address,uint256)")`
   * Assert equals the known topic0 from ERC-20:

     * `0xddf252ad...` (full 32-byte hash).
   * Maybe also test `Approval(address,address,uint256)`.

4. **ERC-20 integration test (optional / property-based)**

   * Similar to `ContractReadTest.echoReturnsSameValue`, but for ERC-20:

     * Have a test that is only enabled when `brane.anvil.rpc` and `brane.anvil.erc20.address` are set.
     * Use `Abi + Contract.read` or `Abi + PublicClient.call` to:

       * Call `decimals()` and assert result == 18.
       * Call `balanceOf(some known address)` and assert expected balance.
   * Mark with JUnit `Assumptions` so it only runs when configured.

### 5. Guardrails

* `Abi` is allowed to import from:

  * `io.brane.internal.web3j.abi.*`
  * `io.brane.internal.web3j.crypto.*`

* No `org.web3j` imports.

* No public API types from web3j leak through:

  * `Abi` only exposes Brane types (`Address`, `Hash`, `HexData`, etc.) and standard Java types.

Once tests pass, we’ll have:

* A robust `Abi` layer.
* `Contract.read` still working but now generalizable to real ERC-20 calls.
* A path to step 4 (events/log decoding and eventually write/WalletClient).