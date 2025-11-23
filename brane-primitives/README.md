# brane-primitives

Core primitive utilities with zero external dependencies.

## Features
- Hex encoding/decoding ✓
- RLP encoding/decoding ✓

## RLP Usage

```java
import io.brane.primitives.rlp.*;
import java.nio.charset.StandardCharsets;

// Encode a string
RlpString str = RlpString.of("hello".getBytes(StandardCharsets.US_ASCII));
byte[] encoded = str.encode();

// Encode a list
RlpList list = RlpList.of(
        RlpString.of("cat".getBytes(StandardCharsets.US_ASCII)),
        RlpString.of("dog".getBytes(StandardCharsets.US_ASCII))
);
byte[] encodedList = list.encode();

// Decode
RlpItem decoded = Rlp.decode(encodedList);
```
