# Nanoproc Python worker

Adapt a synchronous Python function to the Nanoproc raw-byte worker protocol.
Keep models, datasets, or other application state loaded between calls while
the host owns the process lifecycle.

**Pre-release; install from this repository. Not published to PyPI.** Requires
Python 3.11+ and has no runtime dependencies. The local distribution is named
`nanoproc-worker`; the import is `nanoproc`. This is a worker helper, not a Python
host SDK.

## Install

From the repository root, create an environment and install the package:

```sh
python -m venv .venv
.venv/bin/python -m pip install -e ./python
```

On Windows PowerShell use `.venv\Scripts\python.exe` instead of
`.venv/bin/python`. Use this same interpreter when the host launches the worker;
the host does not activate environments or install dependencies.

## Define a worker

```python
import sys

from nanoproc import serve


def handle(request: bytes) -> bytes:
    print(f"received {len(request)} bytes", file=sys.stderr, flush=True)
    return request


if __name__ == "__main__":
    serve(handle)
```

Call `serve` once, after application initialization. It reads a request, invokes
the handler, writes and flushes one response, and then reads the next request.
The same handler and its state are reused sequentially. Requests and responses
are opaque `bytes`; empty and arbitrary binary payloads are valid. Encode JSON
or other application formats explicitly if needed.

Run the [Java-to-Python example](../examples/python/README.md) for a complete
host/worker exchange. Starting a worker in a terminal waits for binary frames;
this is not a line-oriented command-line interface.

## Contract

`serve(handler, *, max_frame_bytes=1048576, input_stream=None, output_stream=None)`
returns `None` when stdin reaches EOF between frames.

- Frames use a four-byte big-endian nonnegative signed 32-bit payload length.
- Both incoming and outgoing payloads are bounded by `max_frame_bytes`, default
  1 MiB. Configure an integer from 1 through 2,147,483,647. Match the host's
  limit: the protocol does not negotiate sizes. The SDK cannot stop application
  code allocating an oversized response before returning it.
- Fragmented reads and short writes are handled. The handler is invoked only
  after the entire request has been read and validated.
- EOF inside a header or payload, an invalid signed length, or an oversized
  request raises `ProtocolError`. A non-`bytes` response raises `TypeError`; an
  oversized response raises `ValueError`. Neither invalid response emits a frame.
- Handler exceptions and transport errors propagate. Leave them uncaught at
  the entry point so the process exits nonzero with diagnostics on stderr.
  Do not resume or restart the loop on the same streams after a failure.
- Expected application errors must be encoded as application response bytes.
  There is no built-in error envelope, retry, handshake, or cancellation message.
- The host owns deadlines, termination, and bounded stderr retention. This loop
  cannot interrupt a hanging handler or undo its side effects.

**Stdout is reserved for frames.** Send logging, `print`, progress bars, and
third-party diagnostics to stderr. The SDK does not intercept stdout writes.
Do not merge stderr into stdout when launching the process.

The optional stream arguments accept blocking binary streams and default to
`sys.stdin.buffer` and `sys.stdout.buffer`. The caller owns these streams;
`serve` never closes them. A response is flushed after each successful handler
call, so Python's `-u` flag is not required for protocol correctness.

See the [draft protocol](../spec/raw-byte-worker.md) and the Java host's
[current lifecycle limitations](../docs/design.md#current-worker-limitations).
This helper does not change those host guarantees.

## Test

From the repository root after installation:

```sh
.venv/bin/python -W error -m unittest discover -s python/tests -v
```

Tests exercise framing, limits, handler failures, stream failures, and actual
subprocess stdio. CI checks Python 3.11 and 3.14 on Linux, macOS, and Windows.

The package uses the repository's Apache-2.0 license. A copy is included in
Python distributions as `python/LICENSE`.
