# Java-to-Python worker

The Java host reuses one Python echo handler for three requests: text, an empty
payload, and 258 arbitrary binary bytes. The handler writes diagnostics to stderr
and echoes the request bytes unchanged over framed stdout.

Requirements: JDK 21+ and Python 3.11+. Use JDK 25 for repository development.
Run from the repository root:

```sh
python -m venv .venv
.venv/bin/python -m pip install -e ./python
./mvnw verify
java --class-path 'target/*' examples/java/CallPythonWorker.java .venv/bin/python
```

Windows PowerShell:

```powershell
python -m venv .venv
.venv\Scripts\python.exe -m pip install -e ./python
.\mvnw.cmd verify
java --class-path 'target/*' examples/java/CallPythonWorker.java .venv\Scripts\python.exe
```

The host takes the interpreter as its only argument and launches
`examples/python/echo_worker.py` directly, without a shell. Install the package
into that interpreter's environment first. No Python installation is required
for Java-only builds or the separate Java stdin/stdout example.

Successful output resembles `Completed 3 calls to Python worker 12345`.
The example fails if a response differs from its request. CI runs it on Linux,
macOS, and Windows using Java 25 and Python 3.14.

The worker profile and Java lifecycle are experimental. See the
[Python worker guide](../../python/README.md) and
[host limitations](../../docs/design.md#current-worker-limitations).
