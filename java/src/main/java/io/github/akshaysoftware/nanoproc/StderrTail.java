package io.github.akshaysoftware.nanoproc;

class StderrTail {
  private final byte[] buffer;
  private final int capacity;
  private int writePosition;
  private int size;

  StderrTail(int capacity) {
    if (capacity < 0) {
      throw new IllegalArgumentException("Capacity cannot be negative");
    }
    this.capacity = capacity;
    this.buffer = new byte[capacity];
    this.writePosition = 0;
    this.size = 0;
  }

  synchronized void append(byte[] source, int offset, int length) {
    if (source == null) {
      throw new IllegalArgumentException("Source cannot be null");
    }
    if (offset < 0 || length < 0 || offset > source.length - length) {
      throw new IndexOutOfBoundsException("Invalid offset or length");
    }

    if (capacity == 0 || length == 0) {
      return;
    }

    if (length >= capacity) {
      System.arraycopy(source, offset + length - capacity, buffer, 0, capacity);
      size = capacity;
      writePosition = 0;
      return;
    }

    int firstCount = Math.min(length, capacity - writePosition);
    int remainingCount = length - firstCount;

    System.arraycopy(source, offset, buffer, writePosition, firstCount);
    if (remainingCount > 0) {
      System.arraycopy(source, offset + firstCount, buffer, 0, remainingCount);
    }

    if (remainingCount > 0) {
      writePosition = remainingCount;
    } else {
      writePosition = (writePosition + firstCount) % capacity;
    }

    size = size + Math.min(length, capacity - size);
  }

  synchronized byte[] snapshot() {
    if (size == 0) {
      return new byte[0];
    }

    byte[] result = new byte[size];
    int start = writePosition - size;
    if (start < 0) {
      start += capacity;
    }

    int firstCount = Math.min(size, capacity - start);
    int remainingCount = size - firstCount;

    System.arraycopy(buffer, start, result, 0, firstCount);
    if (remainingCount > 0) {
      System.arraycopy(buffer, 0, result, firstCount, remainingCount);
    }

    return result;
  }
}
