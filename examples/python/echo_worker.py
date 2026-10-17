"""Echo opaque byte requests; keep stdout exclusively for protocol frames."""

import sys

from nanoproc import serve


def echo(request: bytes) -> bytes:
    print(f"echoing {len(request)} bytes", file=sys.stderr, flush=True)
    return request


if __name__ == "__main__":
    serve(echo)
