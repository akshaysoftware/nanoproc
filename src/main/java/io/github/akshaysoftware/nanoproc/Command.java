package io.github.akshaysoftware.nanoproc;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable process settings. Each configuration method returns a new command.
 *
 * <p>Defaults: empty stdin, inherited environment and directory, a 30-second deadline, 1 MiB
 * stdout, and 64 KiB stderr. Byte limits may be zero. No shell is implicit.
 */
public final class Command {
  final List<String> argv;
  final byte[] input;
  final Map<String, String> environment;
  final Path directory;
  final Duration timeout;
  final int stdoutLimit;
  final int stderrLimit;

  Command(List<String> argv) {
    this(argv, new byte[0], Map.of(), null, Duration.ofSeconds(30), 1024 * 1024, 64 * 1024);
  }

  private Command(
      List<String> argv,
      byte[] input,
      Map<String, String> environment,
      Path directory,
      Duration timeout,
      int stdoutLimit,
      int stderrLimit) {
    this.argv = List.copyOf(argv);
    if (argv.isEmpty() || argv.getFirst().isEmpty()) {
      throw new IllegalArgumentException("An executable is required");
    }
    for (String arg : argv) {
      if (arg.indexOf(0) >= 0) throw new IllegalArgumentException("Arguments cannot contain NUL");
    }
    this.input = input;
    this.environment = Map.copyOf(environment);
    this.directory = directory;
    this.timeout = timeout;
    this.stdoutLimit = stdoutLimit;
    this.stderrLimit = stderrLimit;
  }

  /**
   * Sets stdin bytes; caller-owned bytes are copied.
   *
   * @param bytes input to write, followed by EOF
   * @return a new command
   */
  public Command stdin(byte[] bytes) {
    return new Command(
        argv, bytes.clone(), environment, directory, timeout, stdoutLimit, stderrLimit);
  }

  /**
   * Sets the execution/I/O deadline, measured after process start.
   *
   * @param value positive duration of at most one day
   * @return a new command
   * @throws IllegalArgumentException if outside that range
   */
  public Command timeout(Duration value) {
    Objects.requireNonNull(value, "timeout");
    if (value.isZero() || value.isNegative() || value.compareTo(Duration.ofDays(1)) > 0) {
      throw new IllegalArgumentException("Timeout must be positive and at most one day");
    }
    return new Command(argv, input, environment, directory, value, stdoutLimit, stderrLimit);
  }

  /**
   * Sets the maximum captured stdout bytes; zero rejects any output.
   *
   * @param bytes non-negative byte limit
   * @return a new command
   */
  public Command maxStdoutBytes(int bytes) {
    if (bytes < 0) throw new IllegalArgumentException("Negative stdout limit");
    return new Command(argv, input, environment, directory, timeout, bytes, stderrLimit);
  }

  /**
   * Sets the maximum captured stderr bytes; zero rejects any output.
   *
   * @param bytes non-negative byte limit
   * @return a new command
   */
  public Command maxStderrBytes(int bytes) {
    if (bytes < 0) throw new IllegalArgumentException("Negative stderr limit");
    return new Command(argv, input, environment, directory, timeout, stdoutLimit, bytes);
  }

  /**
   * Adds or overrides an inherited environment variable.
   *
   * @param name non-empty name containing neither '=' nor NUL
   * @param value value containing no NUL
   * @return a new command
   */
  public Command environment(String name, String value) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
    if (name.isEmpty() || name.indexOf('=') >= 0 || name.indexOf(0) >= 0 || value.indexOf(0) >= 0) {
      throw new IllegalArgumentException("Invalid environment entry");
    }
    var overrides = new HashMap<>(environment);
    overrides.put(name, value);
    return new Command(argv, input, overrides, directory, timeout, stdoutLimit, stderrLimit);
  }

  /**
   * Sets the working directory. Relative paths are resolved when configured.
   *
   * @param value non-null working directory
   * @return a new command
   */
  public Command directory(Path value) {
    return new Command(
        argv,
        input,
        environment,
        value.toAbsolutePath().normalize(),
        timeout,
        stdoutLimit,
        stderrLimit);
  }
}
