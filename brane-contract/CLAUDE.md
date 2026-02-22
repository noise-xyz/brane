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
- **`TrustlessAgents`** - ERC-8004 client for Identity, Reputation, and Validation registries
- **`TrustlessAgents.ReadOnly`** - Read-only facade (no signer needed)
- **`FeedbackSummary`** - Aggregated feedback count + score from Reputation Registry
- **`ValidationSummary`** - Aggregated validation count + average response
- **`ValidationStatus`** - Individual validation status record

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

### ERC-8004 Trustless Agents
```java
// Connect to mainnet registries (read-write)
TrustlessAgents agents = TrustlessAgents.connectMainnet(signer);

// Connect read-only (no signer)
TrustlessAgents.ReadOnly readOnly = TrustlessAgents.connectMainnetReadOnly(reader);

// Register an agent
AgentId agentId = agents.register("https://example.com/agent.json");

// Query feedback summary (uses raw eth_call + tuple decoding)
FeedbackSummary summary = agents.getSummary(agentId);

// Give feedback
agents.giveFeedback(agentId, FeedbackValue.of(95, 2), "quality", "");

// Listen for events
List<AgentRegistered> events = agents.getRegistrations(fromBlock, toBlock);

// Parse agent registration file
AgentRegistration card = TrustlessAgents.parseRegistration(jsonString);
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
