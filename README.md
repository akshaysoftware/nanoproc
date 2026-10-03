# Nanoproc

Nanoproc: tiny, safe child processes and persistent workers for Java.

**Status:** pre-release. Java 21+; zero runtime dependencies.

Nanoproc provides a small layer above `ProcessBuilder`: concurrent process I/O,
execution deadlines, bounded captured output, and best-effort cleanup.
Persistent framed workers are planned for v0.1.0.

Here, **safe** means bounded process management. Nanoproc is **not a sandbox**:
children retain filesystem, network, environment, and OS permissions.
It does not impose CPU or child-memory quotas or make untrusted code safe.

## Usage

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

`run()` throws `IOException` for startup/I/O failure and `InterruptedException`
for interruption. Non-zero exits, deadlines, and output overflow are result
outcomes. Captured data is a byte prefix; `stdout()` and `stderr()` return copies.

Deadlines begin after process creation and include I/O completion. Cleanup adds
bounded waits. Descendant cleanup is best effort. Read [the contract](docs/design.md)
for details, including pipe inheritance and partial output.

The artifact has not been published to Maven Central. For local experiments:

```bash
./mvnw install
```

Then add this dependency to another local Maven project:

```xml
<dependency>
  <groupId>io.github.akshaysoftware</groupId>
  <artifactId>nanoproc</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Development

Use JDK 25 by default; the library compiles for Java 21.

```bash
./mvnw fmt:format
./mvnw verify
```

On Windows PowerShell, use `.\mvnw.cmd` in place of `./mvnw`.

## Contributing

Bug reports, documentation improvements, and small focused PRs are welcome.
See [CONTRIBUTING.md](CONTRIBUTING.md), [the roadmap](docs/roadmap.md),
and [the design contract](docs/design.md).

## License

Apache-2.0. See [LICENSE](LICENSE).