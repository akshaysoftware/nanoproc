# Examples

Examples are complete programs run from the repository root against a packaged
Java library. They do not depend on the test fixtures or a published artifact.

| Example | Demonstrates | Requirements |
| --- | --- | --- |
| [Java stdin/stdout round trip](java/README.md) | Direct process launch, input bytes, output limits, and result handling | JDK 21+ |
| [Java-to-Python worker](python/README.md) | Repeated framed calls to a persistent Python handler, including empty and binary payloads | JDK 21+ and Python 3.11+ |

Build and run:

```sh
./mvnw verify
java --class-path 'target/*' examples/java/RunProcess.java
```

In Windows PowerShell use `.\mvnw.cmd verify`. CI runs the example on every
supported OS/JDK combination after packaging.

The Java-to-Python example has its own environment setup in the linked guide.
Python, TypeScript, and Rust are not runtime requirements for the Java-only
example.
