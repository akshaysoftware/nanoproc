# Nanoproc

**Local workers. Multiple languages. Explicit process boundaries.**

Nanoproc provides small APIs for running child processes and exchanging messages
with persistent local workers. Keep your application in its existing language and
reuse another language's libraries through a separate process.

The current implementations are a **pre-release Java host library** for Java 21+
and a **Python worker helper** for Python 3.11+, both with zero runtime
dependencies. TypeScript and Rust SDKs are planned; their directories are
scaffolding, not installable packages. The raw-byte worker protocol is a draft,
and Java worker lifecycle hardening is in progress.

## Use cases

- Run a command with concurrent I/O, an execution deadline, and bounded output.
- Reuse a persistent process across multiple binary request/response exchanges.
- Integrate an existing scientific, ML, or native computation program locally.

Nanoproc is designed for application-owned workers. It does not require a
listening port, broker, or separately deployed service. Applications supply the
worker executable, its runtime, and its dependencies.

## Get started with Java

Use JDK 25 for development; the library targets Java 21.

```sh
./mvnw verify
java --class-path 'target/*' examples/java/RunProcess.java
```

The example sends stdin to a Java child process and checks the returned bytes.
It needs no Python or Node installation. In Windows PowerShell, use
`.\mvnw.cmd verify` for the build; the `java` command is the same.

See the [Java guide](java/README.md) for API usage and local installation, and
[examples](examples/README.md) for complete runnable programs. The artifact has
not been published to Maven Central.

## Add a Python worker

Install the [Python worker package](python/README.md) from this repository and
adapt a synchronous function with `serve(handler)`. Each request and response is
an opaque `bytes` payload. The handler's state persists between calls, while
the Java host controls process launch and call deadlines.

See the [Java-to-Python example](examples/python/README.md) for environment setup
and a complete binary round trip. The Python package is not published to PyPI.

## One protocol, independent implementations

A host SDK owns process launch, calls, diagnostics, and shutdown. A worker SDK
adapts application code to the same byte protocol. A compatible worker can be
reused by hosts in different languages without depending on their SDKs.

The [draft specification](spec/README.md) defines framing separately from payload
encoding. The initial transport uses stdin/stdout with a four-byte big-endian
length header; stderr carries diagnostics. There is no handshake, shared object
model, or automatic cross-language type conversion.

| Directory | Purpose | Status |
| --- | --- | --- |
| [java/](java/README.md) | Java host library and tests | Implemented; pre-release |
| [python/](python/README.md) | Python worker helper | Implemented; pre-release |
| [typescript/](typescript/README.md) | Node/TypeScript host SDK | Planned |
| [rust/](rust/README.md) | Rust worker SDK | Planned |
| [spec/](spec/README.md) | Protocol and compatibility contract | Draft |
| [conformance/](conformance/README.md) | Shared compatibility fixtures and harness | Planned |
| [examples/](examples/README.md) | Runnable integration examples | Java and Java-to-Python available |
| [docs/](docs/README.md) | Architecture and roadmap | Available |

The root `pom.xml` and Maven wrapper build the Java sources in `java/src/`.
SDK releases will identify their supported protocol/profile separately from
their package version. Cross-language compatibility is not yet certified.

## Process boundaries

Nanoproc is **not an operating-system sandbox**. Child processes inherit the
permissions of their execution environment. It does not impose CPU, child-memory,
or GPU quotas, and a timed-out operation may already have performed side effects.

Read the [design and current limitations](docs/design.md) before using the
experimental worker API. Descendant cleanup is best effort for one-shot commands;
the worker cleanup implementation still needs hardening.

## Contribute

See [DEVELOPMENT.md](DEVELOPMENT.md) for setup and commands,
[CONTRIBUTING.md](CONTRIBUTING.md) for contribution expectations, and the
[roadmap](docs/roadmap.md) for the next milestones. Security reports should follow
[SECURITY.md](SECURITY.md).

## License

[Apache-2.0](LICENSE).
