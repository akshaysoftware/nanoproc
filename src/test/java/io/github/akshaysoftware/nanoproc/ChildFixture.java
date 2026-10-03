package io.github.akshaysoftware.nanoproc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

/** Child-JVM fixture; no shell, Python, or Node installation is needed. */
public final class ChildFixture {
  public static void main(String[] args) throws Exception {
    switch (args[0]) {
      case "ready" -> {
        Files.writeString(Path.of(args[1]), Long.toString(ProcessHandle.current().pid()));
        Thread.sleep(60_000);
      }
      case "tree" -> {
        Process descendant = new ProcessBuilder(javaCommand("ready", args[2])).start();
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!Files.exists(Path.of(args[2])) && System.nanoTime() < deadline) Thread.sleep(10);
        if (!Files.exists(Path.of(args[2]))) {
          descendant.destroyForcibly();
          throw new IllegalStateException("Child not ready");
        }
        Files.writeString(Path.of(args[1]), Long.toString(ProcessHandle.current().pid()));
        descendant.waitFor();
      }
      case "no-read" -> {
        Files.writeString(Path.of(args[1]), Long.toString(ProcessHandle.current().pid()));
        Thread.sleep(60_000);
      }
      case "echo" -> System.in.transferTo(System.out);
      case "args" -> System.out.print(args[1]);
      case "exit" -> {
        System.err.print("diagnostic");
        System.exit(7);
      }
      case "sleep" -> Thread.sleep(60_000);
      case "env" -> System.out.print(System.getenv("NANOPROC_TEST_VALUE"));
      case "cwd" -> System.out.print(Path.of("").toRealPath());
      case "both" -> {
        byte[] buffer = new byte[8192];
        for (int i = 0; i < 32; i++) {
          System.out.write(buffer);
          System.err.write(buffer);
        }
      }
      case "stdout" -> {
        while (true) {
          System.out.write(new byte[8192]);
          System.out.flush();
        }
      }
      case "stderr" -> {
        while (true) {
          System.err.write(new byte[8192]);
          System.err.flush();
        }
      }
      case "exact" -> System.out.write(new byte[Integer.parseInt(args[1])]);
      default -> throw new IllegalArgumentException(args[0]);
    }
  }

  private static java.util.List<String> javaCommand(String... args) {
    String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    var command = new ArrayList<String>();
    command.add(Path.of(System.getProperty("java.home"), "bin", executable).toString());
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add(ChildFixture.class.getName());
    command.addAll(Arrays.asList(args));
    return command;
  }
}
