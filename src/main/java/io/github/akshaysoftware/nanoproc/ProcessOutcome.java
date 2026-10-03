package io.github.akshaysoftware.nanoproc;

/** The reason process execution finished. Non-zero exits are still EXITED. */
public enum ProcessOutcome {

  /** Process exited and stdin/stdout/stderr handling completed. */
  EXITED,

  /** The execution or I/O deadline expired. */
  TIMED_OUT,

  /** More stdout bytes were observed than allowed. */
  STDOUT_LIMIT_EXCEEDED,

  /** More stderr bytes were observed than allowed. */
  STDERR_LIMIT_EXCEEDED
}
