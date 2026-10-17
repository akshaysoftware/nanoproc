package io.github.akshaysoftware.nanoproc;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Immutable outcome and captured byte prefixes.
 *
 * @param outcome reason execution ended
 * @param exitCode observed exit code, or empty if unavailable after cleanup
 * @param stdout captured stdout prefix
 * @param stderr captured stderr prefix
 */
public record ProcessResult(
    ProcessOutcome outcome, OptionalInt exitCode, byte[] stdout, byte[] stderr) {
  /**
   * Creates a result, copying captured arrays.
   *
   * @param outcome reason execution ended
   * @param exitCode observed exit code
   * @param stdout captured stdout
   * @param stderr captured stderr
   */
  public ProcessResult {
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(exitCode, "exitCode");
    if (outcome == ProcessOutcome.EXITED && exitCode.isEmpty()) {
      throw new IllegalArgumentException("EXITED requires an exit code");
    }
    stdout = stdout.clone();
    stderr = stderr.clone();
  }

  /**
   * Returns captured stdout without exposing internal storage.
   *
   * @return a copy of captured stdout
   */
  @Override
  public byte[] stdout() {
    return stdout.clone();
  }

  /**
   * Returns captured stderr without exposing internal storage.
   *
   * @return a copy of captured stderr
   */
  @Override
  public byte[] stderr() {
    return stderr.clone();
  }

  /**
   * Tests whether execution completed successfully.
   *
   * @return whether the process exited with code zero and complete I/O
   */
  public boolean successful() {
    return outcome == ProcessOutcome.EXITED && exitCode.orElse(-1) == 0;
  }

  /**
   * Decodes the captured stdout prefix as UTF-8.
   *
   * @return stdout decoded as UTF-8, replacing malformed bytes
   */
  public String stdoutUtf8() {
    return new String(stdout, StandardCharsets.UTF_8);
  }

  /**
   * Decodes the captured stderr prefix as UTF-8.
   *
   * @return stderr decoded as UTF-8, replacing malformed bytes
   */
  public String stderrUtf8() {
    return new String(stderr, StandardCharsets.UTF_8);
  }
}
