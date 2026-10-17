package io.github.akshaysoftware.nanoproc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class CaptureTest {
  @Test
  void exactLimitIsAllowed() throws Exception {
    var capture = new Capture(3);
    assertFalse(capture.drain(new ByteArrayInputStream(new byte[] {1, 2, 3})));
    assertArrayEquals(new byte[] {1, 2, 3}, capture.snapshot());
  }

  @Test
  void overflowRetainsOnlyThePrefix() throws Exception {
    var capture = new Capture(2);
    assertTrue(capture.drain(new ByteArrayInputStream(new byte[] {1, 2, 3, 4})));
    assertArrayEquals(new byte[] {1, 2}, capture.snapshot());
    capture.snapshot()[0] = 99;
    assertEquals(1, capture.snapshot()[0]);
  }

  @Test
  void zeroLimitDistinguishesEofFromOutput() throws Exception {
    assertFalse(new Capture(0).drain(new ByteArrayInputStream(new byte[0])));
    var capture = new Capture(0);
    assertTrue(capture.drain(new ByteArrayInputStream(new byte[] {1})));
    assertEquals(0, capture.snapshot().length);
  }

  @Test
  void ioFailureIsPropagated() {
    var stream =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("fixture");
          }
        };
    assertThrows(IOException.class, () -> new Capture(2).drain(stream));
  }
}
