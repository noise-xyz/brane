# brane-contract

High-level contract interaction via dynamic proxy binding. No code generation required.

## Commands

```bash
# Compile
./gradlew :brane-contract:compileJava

# Run tests
./gradlew :brane-contract:test

# Run specific test
./gradlew :brane-contract:test --tests "sh.brane.contract.MyTest"
```

## Key Classes

- **`BraneContract`** - Main entry point: `bind()` and `bindReadOnly()` create typed contract proxies
- **`ContractOptions`** - Configuration (gas limit, timeouts, poll intervals)
- **`@Payable`** - Annotation for payable functions

## Patterns

### Interface-Based Binding
```java
// Define interface matching contract ABI
interface ERC20 {
    BigInteger balanceOf(Address owner);
    BigInteger totalSupply();
    Hash transfer(Address to, BigInteger amount);
    Hash approve(Address spender, BigInteger amount);
}

// Bind to deployed contract (read-write with signer)
ERC20 token = BraneContract.bind(
    contractAddress,
    abiJson,
    signerClient,
    ERC20.class
);

// Read-only binding (no signer needed)
ERC20 readOnly = BraneContract.bindReadOnly(
    contractAddress,
    abiJson,
    readerClient,
    ERC20.class
);

// Use like a regular Java object
BigInteger balance = token.balanceOf(myAddress);
Hash txHash = token.transfer(recipient, amount);
```

### Method Naming
- Method names map to Solidity function names
- Overloaded functions: use exact parameter types
- Return `Hash` for write functions (returns tx hash)
- Return actual type for view/pure functions

### Payable Functions
```java
interface WETH {
    @Payable
    Hash deposit();  // Sends ETH with the call
}
```

### Event Decoding
```java
// Decode events from transaction receipt using Abi
Abi abi = Abi.fromJson(abiJson);
List<Transfer> events = abi.decodeEvents("Transfer", receipt.logs(), Transfer.class);
```

## How It Works

1. `BraneContract.bind()` creates a JDK dynamic proxy
2. Method calls are intercepted by internal invocation handlers
3. Method signature is validated against ABI
4. Parameters are ABI-encoded via `brane-core`
5. View functions → `Brane.call()`
6. Write functions → `Brane.Signer.sendTransaction()`
7. Return values are ABI-decoded and returned

## Gotchas

- **Method names must match**: Interface method names must exactly match Solidity function names
- **Parameter types matter**: Use `BigInteger` for uint/int, `Address` for address, `byte[]` for bytes
- **No constructor calls**: Use `TxBuilder` to deploy contracts
- **Events**: Use `Abi.decodeEvents()` to decode events from receipt logs

## Dependencies

- brane-core (ABI encoding)
- brane-rpc (client layer)
