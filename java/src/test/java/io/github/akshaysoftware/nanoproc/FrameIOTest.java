package io.github.akshaysoftware.nanoproc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class FrameIOTest {

  @Test
  void testRoundTrip() throws IOException {
    byte[] original = "hello".getBytes();
    int maxPayloadSize = 1024;

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    FrameIO.writeFrame(out, original, maxPayloadSize);

    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
    byte[] read = FrameIO.readFrame(in, maxPayloadSize);

    assertArrayEquals(original, read);
  }

  @Test
  void testEmptyPayload() throws IOException {
    byte[] original = new byte[0];
    int maxPayloadSize = 1024;

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    FrameIO.writeFrame(out, original, maxPayloadSize);

    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
    byte[] read = FrameIO.readFrame(in, maxPayloadSize);

    assertEquals(0, read.length);
  }

  @Test
  void testOversizedOutgoingPayload() {
    byte[] payload = new byte[10];
    int maxPayloadSize = 5;

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          FrameIO.writeFrame(out, payload, maxPayloadSize);
        });
  }

  @Test
  void testOversizedIncomingLength() {
    // Header specifies a length of 10, but maxPayloadSize is 5
    byte[] header = new byte[] {0, 0, 0, 10};
    ByteArrayInputStream in = new ByteArrayInputStream(header);

    WorkerException ex =
        assertThrows(
            WorkerException.class,
            () -> {
              FrameIO.readFrame(in, 5);
            });
    assertEquals(WorkerException.Reason.PROTOCOL, ex.getReason());
  }

  @Test
  void testNegativeIncomingLength() {
    // Header with highest bits set (represents a negative integer value)
    byte[] header = new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    ByteArrayInputStream in = new ByteArrayInputStream(header);

    WorkerException ex =
        assertThrows(
            WorkerException.class,
            () -> {
              FrameIO.readFrame(in, 1024);
            });
    assertEquals(WorkerException.Reason.PROTOCOL, ex.getReason());
  }

  @Test
  void testUnexpectedEofHeader() {
    // Stream ends mid-header (only 2 bytes instead of 4)
    byte[] header = new byte[] {0, 0};
    ByteArrayInputStream in = new ByteArrayInputStream(header);

    WorkerException ex =
        assertThrows(
            WorkerException.class,
            () -> {
              FrameIO.readFrame(in, 1024);
            });
    assertEquals(WorkerException.Reason.PROTOCOL, ex.getReason());
  }

  @Test
  void testUnexpectedEofPayload() {
    // Header says 5 bytes, but payload only provides 2 bytes
    byte[] data = new byte[] {0, 0, 0, 5, 'h', 'e'};
    ByteArrayInputStream in = new ByteArrayInputStream(data);

    WorkerException ex =
        assertThrows(
            WorkerException.class,
            () -> {
              FrameIO.readFrame(in, 1024);
            });
    assertEquals(WorkerException.Reason.PROTOCOL, ex.getReason());
  }

  @Test
  void testPartialReadsHandling() throws IOException {
    byte[] original = "fragmented-test".getBytes();
    int maxPayloadSize = 1024;

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    FrameIO.writeFrame(out, original, maxPayloadSize);
    byte[] frameBytes = out.toByteArray();

    // Custom InputStream that forces partial reads by returning only 1 byte at a time
    InputStream slowStream =
        new InputStream() {
          private int index = 0;

          @Override
          public int read() {
            if (index >= frameBytes.length) {
              return -1;
            }
            return frameBytes[index++] & 0xFF;
          }
        };

    byte[] read = FrameIO.readFrame(slowStream, maxPayloadSize);
    assertArrayEquals(original, read);
  }
}
