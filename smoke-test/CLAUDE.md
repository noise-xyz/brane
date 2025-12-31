# smoke-test

End-to-end integration testing application. Validates all major SDK features work together.

## Running

```bash
# Requires Anvil
anvil  # In separate terminal

# Run smoke tests
./gradlew :smoke-test:run

# Or via script
./scripts/test_smoke.sh
```

## Main Class

`io.brane.smoke.SmokeApp` - Comprehensive test runner

## Test Coverage

- Contract binding and method invocation
- ERC-20 token interactions (transfer, approve, balanceOf)
- Transaction submission and receipt polling
- Event log decoding
- Revert handling and error messages
- Chain ID validation
- EIP-2930 access list creation
- Multiple contract instances
- Error scenarios and edge cases

## Structure

```java
public class SmokeApp {
    public static void main(String[] args) {
        // Setup
        var provider = HttpBraneProvider.create("http://127.0.0.1:8545");
        var client = DefaultWalletClient.create(provider, signer);

        // Run all tests
        testContractBinding();
        testErc20Transfer();
        testEventDecoding();
        testRevertHandling();
        // ...

        System.out.println("  ALL SMOKE TESTS PASSED!");
    }

    private static void testContractBinding() {
        // Test implementation
        // Throws RuntimeException on failure
        System.out.println("  Contract binding");
    }
}
```

## Adding New Smoke Tests

1. Add method: `private static void testMyFeature()`
2. Call from `main()`
3. Throw `RuntimeException` on failure
4. Log success: `System.out.println("   ...");`

## Exit Codes

- `0`: All tests passed
- `1`: Test failure (RuntimeException thrown)

## Dependencies

- brane-core
- brane-rpc
- brane-contract
- Requires Anvil running on `127.0.0.1:8545`
