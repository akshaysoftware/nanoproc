import io.github.akshaysoftware.nanoproc.Nanoproc;
import io.github.akshaysoftware.nanoproc.ProcessResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

/** Runs a child JVM with bounded I/O and verifies a complete stdin/stdout round trip. */
public final class RunProcess {
  public static void main(String[] args) throws Exception {
    String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    Path java = Path.of(System.getProperty("java.home"), "bin", executable);
    Path child = Path.of("examples", "java", "Echo.java").toAbsolutePath();
    byte[] request = "Hello from nanoproc.\n".getBytes(StandardCharsets.UTF_8);

    // Arguments are passed directly. Closing stdin lets this one-shot child finish.
    ProcessResult result =
        Nanoproc.command(java.toString(), child.toString())
            .stdin(request)
            .timeout(Duration.ofSeconds(10))
            .maxStdoutBytes(1024)
            .maxStderrBytes(64 * 1024)
            .run();

    if (!result.successful()) {
      throw new IOException("Child failed: " + result.outcome() + ": " + result.stderrUtf8());
    }
    if (!Arrays.equals(request, result.stdout())) {
      throw new IOException("Child response differs from its input");
    }
    System.out.print(result.stdoutUtf8());
  }
}
