# Java-to-Python worker

The Java host reuses one Python echo handler for three requests: text, an empty
payload, and 258 arbitrary binary bytes. The handler writes diagnostics to stderr
and echoes the request bytes unchanged over framed stdout.

Requirements: JDK 21+ and [uv](https://docs.astral.sh/uv/getting-started/installation/)
0.12.23 or newer. The worker requires Python 3.11+; uv can install a compatible
interpreter. Use JDK 25 for repository development. Run from the repository root:

```sh
uv sync --project python --locked --no-dev
./mvnw verify
uv run --project python --locked --no-dev java --class-path 'target/*' examples/java/CallPythonWorker.java python
```

Windows PowerShell:

```powershell
uv sync --project python --locked --no-dev
.\mvnw.cmd verify
uv run --project python --locked --no-dev java --class-path 'target/*' examples/java/CallPythonWorker.java python
```

`uv sync` creates `python/.venv`, and `uv run` makes its interpreter available as
`python` to the Java program. The host takes the interpreter as its only argument
and launches `examples/python/echo_worker.py` directly, without a shell. No
Python installation is required for Java-only builds or the separate Java
stdin/stdout example.

Successful output resembles `Completed 3 calls to Python worker 12345`.
The example fails if a response differs from its request. CI runs it on Linux,
macOS, and Windows using Java 25 and Python 3.14.

The worker profile and Java lifecycle are experimental. See the
[Python worker guide](../../python/README.md) and
[host limitations](../../docs/design.md#current-worker-limitations).
