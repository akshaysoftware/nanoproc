# Nanoproc for Java

The Java host library runs bounded one-shot commands and experimental persistent
raw-byte workers. It targets Java 21 and has zero runtime dependencies.

Sources and tests live in `src/main/java` and `src/test/java` under this directory.
The Maven build is defined by the repository-root `pom.xml`; run the wrapper
from the repository root.

## Run a command

```java
import io.github.akshaysoftware.nanoproc.Nanoproc;
import io.github.akshaysoftware.nanoproc.ProcessResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

ProcessResult result = Nanoproc.command("python", "analyse.py")
    .stdin("input".getBytes(StandardCharsets.UTF_8))
    .timeout(Duration.ofSeconds(10))
    .maxStdoutBytes(1024 * 1024)
    .maxStderrBytes(64 * 1024)
    .run();

if (result.successful()) {
  System.out.print(result.stdoutUtf8());
} else {
  System.err.println(result.outcome());
  System.err.print(result.stderrUtf8());
}
```

Supply your own executable and script. For a self-contained example using only
the JDK, see [RunProcess](../examples/java/README.md).

Arguments are passed directly; no shell is inserted. `run()` throws `IOException`
for startup or I/O failure and `InterruptedException` for interruption. Nonzero
exits, execution deadlines, and captured-output overflow are result outcomes.
Captured stdout/stderr are byte prefixes, and array accessors return copies.

The deadline begins after process creation and covers I/O completion. Cleanup
adds bounded waits; descendant termination is best effort.

## Persistent workers

`Nanoproc.worker(argv).start()` starts an application-owned process. `call(byte[])`
writes one frame and reads one response. `stderrTail()` returns a copied suffix
of recent diagnostics, and `close()` initiates shutdown. Only one call is admitted
at a time; another active call is rejected rather than queued.

The child must implement the [draft raw-byte protocol](../spec/raw-byte-worker.md).
Ordinary programs that read stdin until EOF are not persistent framed workers.
Timeouts, framing failures, or transport errors make the worker unavailable for
further calls. A failed request is not automatically replayed.

The worker API is experimental. Read the
[current lifecycle limitations](../docs/design.md#current-worker-limitations)
before relying on shutdown or interruption behaviour.

## Build and local installation

From the repository root:

```sh
./mvnw verify
./mvnw install
```

The artifact is not yet published to Maven Central. After local installation,
another Maven project can depend on:

```xml
<dependency>
  <groupId>io.github.akshaysoftware</groupId>
  <artifactId>nanoproc</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

See [development instructions](../DEVELOPMENT.md) for Windows commands and
focused test runs.
