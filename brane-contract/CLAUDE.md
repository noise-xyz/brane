# brane-contract

High-level contract interaction via dynamic proxy binding. No code generation required.

## Key Classes

- **`BraneContract`** - Main entry point: `bind()` creates typed contract proxy
- **`ReadOnlyContract`** - Facade for view/pure functions
- **`ReadWriteContract`** - Extends ReadOnlyContract with state-changing methods
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

// Bind to deployed contract
ERC20 token = BraneContract.bind(
    ERC20.class,
    abi,
    contractAddress,
    signerClient
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

### Low-Level Facade
```java
// When you don't want interface binding
var contract = new ReadWriteContract(abi, address, signerClient);

// Call view function
BigInteger balance = contract.call("balanceOf", BigInteger.class, owner);

// Send transaction
TransactionReceipt receipt = contract.sendAndWait("transfer", to, amount);

// Decode events from receipt
List<Transfer> events = contract.decodeEvents("Transfer", Transfer.class, receipt);
```

## How It Works

1. `BraneContract.bind()` creates a JDK dynamic proxy
2. Method calls are intercepted by `ContractInvocationHandler`
3. Method signature is validated against ABI
4. Parameters are ABI-encoded via `brane-core`
5. View functions → `Brane.call()`
6. Write functions → `Brane.Signer.sendTransaction()`
7. Return values are ABI-decoded and returned

## Gotchas

- **Method names must match**: Interface method names must exactly match Solidity function names
- **Parameter types matter**: Use `BigInteger` for uint/int, `Address` for address, `byte[]` for bytes
- **No constructor calls**: Use `TxBuilder` to deploy contracts
- **Events**: Use `ReadOnlyContract.decodeEvents()` or decode logs manually

## Dependencies

- brane-core (ABI encoding)
- brane-rpc (client layer)
