package io.github.akshaysoftware.nanoproc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class Capture {
  private final int limit;
  private final ByteArrayOutputStream bytes;

  Capture(int limit) {
    this.limit = limit;
    this.bytes = new ByteArrayOutputStream(Math.min(limit, 8192));
  }

  // Read one byte beyond the remaining allowance to detect overflow without
  // retaining that byte. Never allocate a buffer based on untrusted output.
  boolean drain(InputStream stream) throws IOException {
    byte[] buffer = new byte[8192];
    while (true) {
      int remaining;
      synchronized (this) {
        remaining = limit - bytes.size();
      }
      int wanted = remaining >= buffer.length ? buffer.length : remaining + 1;
      int count = stream.read(buffer, 0, wanted);
      if (count < 0) return false;
      if (count == 0) continue;
      synchronized (this) {
        bytes.write(buffer, 0, Math.min(count, remaining));
      }
      if (count > remaining) return true;
    }
  }

  synchronized byte[] snapshot() {
    return bytes.toByteArray();
  }
}
