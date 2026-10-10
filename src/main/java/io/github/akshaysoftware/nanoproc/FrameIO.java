package io.github.akshaysoftware.nanoproc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class FrameIO {

  /**
   * Writes a framed message: 4-byte big-endian length header followed by the payload. Flushes the
   * stream after writing.
   *
   * @param stream the output stream (ownership remains with caller)
   * @param payload the payload bytes to write
   * @param maxPayloadSize the maximum allowed payload size in bytes
   * @throws IOException if an I/O error occurs
   * @throws IllegalArgumentException if the payload exceeds maxPayloadSize
   */
  public static void writeFrame(OutputStream stream, byte[] payload, int maxPayloadSize)
      throws IOException {
    int length = (payload != null) ? payload.length : 0;

    if (length > maxPayloadSize) {
      throw new IllegalArgumentException(
          "Payload size (" + length + ") exceeds maximum allowed size (" + maxPayloadSize + ").");
    }

    byte[] header =
        new byte[] {
          (byte) (length >> 24), (byte) (length >> 16), (byte) (length >> 8), (byte) length
        };

    stream.write(header);

    if (length > 0) {
      stream.write(payload, 0, length);
    }

    stream.flush();
  }

  /**
   * Reads a complete frame: 4-byte big-endian length header followed by the exact payload. Handles
   * partial reads and validates sizes.
   *
   * @param stream the input stream (ownership remains with caller)
   * @param maxPayloadSize the maximum allowed payload size in bytes
   * @return the complete payload as a byte array
   * @throws WorkerException if a protocol violation occurs (e.g., invalid length, unexpected EOF)
   * @throws IOException if an ordinary transport I/O error occurs
   */
  public static byte[] readFrame(InputStream stream, int maxPayloadSize) throws IOException {
    byte[] header = new byte[4];
    int headerBytesRead = 0;
    while (headerBytesRead < 4) {
      int count = stream.read(header, headerBytesRead, 4 - headerBytesRead);
      if (count == -1) {
        throw new WorkerException(
            WorkerException.Reason.PROTOCOL,
            "Stream ended unexpectedly while reading frame header.");
      }
      headerBytesRead += count;
    }

    int length =
        ((header[0] & 0xFF) << 24)
            | ((header[1] & 0xFF) << 16)
            | ((header[2] & 0xFF) << 8)
            | (header[3] & 0xFF);

    if (length < 0 || length > maxPayloadSize) {
      throw new WorkerException(
          WorkerException.Reason.PROTOCOL,
          "Invalid or oversized payload length received: "
              + length
              + " (Max allowed: "
              + maxPayloadSize
              + ")");
    }

    byte[] payload = new byte[length];

    int payloadBytesRead = 0;
    while (payloadBytesRead < length) {
      int count = stream.read(payload, payloadBytesRead, length - payloadBytesRead);
      if (count == -1) {
        throw new WorkerException(
            WorkerException.Reason.PROTOCOL,
            "Stream ended unexpectedly while reading payload. Expected "
                + length
                + " bytes, but got "
                + payloadBytesRead
                + ".");
      }
      payloadBytesRead += count;
    }

    return payload;
  }
}
