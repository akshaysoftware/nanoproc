import io.github.akshaysoftware.nanoproc.Nanoproc;
import io.github.akshaysoftware.nanoproc.Worker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

/** Reuses a Python worker for text, empty, and binary requests. */
public final class CallPythonWorker {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("Usage: CallPythonWorker.java <python-executable>");
    }
    Path script = Path.of("examples", "python", "echo_worker.py").toAbsolutePath();
    byte[] binary = new byte[258];
    for (int i = 0; i < binary.length; i++) {
      binary[i] = (byte) i;
    }
    byte[][] requests = {
      "Hello from Java.\n".getBytes(StandardCharsets.UTF_8), new byte[0], binary
    };

    // Use the interpreter from the environment where ./python was installed.
    try (Worker worker =
        Nanoproc.worker(args[0], "-u", script.toString())
            .maxFrameBytes(1024 * 1024)
            .callTimeout(Duration.ofSeconds(10))
            .start()) {
      for (byte[] request : requests) {
        if (!Arrays.equals(request, worker.call(request))) {
          throw new IOException("Python response differs from its input");
        }
      }
      System.out.println("Completed 3 calls to Python worker " + worker.pid());
    }
  }
}
