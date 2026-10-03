package io.github.akshaysoftware.nanoproc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(20)
class ProcessTest {
  static Command child(String... args) {
    String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    // Absolute classpath is essential for the working-directory test.
    String classes = Path.of("target/test-classes").toAbsolutePath().toString();
    var argv = new ArrayList<String>();
    argv.add(Path.of(System.getProperty("java.home"), "bin", executable).toString());
    argv.add("-cp");
    argv.add(classes);
    argv.add(ChildFixture.class.getName());
    argv.addAll(Arrays.asList(args));
    return Nanoproc.command(argv.toArray(String[]::new)).timeout(Duration.ofSeconds(10));
  }

  @Test
  void roundTripsStdinLargerThanTypicalPipeBuffers() throws Exception {
    byte[] input = new byte[256 * 1024];
    Arrays.fill(input, (byte) 65);
    ProcessResult result = child("echo").stdin(input).run();
    assertTrue(result.successful());
    assertArrayEquals(input, result.stdout());
  }

  @Test
  void drainsBothStreamsConcurrently() throws Exception {
    ProcessResult result = child("both").maxStderrBytes(300_000).run();
    assertTrue(result.successful());
    assertEquals(256 * 1024, result.stdout().length);
    assertEquals(256 * 1024, result.stderr().length);
  }

  @Test
  void nonzeroExitIsAnOutcome() throws Exception {
    ProcessResult result = child("exit").run();
    assertEquals(ProcessOutcome.EXITED, result.outcome());
    assertEquals(7, result.exitCode().orElseThrow());
    assertEquals("diagnostic", result.stderrUtf8());
    assertFalse(result.successful());
  }

  @Test
  void argumentsAreNotShellParsed() throws Exception {
    assertEquals("hello ; $(ignored) *", child("args", "hello ; $(ignored) *").run().stdoutUtf8());
  }

  @Test
  void environmentAndDirectoryAreApplied(@TempDir Path directory) throws Exception {
    assertEquals(
        "value", child("env").environment("NANOPROC_TEST_VALUE", "value").run().stdoutUtf8());
    assertEquals(
        directory.toRealPath().toString(), child("cwd").directory(directory).run().stdoutUtf8());
  }

  @Test
  void timeoutReturnsAnOutcome() throws Exception {
    assertEquals(
        ProcessOutcome.TIMED_OUT, child("sleep").timeout(Duration.ofSeconds(1)).run().outcome());
  }

  @Test
  void outputLimitsRetainOnlyBoundedPrefixes() throws Exception {
    ProcessResult stdout = child("stdout").maxStdoutBytes(1024).run();
    assertEquals(ProcessOutcome.STDOUT_LIMIT_EXCEEDED, stdout.outcome());
    assertEquals(1024, stdout.stdout().length);
    ProcessResult stderr = child("stderr").maxStderrBytes(1024).run();
    assertEquals(ProcessOutcome.STDERR_LIMIT_EXCEEDED, stderr.outcome());
    assertEquals(1024, stderr.stderr().length);
  }

  @Test
  void exactAndZeroLimitsWork() throws Exception {
    assertTrue(child("exact", "8").maxStdoutBytes(8).run().successful());
    assertTrue(child("exact", "0").maxStdoutBytes(0).run().successful());
    assertEquals(
        ProcessOutcome.STDOUT_LIMIT_EXCEEDED,
        child("exact", "1").maxStdoutBytes(0).run().outcome());
  }

  @Test
  void missingExecutableIsAnInfrastructureFailure(@TempDir Path directory) {
    assertThrows(
        IOException.class, () -> Nanoproc.command(directory.resolve("missing").toString()).run());
  }
}
