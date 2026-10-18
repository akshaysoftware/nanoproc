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
example against the packaged JAR. On Java 25 it also installs the Python worker
package and runs the Java-to-Python example on each OS. All Java and Python
matrix jobs must pass the `CI gate`.

## Python worker development

Install [uv](https://docs.astral.sh/uv/getting-started/installation/) 0.12.23 or
newer. The worker requires Python 3.11+; uv can install a compatible interpreter.
From the repository root:

```sh
uv sync --project python --locked
uv run --directory python --locked pytest
uv build --directory python
```

These commands also work in Windows PowerShell. uv creates `python/.venv` and
installs the development dependencies recorded in `python/uv.lock`. It activates
that environment for commands run with `uv run`; manual activation is optional.
Python-only tests do not require Java. To run the cross-language example after
Maven packaging:

```sh
uv run --project python --locked java --class-path 'target/*' examples/java/CallPythonWorker.java python
```

CI uses `uv sync --locked --no-editable` and pytest to test the installed package
on Python 3.11 and 3.14 across Linux, macOS, and Windows. Framing tests use
independent wire bytes; subprocess tests check binary stdio, flushing before
EOF, clean shutdown on EOF, and nonzero exits on errors. They do not certify the
Java host's outstanding lifecycle guarantees.

For a deliberate dependency update, run `uv lock --directory python --upgrade`,
rerun tests, and commit the lock file with related manifest changes. Dependabot
also checks the uv project. pytest is a development dependency only.

## Work on the ecosystem

- Keep protocol changes in [spec/](spec/README.md) and explain compatibility.
- Add shared fixtures under [conformance/](conformance/README.md) as SDKs arrive.
- Keep runnable examples under [examples/](examples/README.md).
- Mark planned SDKs as planned until they have an implementation and tests.
- Update [docs/design.md](docs/design.md) when changing lifecycle guarantees.

There is no shared native core or daemon. Each SDK will use its runtime's process
facilities and implement the documented contract. The Java library must continue
to have zero runtime dependencies.
