package io.github.akshaysoftware.nanoproc;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class CommandTest {

  @Test
  void configurationDoesNotChangeTheOriginal() {
    String[] argv = {"tool", "argument"};
    byte[] input = {1, 2};
    Command original = Nanoproc.command(argv);
    Command configured =
        original
            .stdin(input)
            .environment("NANOPROC_TEST", "value")
            .timeout(Duration.ofSeconds(2))
            .maxStdoutBytes(10);
    argv[0] = "changed";
    input[0] = 9;
    assertEquals("tool", configured.argv.getFirst());
    assertArrayEquals(new byte[] {1, 2}, configured.input);
    assertEquals(0, original.input.length);
    assertTrue(original.environment.isEmpty());
    assertEquals(Duration.ofSeconds(30), original.timeout);
    assertEquals(1024 * 1024, original.stdoutLimit);
  }

  @Test
  void invalidSettingsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> Nanoproc.command());
    assertThrows(IllegalArgumentException.class, () -> Nanoproc.command(""));
    assertThrows(IllegalArgumentException.class, () -> Nanoproc.command("tool", "a\0b"));
    Command command = Nanoproc.command("tool");
    assertThrows(IllegalArgumentException.class, () -> command.timeout(Duration.ZERO));
    assertThrows(IllegalArgumentException.class, () -> command.timeout(Duration.ofDays(2)));
    assertThrows(IllegalArgumentException.class, () -> command.maxStdoutBytes(-1));
    assertThrows(IllegalArgumentException.class, () -> command.maxStderrBytes(-1));
    assertThrows(IllegalArgumentException.class, () -> command.environment("A=B", "value"));
    assertThrows(NullPointerException.class, () -> command.stdin(null));
  }

  @Test
  void resultsDoNotExposeCallerOrInternalArrays() {
    byte[] bytes = {65};
    var result = new ProcessResult(ProcessOutcome.EXITED, OptionalInt.of(0), bytes, bytes);
    bytes[0] = 66;
    result.stdout()[0] = 67;
    result.stderr()[0] = 68;
    assertEquals("A", result.stdoutUtf8());
    assertEquals("A", result.stderrUtf8());
    assertTrue(result.successful());
    assertFalse(
        new ProcessResult(ProcessOutcome.EXITED, OptionalInt.of(7), bytes, bytes).successful());
  }
}
