# Development

## Requirements

- Git.
- JDK 25 for development; CI also runs JDK 21.
- Network access on the first build to download Maven and build dependencies.

The Maven wrapper pins Maven. Python, Node, Rust, a global Maven installation,
and native compilers are not required to build or test the Java library.
`.java-version` records the preferred JDK for compatible version managers; it
does not install a JDK.

## Set up

Fork the repository on GitHub, clone your fork, and create a branch:

```sh
git clone https://github.com/YOUR-USERNAME/nanoproc.git
cd nanoproc
git remote add upstream https://github.com/akshaysoftware/nanoproc.git
git switch -c feat/my-change
java -version
./mvnw verify
```

Run commands from the repository root. The root `pom.xml` points Maven at
`java/src/main/java` and `java/src/test/java`. Build output remains in `target/`,
so existing artifact coordinates, release metadata, and child-JVM test paths
remain rooted in one place.

## Commands

| Task | Command |
| --- | --- |
| Run tests and packaging checks | `./mvnw verify` |
| Run tests | `./mvnw test` |
| Run one test class | `./mvnw -Dtest=StderrTailTest test` |
| Check Java formatting | `./mvnw fmt:check` |
| Apply Java formatting | `./mvnw fmt:format` |
| Build a clean artifact | `./mvnw clean package` |
| Install the snapshot locally | `./mvnw install` |
| Run the Java example after packaging | `java --class-path 'target/*' examples/java/RunProcess.java` |

On Windows PowerShell, replace `./mvnw` with `.\mvnw.cmd`. For example:

```powershell
.\mvnw.cmd verify
java --class-path 'target/*' examples/java/RunProcess.java
```

`verify` checks formatting and rejects changes that need formatting; it does not
rewrite source files. Apply `fmt:format` explicitly before committing.

## Tests and process fixtures

Java tests are in `java/src/test/java`. `ChildFixture` launches the current JDK
as a child process, so tests do not depend on external language installations.
Use explicit readiness signals for process lifecycle tests and generous outer
timeouts. Ensure fixture processes are cleaned up even when an assertion fails.

CI verifies Linux, macOS, and Windows on Java 21 and 25, then runs the Java
example against the packaged JAR. All matrix jobs must pass the `CI gate`.

## Work on the ecosystem

- Keep protocol changes in [spec/](spec/README.md) and explain compatibility.
- Add shared fixtures under [conformance/](conformance/README.md) as SDKs arrive.
- Keep runnable examples under [examples/](examples/README.md).
- Mark planned SDKs as planned until they have an implementation and tests.
- Update [docs/design.md](docs/design.md) when changing lifecycle guarantees.

There is no shared native core or daemon. Each SDK will use its runtime's process
facilities and implement the documented contract. The Java library must continue
to have zero runtime dependencies.
