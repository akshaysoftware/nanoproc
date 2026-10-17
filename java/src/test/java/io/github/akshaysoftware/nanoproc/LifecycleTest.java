package io.github.akshaysoftware.nanoproc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(25)
class LifecycleTest {
  @TempDir Path directory;

  @Test
  void timeoutCleansUpRootAndObservedDescendant() throws Exception {
    Path rootFile = directory.resolve("root.pid");
    Path childFile = directory.resolve("child.pid");
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var future =
          executor.submit(
              () ->
                  ProcessTest.child("tree", rootFile.toString(), childFile.toString())
                      .timeout(Duration.ofSeconds(5))
                      .run());
      long root = awaitPid(rootFile);
      long child = awaitPid(childFile);
      try {
        assertEquals(ProcessOutcome.TIMED_OUT, future.get(10, TimeUnit.SECONDS).outcome());
        awaitStopped(root);
        awaitStopped(child);
      } finally {
        kill(root);
        kill(child);
      }
    }
  }

  @Test
  void interruptionCleansUpAndRestoresFlag() throws Exception {
    Path file = directory.resolve("interrupted.pid");
    var failure = new AtomicReference<Throwable>();
    Thread caller =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    ProcessTest.child("ready", file.toString()).run();
                    failure.set(new AssertionError("Expected interruption"));
                  } catch (InterruptedException expected) {
                    if (!Thread.currentThread().isInterrupted())
                      failure.set(new AssertionError("Flag lost"));
                  } catch (Throwable unexpected) {
                    failure.set(unexpected);
                  }
                });
    long pid = awaitPid(file);
    try {
      caller.interrupt();
      caller.join(5000);
      assertFalse(caller.isAlive());
      assertNull(failure.get());
      awaitStopped(pid);
    } finally {
      caller.interrupt();
      kill(pid);
    }
  }

  @Test
  void blockedStdinDoesNotPreventTimeout() throws Exception {
    Path file = directory.resolve("blocked.pid");
    ProcessResult result =
        ProcessTest.child("no-read", file.toString())
            .stdin(new byte[4 * 1024 * 1024])
            .timeout(Duration.ofSeconds(3))
            .run();
    assertEquals(ProcessOutcome.TIMED_OUT, result.outcome());
    long pid = awaitPid(file);
    try {
      awaitStopped(pid);
    } finally {
      kill(pid);
    }
  }

  @Test
  void alreadyInterruptedCallerDoesNotLaunchProcess() throws Exception {
    Path file = directory.resolve("never.pid");
    Thread.currentThread().interrupt();
    try {
      assertThrows(
          InterruptedException.class, () -> ProcessTest.child("ready", file.toString()).run());
      assertTrue(Thread.currentThread().isInterrupted());
      assertFalse(Files.exists(file));
    } finally {
      Thread.interrupted();
    }
  }

  private static long awaitPid(Path file) throws Exception {
    long started = System.nanoTime();
    while (System.nanoTime() - started < 10_000_000_000L) {
      if (Files.exists(file)) {
        String value = Files.readString(file).trim();
        if (!value.isEmpty()) return Long.parseLong(value);
      }
      Thread.sleep(10);
    }
    throw new AssertionError("Child did not become ready: " + file);
  }

  private static void awaitStopped(long pid) throws Exception {
    long started = System.nanoTime();
    while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)
        && System.nanoTime() - started < 5_000_000_000L) Thread.sleep(10);
    assertFalse(
        ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false),
        "Process still alive: " + pid);
  }

  private static void kill(long pid) {
    ProcessHandle.of(pid).filter(ProcessHandle::isAlive).ifPresent(ProcessHandle::destroyForcibly);
  }
}
