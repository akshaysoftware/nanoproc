package io.github.akshaysoftware.nanoproc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

class StderrTailTest {

  @Test
  void negativeCapacityRejects() {
    assertThrows(IllegalArgumentException.class, () -> new StderrTail(-1));
  }

  @Test
  void emptyTailSnapshot() {
    StderrTail tail = new StderrTail(5);
    assertArrayEquals(new byte[0], tail.snapshot());
  }

  @Test
  void capacityZeroAlwaysEmpty() {
    StderrTail tail = new StderrTail(0);
    byte[] data = "hello".getBytes();
    tail.append(data, 0, data.length);
    assertArrayEquals(new byte[0], tail.snapshot());
  }

  @Test
  void capacityOneMostRecentByte() {
    StderrTail tail = new StderrTail(1);
    byte[] data = "abc".getBytes();
    tail.append(data, 0, data.length);
    assertArrayEquals("c".getBytes(), tail.snapshot());
  }

  @Test
  void appendBelowCapacity() {
    StderrTail tail = new StderrTail(5);
    byte[] data = "abc".getBytes();
    tail.append(data, 0, data.length);
    assertArrayEquals("abc".getBytes(), tail.snapshot());
  }

  @Test
  void exactlyFillCapacity() {
    StderrTail tail = new StderrTail(3);
    byte[] data = "abc".getBytes();
    tail.append(data, 0, data.length);
    assertArrayEquals("abc".getBytes(), tail.snapshot());
  }

  @Test
  void appendAcrossBoundary() {
    StderrTail tail = new StderrTail(3);
    tail.append("ab".getBytes(), 0, 2);
    tail.append("cd".getBytes(), 0, 2);
    assertArrayEquals("bcd".getBytes(), tail.snapshot());
  }

  @Test
  void oneChunkLargerThanCapacity() {
    StderrTail tail = new StderrTail(3);
    byte[] data = "abcdef".getBytes();
    tail.append(data, 0, data.length);
    assertArrayEquals("def".getBytes(), tail.snapshot());
  }

  @Test
  void oversizedChunkAfterPreviousAppends() {
    StderrTail tail = new StderrTail(3);
    tail.append("ab".getBytes(), 0, 2);
    byte[] data = "abcdefg".getBytes();
    tail.append(data, 0, data.length);
    assertArrayEquals("efg".getBytes(), tail.snapshot());
  }

  @Test
  void nonzeroSourceOffset() {
    StderrTail tail = new StderrTail(3);
    byte[] data = "xxabcdefxx".getBytes();
    tail.append(data, 2, 6);
    assertArrayEquals("def".getBytes(), tail.snapshot());
  }

  @Test
  void modifySourceAfterAppending() {
    StderrTail tail = new StderrTail(3);
    byte[] data = "abc".getBytes();
    tail.append(data, 0, data.length);
    data[0] = 'z';
    assertArrayEquals("abc".getBytes(), tail.snapshot());
  }

  @Test
  void modifySnapshotDoesNotAffectTail() {
    StderrTail tail = new StderrTail(3);
    tail.append("abc".getBytes(), 0, 3);
    byte[] snap = tail.snapshot();
    snap[0] = 'z';
    assertArrayEquals("abc".getBytes(), tail.snapshot());
  }

  @Test
  void appendAfterTakingSnapshot() {
    StderrTail tail = new StderrTail(3);
    tail.append("ab".getBytes(), 0, 2);
    byte[] snap1 = tail.snapshot();
    tail.append("cd".getBytes(), 0, 2);
    assertArrayEquals("ab".getBytes(), snap1);
    assertArrayEquals("bcd".getBytes(), tail.snapshot());
  }

  @Test
  void randomChunksAgainstReferenceBuffer() {
    int capacity = 17;
    StderrTail tail = new StderrTail(capacity);
    byte[] reference = new byte[0];
    Random rand = new Random(42);

    for (int i = 0; i < 1000; i++) {
      int len = rand.nextInt(25);
      byte[] chunk = new byte[len];
      rand.nextBytes(chunk);

      tail.append(chunk, 0, chunk.length);

      // Update reference buffer
      byte[] newRef = new byte[reference.length + chunk.length];
      System.arraycopy(reference, 0, newRef, 0, reference.length);
      System.arraycopy(chunk, 0, newRef, reference.length, chunk.length);
      reference = newRef;

      // Expected tail is the last `capacity` bytes of reference
      int expectedLen = Math.min(reference.length, capacity);
      byte[] expected = new byte[expectedLen];
      if (expectedLen > 0) {
        System.arraycopy(reference, reference.length - expectedLen, expected, 0, expectedLen);
      }

      assertArrayEquals(expected, tail.snapshot());
    }
  }
}
