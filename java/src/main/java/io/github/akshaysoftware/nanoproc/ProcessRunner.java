package io.github.akshaysoftware.nanoproc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

final class ProcessRunner {
  private enum Kind {
    INPUT,
    STDOUT,
    STDERR,
    EXIT
  }

  private record Event(Kind kind, boolean overflow, IOException failure) {}

  @FunctionalInterface
  private interface Operation {
    boolean run() throws IOException, InterruptedException;
  }

  static ProcessResult run(Command command) throws IOException, InterruptedException {
    if (Thread.currentThread().isInterrupted())
      throw new InterruptedException("Interrupted before start");
    ProcessBuilder builder = new ProcessBuilder(command.argv);
    builder.environment().putAll(command.environment);
    if (command.directory != null) builder.directory(command.directory.toFile());
    Process process = builder.start();
    long started = System.nanoTime();
    var stdout = new Capture(command.stdoutLimit);
    var stderr = new Capture(command.stderrLimit);
    // Exactly four tasks, each publishing exactly one event.
    var events = new ArrayBlockingQueue<Event>(4);
    List<Thread> tasks = new ArrayList<>(4);
    var observed = new LinkedHashSet<ProcessHandle>();
    observed.add(process.toHandle());
    ProcessOutcome outcome = ProcessOutcome.EXITED;
    try {
      tasks.add(
          task(
              events,
              Kind.INPUT,
              () -> {
                try (var input = process.getOutputStream()) {
                  input.write(command.input);
                }
                return false;
              }));
      tasks.add(task(events, Kind.STDOUT, () -> stdout.drain(process.getInputStream())));
      tasks.add(task(events, Kind.STDERR, () -> stderr.drain(process.getErrorStream())));
      tasks.add(
          task(
              events,
              Kind.EXIT,
              () -> {
                process.waitFor();
                return false;
              }));
      int completed = 0;
      while (completed < 4) {
        ProcessCleanup.observe(process, observed);
        long remaining = command.timeout.toNanos() - (System.nanoTime() - started);
        if (remaining <= 0) {
          outcome = ProcessOutcome.TIMED_OUT;
          break;
        }
        Event event = events.poll(Math.min(remaining, 20_000_000L), TimeUnit.NANOSECONDS);
        if (event == null) continue;
        if (event.failure != null) throw event.failure;
        if (event.overflow) {
          outcome =
              event.kind == Kind.STDOUT
                  ? ProcessOutcome.STDOUT_LIMIT_EXCEEDED
                  : ProcessOutcome.STDERR_LIMIT_EXCEEDED;
          break;
        }
        completed++;
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw interrupted;
    } finally {
      ProcessCleanup.observe(process, observed);
      ProcessCleanup.close(process, tasks, observed);
    }
    OptionalInt exitCode =
        process.isAlive() ? OptionalInt.empty() : OptionalInt.of(process.exitValue());
    return new ProcessResult(outcome, exitCode, stdout.snapshot(), stderr.snapshot());
  }

  private static Thread task(ArrayBlockingQueue<Event> events, Kind kind, Operation operation) {
    return Thread.ofVirtual()
        .name("nanoproc-" + kind.name().toLowerCase())
        .start(
            () -> {
              try {
                events.add(new Event(kind, operation.run(), null));
              } catch (IOException failure) {
                events.add(new Event(kind, false, failure));
              } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                events.add(
                    new Event(
                        kind, false, new IOException("Process task interrupted", interrupted)));
              } catch (RuntimeException failure) {
                events.add(new Event(kind, false, new IOException("Process task failed", failure)));
              }
            });
  }
}
