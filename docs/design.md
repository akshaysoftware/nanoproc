# Design

Nanoproc manages local process boundaries. A host owns launch, requests,
diagnostics, and cleanup; a worker owns its application state and computation.
No daemon, network service, embedded runtime, or shared native core is required.
Independent SDKs will implement the [draft protocol](../spec/README.md).

## Java implementation

| Component | Responsibility |
| --- | --- |
| `Nanoproc` | Public factories for commands and worker builders |
| `Command` | Immutable one-shot configuration and owned stdin bytes |
| `ProcessRunner` | Concurrent input/output/exit coordination and outcomes |
| `ProcessCleanup` | Best-effort one-shot process-tree termination and bounded waits |
| `Capture` | Bounded stdout/stderr prefixes and overflow detection |
| `ProcessResult` | Outcome, optional exit code, and copied output arrays |
| `Worker` | Persistent process, call admission, exchange deadlines, and diagnostics |
| `FrameIO` | Four-byte big-endian framing with validation before allocation |
| `StderrTail` | Synchronized fixed-capacity diagnostic suffix |

Implementation and tests are in [java/src/](../java/src/). The root Maven build
keeps the existing artifact coordinates and `target/` paths.

## One-shot commands

Arguments are passed directly to `ProcessBuilder`. Configuration copies incoming
arrays and maps. Four virtual-thread tasks write stdin, drain stdout, drain
stderr, and observe exit. The coordinator waits for execution and I/O completion,
subject to a deadline measured after process startup.

Captured output is a prefix. Reading one byte beyond the allowance detects
overflow without retaining that byte. Nonzero exit, timeout, and stdout/stderr
overflow are result outcomes; startup and transport failures are exceptions.

Cleanup retains observed descendant handles because reparenting can hide them
after root exit. It requests normal then forceful termination and bounds its
waits. Stream closure runs separately because it can contend with blocked I/O.
Descendants can escape observation; process-tree termination is best effort.

## Persistent workers

`Worker.Builder` configures a fresh process. One active `call(byte[])` writes a
request and reads a response on a virtual thread while the caller waits within
one budget. A separate reader continuously appends stderr to a fixed-size ring.
Stdout belongs exclusively to the framing reader.

The observable states are `OPEN`, `BUSY`, `BROKEN`, and `CLOSED`. A failed active
exchange prevents reuse because stream alignment and request completion may be
unknown. Diagnostic snapshots remain accessible after shutdown. SDKs do not
automatically replay requests or reconstruct lost application state.

### Current worker limitations

The worker is experimental and is not ready for a stable lifecycle guarantee.
Hardening is required for termination during blocked I/O, active-task ownership,
interruption preservation in close, completion/close ordering, graceful EOF
shutdown, and descendant cleanup. Closing a worker or receiving a timeout is
currently not proof that its child or descendants have stopped.

Keep call durations within the one-shot API's supported positive, at-most-one-day
range; worker timeout validation still needs to enforce that bound. Process exit
also needs coordination with buffered responses and final diagnostic drainage.
The repository layout and protocol draft do not resolve these runtime issues.

## Compatibility and scope

The raw payload is opaque. JSON, typed errors, negotiation, callbacks, and
streaming are not part of the current profile. Future profiles must be explicit
so existing raw workers do not receive unexpected control frames.

A supported SDK release will identify its protocol/profile and run against shared
fixtures and cross-language smoke tests. SDK package versions need not match.
The planned SDK directories and conformance scaffold do not certify compatibility.

Processes retain the permissions of their environment. Nanoproc is not a sandbox
or a CPU, GPU, child-memory, or filesystem resource limiter. Applications manage
runtime installation, dependencies, deployment, and side-effect recovery.
