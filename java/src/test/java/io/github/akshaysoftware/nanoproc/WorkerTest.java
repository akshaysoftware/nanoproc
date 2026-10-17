package io.github.akshaysoftware.nanoproc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class WorkerTest {

  @Test
  void callTimeoutTriggersTimedOutException() throws Exception {
    var cmd = ChildFixture.javaCommand("framed-hang-read", "1048576");
    try (Worker worker =
        Nanoproc.worker(cmd.toArray(new String[0])).callTimeout(Duration.ofMillis(200)).start()) {
      WorkerException ex =
          assertThrows(
              WorkerException.class, () -> worker.call("test".getBytes(StandardCharsets.UTF_8)));
      assertEquals(WorkerException.Reason.TIMED_OUT, ex.getReason());
      assertEquals(Worker.State.BROKEN, worker.state());
    }
  }

  @Test
  void exchangeSucceedsWithinTimeout() throws Exception {
    var cmd = ChildFixture.javaCommand("framed-slow", "100", "1048576");
    try (Worker worker =
        Nanoproc.worker(cmd.toArray(new String[0])).callTimeout(Duration.ofSeconds(5)).start()) {
      byte[] resp = worker.call("quick".getBytes(StandardCharsets.UTF_8));
      assertEquals("quick", new String(resp, StandardCharsets.UTF_8));
      assertEquals(Worker.State.OPEN, worker.state());
    }
  }

  @Test
  void interruptedCallerRejectsBeforeAdmission() throws Exception {
    var cmd = ChildFixture.javaCommand("framed-echo", "1048576");
    try (Worker worker = Nanoproc.worker(cmd.toArray(new String[0])).start()) {
      Thread.currentThread().interrupt();
      try {
        assertThrows(
            InterruptedException.class, () -> worker.call("test".getBytes(StandardCharsets.UTF_8)));
      } finally {
        Thread.interrupted(); // clear interrupted flag
      }
      // Worker should remain usable after pre-admission rejection
      byte[] resp = worker.call("ok".getBytes(StandardCharsets.UTF_8));
      assertEquals("ok", new String(resp, StandardCharsets.UTF_8));
    }
  }

  @Test
  void malformedResponseBreaksWorker() throws Exception {
    var cmd = ChildFixture.javaCommand("framed-malformed", "1048576");
    try (Worker worker = Nanoproc.worker(cmd.toArray(new String[0])).start()) {
      WorkerException ex =
          assertThrows(
              WorkerException.class, () -> worker.call("trigger".getBytes(StandardCharsets.UTF_8)));
      assertEquals(WorkerException.Reason.PROTOCOL, ex.getReason());

      WorkerException unavailableEx =
          assertThrows(
              WorkerException.class, () -> worker.call("after".getBytes(StandardCharsets.UTF_8)));
      assertEquals(WorkerException.Reason.UNAVAILABLE, unavailableEx.getReason());
    }
  }
}
