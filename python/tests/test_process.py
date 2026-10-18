import os
import queue
import subprocess
import sys
import threading
from pathlib import Path

import pytest

ECHO_WORKER = (
    Path(__file__).resolve().parents[2] / "examples" / "python" / "echo_worker.py"
)


@pytest.fixture
def run_echo():
    def run(data):
        return subprocess.run(
            [sys.executable, str(ECHO_WORKER)],
            input=data,
            capture_output=True,
            timeout=10,
            check=False,
        )

    return run


def test_real_stdio_binary_round_trip_and_clean_eof(run_echo):
    binary = bytes(range(256)) + b"\r\n"
    frames = b"\0\0\0\0\0\0\x01\x02" + binary
    result = run_echo(frames)
    assert result.returncode == 0, result.stderr
    assert result.stdout == frames
    assert (
        result.stderr.replace(b"\r\n", b"\n") == b"echoing 0 bytes\nechoing 258 bytes\n"
    )


def test_empty_stdin_exits_without_a_response(run_echo):
    result = run_echo(b"")
    assert result.returncode == 0, result.stderr
    assert result.stdout == b""
    assert result.stderr == b""


@pytest.mark.parametrize(
    "frame",
    [b"\0\0\0\x03a", b"\x80\0\0\0"],
    ids=["truncated", "invalid-length"],
)
def test_protocol_failure_exits_nonzero_without_a_response(run_echo, frame):
    result = run_echo(frame)
    assert result.returncode != 0
    assert result.stdout == b""
    assert b"ProtocolError" in result.stderr


def test_handler_exception_exits_nonzero_without_an_error_frame():
    program = (
        "from nanoproc import serve\n"
        "def fail(request):\n"
        "    raise RuntimeError('application failed')\n"
        "serve(fail)\n"
    )
    result = subprocess.run(
        [sys.executable, "-c", program],
        input=b"\0\0\0\0",
        capture_output=True,
        timeout=10,
        check=False,
    )
    assert result.returncode != 0
    assert result.stdout == b""
    assert b"RuntimeError: application failed" in result.stderr


def test_response_is_flushed_before_eof_and_worker_remains_alive():
    # Do not use -u: serve itself must flush while the input pipe stays open.
    process = subprocess.Popen(
        [sys.executable, str(ECHO_WORKER)],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )
    responses = queue.Queue()

    def read_responses():
        try:
            for _ in range(2):
                data = bytearray()
                while len(data) < 5:
                    chunk = os.read(process.stdout.fileno(), 5 - len(data))
                    if not chunk:
                        raise EOFError("worker exited before responding")
                    data.extend(chunk)
                responses.put(bytes(data))
        except Exception as error:
            responses.put(error)

    reader = threading.Thread(target=read_responses, daemon=True)
    reader.start()
    try:
        for payload in [b"a", b"b"]:
            frame = b"\0\0\0\x01" + payload
            process.stdin.write(frame)
            process.stdin.flush()
            response = responses.get(timeout=10)
            if isinstance(response, Exception):
                raise response
            assert response == frame
            assert process.poll() is None
        process.stdin.close()
        assert process.wait(timeout=10) == 0
    finally:
        if process.poll() is None:
            process.kill()
        process.wait(timeout=10)
        reader.join(timeout=10)
        process.stdin.close()
        process.stdout.close()
    assert not reader.is_alive()
