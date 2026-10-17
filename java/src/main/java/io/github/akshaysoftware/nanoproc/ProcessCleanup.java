package io.github.akshaysoftware.nanoproc;

import java.io.IOException;
import java.util.List;
import java.util.Set;

final class ProcessCleanup {
  private static final long WAIT_NANOS = 250_000_000L;

  static void observe(Process process, Set<ProcessHandle> observed) {
    try (var descendants = process.descendants()) {
      descendants.forEach(observed::add);
    } catch (SecurityException ignored) {
      /* Process-tree observation is best effort. */
    }
  }

  static void close(Process process, List<Thread> tasks, Set<ProcessHandle> observed) {
    boolean interrupted = Thread.interrupted();
    try {
      // Descendants before root where the OS permits. Preserve handles observed
      // while the root was alive; reparenting can otherwise hide children.
      for (ProcessHandle handle : observed) {
        if (!handle.equals(process.toHandle())) terminate(handle, false);
      }
      terminate(process.toHandle(), false);
      interrupted |= waitFor(observed, WAIT_NANOS);
      observe(process, observed);
      for (ProcessHandle handle : observed) terminate(handle, true);
      interrupted |= waitFor(observed, WAIT_NANOS);
      for (Thread task : tasks) task.interrupt();
      // Process pipe close can contend with a blocked write. Never perform that
      // close on the caller's thread or use an executor whose close waits forever.
      Thread closer =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      process.getOutputStream().close();
                    } catch (IOException ignored) {
                    }
                    try {
                      process.getInputStream().close();
                    } catch (IOException ignored) {
                    }
                    try {
                      process.getErrorStream().close();
                    } catch (IOException ignored) {
                    }
                  });
      long started = System.nanoTime();
      for (Thread task : tasks) interrupted |= join(task, started);
      interrupted |= join(closer, started);
    } finally {
      if (interrupted) Thread.currentThread().interrupt();
    }
  }

  private static boolean waitFor(Set<ProcessHandle> observed, long budget) {
    boolean interrupted = false;
    long started = System.nanoTime();
    while (observed.stream().anyMatch(ProcessHandle::isAlive)
        && System.nanoTime() - started < budget) {
      try {
        Thread.sleep(10);
      } catch (InterruptedException ignored) {
        interrupted = true;
      }
    }
    return interrupted;
  }

  private static void terminate(ProcessHandle handle, boolean force) {
    try {
      if (handle.isAlive()) {
        if (force) handle.destroyForcibly();
        else handle.destroy();
      }
    } catch (SecurityException | UnsupportedOperationException ignored) {
      // The API cannot guarantee termination on every OS or permission boundary.
    }
  }

  private static boolean join(Thread task, long started) {
    boolean interrupted = false;
    while (task.isAlive()) {
      long remaining = WAIT_NANOS - (System.nanoTime() - started);
      if (remaining <= 0) break;
      try {
        task.join(Math.max(1, remaining / 1_000_000));
      } catch (InterruptedException ignored) {
        interrupted = true;
      }
    }
    return interrupted;
  }
}
