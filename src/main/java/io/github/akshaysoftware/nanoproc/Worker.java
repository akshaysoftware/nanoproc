package io.github.akshaysoftware.nanoproc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A persistent, bounded child process worker supporting framed binary exchanges with strict call
 * deadlines, thread-safe state management, and background diagnostic retention.
 */
public final class Worker implements AutoCloseable {

  /** Lifecycle states of the worker. */
  public enum State {
    /** Accepting new calls. */
    OPEN,
    /** One exchange is currently active. */
    BUSY,
    /** An exchange or process failure occurred; further calls are unsafe. */
    BROKEN,
    /** Explicitly closed. */
    CLOSED
  }

  /** Immutable configuration builder for {@link Worker}. */
  public static final class Builder {
    private final List<String> argv;
    private final Map<String, String> environment = new HashMap<>();
    private Path directory;
    private int maxFrameBytes = 1024 * 1024;
    private int stderrTailBytes = 64 * 1024;
    private Duration callTimeout = Duration.ofSeconds(30);

    Builder(List<String> argv) {
      for (String arg : argv) {
        if (arg == null) {
          throw new NullPointerException("argv element cannot be null");
        }
      }
      this.argv = List.copyOf(argv);
    }

    /** Sets environment variable overrides for the child process. */
    public Builder environment(Map<String, String> env) {
      if (env == null) {
        throw new NullPointerException("env cannot be null");
      }
      Builder copy = new Builder(this.argv);
      copy.environment.putAll(this.environment);
      copy.environment.putAll(env);
      copy.directory = this.directory;
      copy.maxFrameBytes = this.maxFrameBytes;
      copy.stderrTailBytes = this.stderrTailBytes;
      copy.callTimeout = this.callTimeout;
      return copy;
    }

    /** Sets the working directory for the child process. */
    public Builder directory(Path directory) {
      Builder copy = new Builder(this.argv);
      copy.environment.putAll(this.environment);
      copy.directory = directory;
      copy.maxFrameBytes = this.maxFrameBytes;
      copy.stderrTailBytes = this.stderrTailBytes;
      copy.callTimeout = this.callTimeout;
      return copy;
    }

    /** Sets the maximum allowed frame payload size in bytes. */
    public Builder maxFrameBytes(int maxFrameBytes) {
      if (maxFrameBytes <= 0) {
        throw new IllegalArgumentException("maxFrameBytes must be positive");
      }
      Builder copy = new Builder(this.argv);
      copy.environment.putAll(this.environment);
      copy.directory = this.directory;
      copy.maxFrameBytes = maxFrameBytes;
      copy.stderrTailBytes = this.stderrTailBytes;
      copy.callTimeout = this.callTimeout;
      return copy;
    }

    /** Sets the capacity of the stderr tail buffer in bytes. */
    public Builder stderrTailBytes(int stderrTailBytes) {
      if (stderrTailBytes < 0) {
        throw new IllegalArgumentException("stderrTailBytes cannot be negative");
      }
      Builder copy = new Builder(this.argv);
      copy.environment.putAll(this.environment);
      copy.directory = this.directory;
      copy.maxFrameBytes = this.maxFrameBytes;
      copy.stderrTailBytes = stderrTailBytes;
      copy.callTimeout = this.callTimeout;
      return copy;
    }

    /** Sets the maximum duration allowed per call exchange (default: 30 seconds). */
    public Builder callTimeout(Duration callTimeout) {
      if (callTimeout == null) {
        throw new NullPointerException("callTimeout cannot be null");
      }
      if (callTimeout.isNegative() || callTimeout.isZero()) {
        throw new IllegalArgumentException("callTimeout must be positive");
      }
      Builder copy = new Builder(this.argv);
      copy.environment.putAll(this.environment);
      copy.directory = this.directory;
      copy.maxFrameBytes = this.maxFrameBytes;
      copy.stderrTailBytes = this.stderrTailBytes;
      copy.callTimeout = callTimeout;
      return copy;
    }

    /** Launches the child process and returns a live {@link Worker}. */
    public Worker start() throws IOException {
      return new Worker(argv, environment, directory, maxFrameBytes, stderrTailBytes, callTimeout);
    }
  }

  private final Process process;
  private final OutputStream stdin;
  private final InputStream stdout;
  private final InputStream stderr;
  private final int maxFrameBytes;
  private final Duration callTimeout;
  private final StderrTail stderrTail;
  private final Thread stderrReaderThread;
  private final Thread processExitMonitorThread;
  private final CompletableFuture<Void> cleanupFuture = new CompletableFuture<>();

  private State state = State.OPEN;
  private Object activeExchangeToken = null;
  private boolean cleanupInitiated = false;

  Worker(
      List<String> argv,
      Map<String, String> env,
      Path dir,
      int maxFrameBytes,
      int stderrTailBytes,
      Duration callTimeout)
      throws IOException {
    this.maxFrameBytes = maxFrameBytes;
    this.callTimeout = callTimeout;
    this.stderrTail = new StderrTail(stderrTailBytes);

    ProcessBuilder pb = new ProcessBuilder(argv);
    if (dir != null) {
      pb.directory(dir.toFile());
    }
    if (!env.isEmpty()) {
      pb.environment().putAll(env);
    }

    this.process = pb.start();
    this.stdin = process.getOutputStream();
    this.stdout = process.getInputStream();
    this.stderr = process.getErrorStream();

    this.stderrReaderThread = Thread.ofVirtual().start(this::drainStderr);
    this.processExitMonitorThread = Thread.ofVirtual().start(this::monitorProcessExit);
  }

