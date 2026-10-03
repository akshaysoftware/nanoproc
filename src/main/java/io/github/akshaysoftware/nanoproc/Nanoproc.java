package io.github.akshaysoftware.nanoproc;

import java.util.List;

/** Entry point for bounded child processes. */
public final class Nanoproc {
  private Nanoproc() {}

  /**
   * Creates an immutable argv-based command with bounded defaults.
   *
   * @param argv executable followed by its arguments; no shell is added
   * @return a reusable command
   * @throws IllegalArgumentException if argv is empty or contains invalid strings
   * @throws NullPointerException if argv or an element is null
   */
  public static Command command(String... argv) {
    return new Command(List.of(argv));
  }
}
