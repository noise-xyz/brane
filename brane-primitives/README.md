# brane-primitives

Core primitive utilities with zero external dependencies.

## Features
- Hex encoding/decoding ✓
- RLP encoding/decoding ✓

## RLP Usage

```java
import io.brane.primitives.rlp.*;

// Encode a string
RlpString str = RlpString.of("hello");
byte[] encoded = str.encode();

// Encode a list
RlpList list = RlpList.of(
    RlpString.of("cat"),
    RlpString.of("dog")
);
byte[] encodedList = list.encode();

// Decode
RlpItem decoded = Rlp.decode(encodedList);
```

## References
- [Ethereum RLP Specification](https://ethereum.org/en/developers/docs/data-structures-and-encoding/rlp/)
