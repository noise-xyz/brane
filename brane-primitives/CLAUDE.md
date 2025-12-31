# brane-primitives

Low-level utilities with zero external dependencies. Foundation for all other modules.

## Key Classes

### Hex (`io.brane.primitives.Hex`)
```java
// Encode
String hex = Hex.encode(bytes);           // "0xabcd..."
String hex = Hex.encodeNoPrefix(bytes);   // "abcd..."
String hex = Hex.encodeByte(0xff);        // "ff"

// Decode
byte[] bytes = Hex.decode("0xabcd");
byte[] bytes = Hex.decode("abcd");        // Prefix optional

// Utilities
String clean = Hex.cleanPrefix("0xabcd"); // "abcd"
boolean has = Hex.hasPrefix("0xabcd");    // true
byte[] bytes = Hex.toBytes("0xabcd");     // Alias for decode
```

### RLP (`io.brane.primitives.rlp`)
```java
// Encode
byte[] encoded = Rlp.encode(item);
byte[] encoded = Rlp.encodeList(items);

// Decode
RlpItem item = Rlp.decode(bytes);
RlpList list = (RlpList) Rlp.decode(bytes);

// Types
RlpString str = RlpString.of(bytes);
RlpNumeric num = RlpNumeric.of(bigInteger);
RlpList list = RlpList.of(item1, item2);
```

## Why This Module Exists

- **Zero dependencies**: Pure Java, no external libraries
- **Foundation**: Used by all other brane modules
- **Performance**: Hot path code, optimized for speed
- **Correctness**: Extensively tested against Ethereum specs

## Gotchas

- **Hex prefix**: `decode()` handles both with and without "0x" prefix
- **Empty bytes**: `Hex.encode(new byte[0])` returns `"0x"`
- **RLP encoding**: Numbers must be minimal (no leading zeros except for 0 itself)
