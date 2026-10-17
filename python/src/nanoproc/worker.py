"""A blocking, single-handler worker loop over binary streams."""

import sys
from collections.abc import Callable
from typing import BinaryIO

DEFAULT_MAX_FRAME_BYTES = 1024 * 1024
_MAX_WIRE_LENGTH = (1 << 31) - 1


class ProtocolError(Exception):
    """An incoming frame has an invalid length or ends prematurely."""


def _read_exact(
    stream: BinaryIO, size: int, *, at_frame_boundary: bool = False
) -> bytes | None:
    data = bytearray()
    while len(data) < size:
        chunk = stream.read(size - len(data))
        if chunk is None:
            raise OSError("serve requires a blocking binary input stream")
        if not chunk:
            if at_frame_boundary and not data:
                return None
            raise ProtocolError("unexpected EOF inside a frame")
        data.extend(chunk)
    return bytes(data)


def _write_all(stream: BinaryIO, data: bytes) -> None:
    remaining = memoryview(data)
    while remaining:
        written = stream.write(remaining)
        if written is None or written <= 0:
            raise OSError("output stream made no progress writing a frame")
        remaining = remaining[written:]


def serve(
    handler: Callable[[bytes], bytes],
    *,
    max_frame_bytes: int = DEFAULT_MAX_FRAME_BYTES,
    input_stream: BinaryIO | None = None,
    output_stream: BinaryIO | None = None,
) -> None:
    """Serve sequential byte requests until EOF between frames.

    By default, use sys.stdin.buffer and sys.stdout.buffer. Streams must be
    blocking binary streams; they remain owned by the caller and are not closed.
    Each request and response is capped by max_frame_bytes (default: 1 MiB).

    The handler must return bytes. Stdout is reserved for frames: send all
    diagnostics, including output from third-party libraries, to stderr.
    Handler, protocol, and transport exceptions propagate without an error
    frame. Let them terminate the worker; do not restart this loop on the same
    streams after failure. Application error payloads are application-defined.

    There is no timeout or concurrency scheduler here. The host owns call
    deadlines, process lifetime, and bounded stderr retention.
    """
    if not callable(handler):
        raise TypeError("handler must be callable")
    if type(max_frame_bytes) is not int:
        raise TypeError("max_frame_bytes must be an integer")
    if not 1 <= max_frame_bytes <= _MAX_WIRE_LENGTH:
        raise ValueError("max_frame_bytes must be between 1 and 2147483647")

    source = sys.stdin.buffer if input_stream is None else input_stream
    sink = sys.stdout.buffer if output_stream is None else output_stream

    while True:
        header = _read_exact(source, 4, at_frame_boundary=True)
        if header is None:
            return
        length = int.from_bytes(header, "big")
        if length > _MAX_WIRE_LENGTH or length > max_frame_bytes:
            raise ProtocolError(f"request length {length} exceeds max_frame_bytes")

        request = _read_exact(source, length)
        # Only a header read at a frame boundary can return None.
        assert request is not None
        response = handler(request)
        if not isinstance(response, bytes):
            raise TypeError("handler must return bytes")
        if len(response) > max_frame_bytes:
            raise ValueError("response exceeds max_frame_bytes")

        # Validate the entire response before emitting any part of its frame.
        _write_all(sink, len(response).to_bytes(4, "big"))
        _write_all(sink, response)
        sink.flush()
