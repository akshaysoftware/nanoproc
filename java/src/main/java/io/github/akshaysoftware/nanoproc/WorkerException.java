package io.github.akshaysoftware.nanoproc;

import java.io.IOException;

public class WorkerException extends IOException {

  public enum Reason {
    PROTOCOL,
    TIMED_OUT,
    BUSY,
    UNAVAILABLE
  }

  private final Reason reason;

  public WorkerException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public WorkerException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason getReason() {
    return reason;
  }
}
