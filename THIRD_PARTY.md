# Third-Party Licenses

This document lists the third-party dependencies used by the Brane SDK, along with their
respective licenses. Brane SDK is dual-licensed under Apache-2.0 and MIT (see LICENSE-APACHE
and LICENSE-MIT).

## Runtime Dependencies

These dependencies are included when you use Brane SDK in your project.

### BouncyCastle

- **Packages**: `org.bouncycastle:bcprov-jdk15on`, `org.bouncycastle:bcpkix-jdk15on`
- **Version**: 1.70
- **License**: Bouncy Castle License (MIT-equivalent)
- **Website**: https://www.bouncycastle.org/
- **Used by**: `brane-core`
- **Purpose**: Cryptographic operations (ECDSA signing, key generation, secp256k1)

```
Copyright (c) 2000-2021 The Legion of the Bouncy Castle Inc.
(https://www.bouncycastle.org)

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in the
Software without restriction, including without limitation the rights to use, copy,
modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
and to permit persons to whom the Software is furnished to do so, subject to the
following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

---

### Jackson

- **Package**: `com.fasterxml.jackson.core:jackson-databind`
- **Version**: 2.17.1
- **License**: Apache-2.0
- **Website**: https://github.com/FasterXML/jackson
- **Used by**: `brane-core`, `brane-rpc`, `brane-contract`
- **Purpose**: JSON parsing for ABI definitions and RPC communication

```
Copyright 2007-, Tatu Saloranta (tatu.saloranta@iki.fi)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

### SLF4J

- **Package**: `org.slf4j:slf4j-api`
- **Version**: 2.0.13
- **License**: MIT
- **Website**: https://www.slf4j.org/
- **Used by**: `brane-core`, `brane-rpc`
- **Purpose**: Logging facade

```
Copyright (c) 2004-2024 QOS.ch
All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

---

### Netty

- **Packages**: `io.netty:netty-handler`, `io.netty:netty-codec-http`, `io.netty:netty-transport-native-epoll`, `io.netty:netty-transport-native-kqueue`
- **Version**: 4.1.107.Final
- **License**: Apache-2.0
- **Website**: https://netty.io/
- **Used by**: `brane-rpc`
- **Purpose**: WebSocket transport for JSON-RPC subscriptions

```
Copyright 2014 The Netty Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

### LMAX Disruptor

- **Package**: `com.lmax:disruptor`
- **Version**: 3.4.4
- **License**: Apache-2.0
- **Website**: https://lmax-exchange.github.io/disruptor/
- **Used by**: `brane-rpc`
- **Purpose**: High-performance inter-thread messaging for WebSocket provider

```
Copyright 2011 LMAX Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

### jc-kzg-4844

- **Package**: `io.consensys.protocols:jc-kzg-4844`
- **Version**: 2.1.5
- **License**: Apache-2.0
- **Website**: https://github.com/Consensys/jc-kzg-4844
- **Used by**: `brane-kzg`
- **Purpose**: KZG commitment scheme for EIP-4844 blob transactions

**Bundled Native Libraries**: This library includes native code from:
- [c-kzg-4844](https://github.com/ethereum/c-kzg-4844) (Apache-2.0) - KZG implementation
- [blst](https://github.com/supranational/blst) (Apache-2.0) - BLS12-381 cryptographic library

```
Copyright 2022 Consensys Software Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

### JSpecify

- **Package**: `org.jspecify:jspecify`
- **Version**: 1.0.0
- **License**: Apache-2.0
- **Website**: https://jspecify.dev/
- **Used by**: `brane-rpc`
- **Purpose**: Nullness annotations for API clarity

```
Copyright 2020 The JSpecify Authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## Test Dependencies

These dependencies are only used during development and testing. They are not included
in the distributed artifacts.

| Dependency | Version | License | Purpose |
|------------|---------|---------|---------|
| JUnit Jupiter | 5.10.2 | EPL-2.0 | Unit testing framework |
| Mockito | 5.11.0 | MIT | Mocking framework |
| Logback | 1.5.6 | EPL-1.0 / LGPL-2.1 | Logging implementation for tests |
| Testcontainers | 1.19.7 | MIT | Docker-based integration testing |

---

## Benchmark Dependencies

These dependencies are only used in the `brane-benchmark` module for performance testing.
They are not included in the distributed SDK artifacts.

| Dependency | Version | License | Purpose |
|------------|---------|---------|---------|
| JMH | 1.37 | GPL-2.0 with Classpath Exception | Microbenchmark harness |
| web3j | 4.10.3 | Apache-2.0 | Comparison benchmarks only |

---

## Build Tool Dependencies

These are used only during the build process and are not included in distributed artifacts.

| Dependency | Version | License | Purpose |
|------------|---------|---------|---------|
| Spotless | 6.25.0 | Apache-2.0 | Code formatting |
| JReleaser | 1.21.0 | Apache-2.0 | Release automation |

---

## License Summary

| License | Dependencies |
|---------|--------------|
| **Apache-2.0** | Jackson, Netty, Disruptor, jc-kzg-4844, JSpecify |
| **MIT** | BouncyCastle, SLF4J |

All runtime dependencies use licenses that are compatible with both Apache-2.0 and MIT,
allowing Brane SDK to be dual-licensed under either license at the user's choice.

---

## Transitive Dependencies

The above dependencies may bring in additional transitive dependencies. For a complete
list of all transitive dependencies and their licenses, run:

```bash
./gradlew dependencies --configuration runtimeClasspath
```

To generate a comprehensive license report, consider adding a license reporting plugin
such as [gradle-license-plugin](https://github.com/jk1/Gradle-License-Report) or
[license-gradle-plugin](https://github.com/hierynomus/license-gradle-plugin).

---

## Updating This Document

When adding new dependencies to the project:

1. Add the dependency information to the appropriate section above
2. Include the license text or reference
3. Verify license compatibility with Apache-2.0 and MIT
4. Update the License Summary table if introducing a new license type