  private void drainStderr() {
    byte[] buffer = new byte[8192];
    try {
      while (true) {
        int read = stderr.read(buffer);
        if (read == -1) {
          break;
        }
        if (read > 0) {
          stderrTail.append(buffer, 0, read);
        }
      }
    } catch (IOException e) {
      // Stream closed or error reading stderr
    }
  }

  private void monitorProcessExit() {
    try {
      process.waitFor();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      triggerBreakdownAndCleanup();
    }
  }

  /**
   * Executes a framed request/response exchange with the child process.
   *
   * @param request the payload bytes to send
   * @return the response payload bytes
   * @throws WorkerException if a protocol violation, timeout, or unavailability occurs
   * @throws IOException if an I/O transport error occurs
   * @throws InterruptedException if the calling thread is interrupted during the exchange
   */
  public byte[] call(byte[] request) throws WorkerException, IOException, InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException("Thread was interrupted prior to call admission");
    }
    if (request == null) {
      throw new NullPointerException("Request cannot be null");
    }
    if (request.length > maxFrameBytes) {
      throw new IllegalArgumentException(
          "Request size exceeds maxFrameBytes: " + request.length + " > " + maxFrameBytes);
    }

    byte[] requestCopy = request.clone();
    Object exchangeToken = new Object();

    synchronized (this) {
      if (state == State.CLOSED || state == State.BROKEN) {
        throw new WorkerException(
            WorkerException.Reason.UNAVAILABLE, "Worker is UNAVAILABLE (state: " + state + ")");
      }
      if (state == State.BUSY) {
        throw new WorkerException(WorkerException.Reason.BUSY, "Worker is BUSY");
      }
      state = State.BUSY;
      activeExchangeToken = exchangeToken;
    }

    long startTime = System.nanoTime();
    CompletableFuture<byte[]> responseFuture = new CompletableFuture<>();

    Thread.ofVirtual()
        .start(
            () -> {
              try {
                FrameIO.writeFrame(stdin, requestCopy, maxFrameBytes);
                byte[] response = FrameIO.readFrame(stdout, maxFrameBytes);
                responseFuture.complete(response);
              } catch (Throwable t) {
                responseFuture.completeExceptionally(t);
              }
            });

    try {
      long elapsedNanos = System.nanoTime() - startTime;
      long remainingNanos = callTimeout.toNanos() - elapsedNanos;
      if (remainingNanos <= 0) {
        responseFuture.cancel(true);
        triggerBreakdownAndCleanup();
        throw new WorkerException(
            WorkerException.Reason.TIMED_OUT, "Call timed out before execution");
      }

      byte[] response = responseFuture.get(remainingNanos, TimeUnit.NANOSECONDS);

      synchronized (this) {
        if (state == State.BUSY && activeExchangeToken == exchangeToken) {
          state = State.OPEN;
          activeExchangeToken = null;
        }
      }
      return response;

    } catch (TimeoutException e) {
      responseFuture.cancel(true);
      triggerBreakdownAndCleanup();
      throw new WorkerException(
          WorkerException.Reason.TIMED_OUT, "Call timed out after " + callTimeout, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      responseFuture.cancel(true);
      triggerBreakdownAndCleanup();
      throw e;
    } catch (ExecutionException e) {
      responseFuture.cancel(true);
      triggerBreakdownAndCleanup();
      Throwable cause = e.getCause();
      if (cause instanceof WorkerException) {
        throw (WorkerException) cause;
      }
      if (cause instanceof IOException) {
        throw (IOException) cause;
      }
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      throw new IOException("Exchange failed: " + cause.getMessage(), cause);
    }
  }

  /** Returns an independent snapshot of the current stderr diagnostic tail. */
  public byte[] stderrTail() {
    return stderrTail.snapshot();
  }

  /** Returns the operating system process ID of the worker. */
  public long pid() {
    return process.pid();
  }

  /** Returns the current lifecycle state of the worker. */
  public synchronized State state() {
    return state;
  }

  @Override
  public void close() {
    synchronized (this) {
      if (state == State.CLOSED) {
        return;
      }
      state = State.CLOSED;
    }
    triggerBreakdownAndCleanup();
    try {
      cleanupFuture.get(5, TimeUnit.SECONDS);
    } catch (Exception ignored) {
    }
  }

  private synchronized void triggerBreakdownAndCleanup() {
    if (state != State.CLOSED) {
      state = State.BROKEN;
    }
    if (!cleanupInitiated) {
      cleanupInitiated = true;
      Thread.ofVirtual().start(this::performCleanup);
    }
  }

  private void performCleanup() {
    try {
      try {
        stdin.close();
      } catch (IOException ignored) {
      }
      try {
        stdout.close();
      } catch (IOException ignored) {
      }
      try {
        stderr.close();
      } catch (IOException ignored) {
      }

      process.destroyForcibly();
      boolean finished = process.waitFor(5, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      cleanupFuture.complete(null);
    }
  }
}
