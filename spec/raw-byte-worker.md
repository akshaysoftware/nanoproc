# Raw-byte worker protocol

**Draft: describes the existing wire format and the target lifecycle contract.
Worker lifecycle guarantees are not fully implemented; see
[current limitations](../docs/design.md#current-worker-limitations).**

## Streams and ownership

The host launches and owns a persistent worker process. Requests use the child's
stdin; responses use its stdout. Stderr carries diagnostic bytes and is drained
independently. Stdout is reserved exclusively for protocol frames: log messages
there corrupt the exchange.

The host supplies the executable, argument vector, environment overrides, and
working directory. There is no implicit shell, listening socket, or daemon.
Each side must explicitly arrange its runtime and application dependencies.

## Frame layout

A frame consists of a four-byte big-endian length followed by exactly that many
payload bytes. The length counts the payload only, excluding the header.

| Field | Size | Meaning |
| --- | --- | --- |
| Length | 4 bytes | Nonnegative signed 32-bit value in big-endian order |
| Payload | Length bytes | Opaque bytes; encoding belongs to the application |

The valid length range is 0 through 2,147,483,647, additionally bounded by the
receiver's configured maximum. A high-bit-set header is invalid. Validate the
length before allocating payload storage. There is no size negotiation; both
programs must be configured with compatible limits.

Examples:

| Payload | Complete frame in hexadecimal |
| --- | --- |
| Empty | `00 00 00 00` |
| Bytes `01 80 FF` | `00 00 00 03 01 80 FF` |

A length of 258 has header `00 00 01 02`. Empty payloads are valid. Payload bytes
may contain NUL, newlines, and arbitrary binary values.

Reads may return less than the requested amount. Implementations must read the
entire header and payload and consume no bytes belonging to the next frame.
Writers send header and payload in order and flush after each frame.

## Exchanges

The host sends one request frame and the worker sends exactly one response frame.
There is one active exchange per worker, no pipelining, and no unsolicited frames
or startup greeting. This profile does not define callbacks or streaming replies.

The host validates the outgoing size before writing. It rejects another active
call immediately rather than implicitly creating an unbounded request queue.

## Failure and shutdown contract

The target host contract is:

- One call budget covers writing, flushing, and reading the response.
- Invalid framing, an incomplete response, transport failure, or an interrupted
  or timed-out active exchange makes the worker unavailable for further calls.
- A caller interrupted before admission sends no request.
- Explicit close stops admission, aborts an active exchange, and performs bounded
  cleanup. A late response cannot reopen a closed worker.
- A cooperative idle worker may exit when stdin reaches EOF between requests.
  EOF while the host expects a response is a failed exchange.
- Diagnostics are retained as a bounded byte suffix and remain accessible after
  shutdown. The retention limit does not permit stopping stderr drainage.

Timeout bounds the exchange, not the time spent creating the OS process. Cleanup
may add its own bounded waits. Cancellation or timeout cannot undo side effects;
requests must not be replayed automatically. Terminating descendants remains
best effort, not an operating-system isolation guarantee.

These lifecycle requirements are the hardening target for Java and future host
SDKs. They are not a claim that the current experimental worker passes all of
them. Expected application errors are application-defined bytes in this profile;
a future structured profile may standardize error envelopes.
