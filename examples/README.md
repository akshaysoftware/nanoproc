# Examples

Examples are complete programs run from the repository root against a packaged
Java library. They do not depend on the test fixtures or a published artifact.

| Example | Demonstrates | Requirements |
| --- | --- | --- |
| [Java stdin/stdout round trip](java/README.md) | Direct process launch, input bytes, output limits, and result handling | JDK 21+ |

Build and run:

```sh
./mvnw verify
java --class-path 'target/*' examples/java/RunProcess.java
```

In Windows PowerShell use `.\mvnw.cmd verify`. CI runs the example on every
supported OS/JDK combination after packaging.

Cross-language examples will accompany implemented worker SDKs. The planned
Python, TypeScript, and Rust directories are not runtime requirements for this
example.
