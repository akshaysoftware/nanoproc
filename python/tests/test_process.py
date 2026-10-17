import os
import queue
import subprocess
import sys
import threading
import unittest
from pathlib import Path

ECHO_WORKER = (
    Path(__file__).resolve().parents[2] / "examples" / "python" / "echo_worker.py"
)


class ProcessTests(unittest.TestCase):
    def run_echo(self, data):
        return subprocess.run(
            [sys.executable, str(ECHO_WORKER)],
            input=data,
            capture_output=True,
            timeout=10,
            check=False,
        )

    def test_real_stdio_binary_round_trip_and_clean_eof(self):
        binary = bytes(range(256)) + b"\r\n"
        frames = b"\0\0\0\0\0\0\x01\x02" + binary
        result = self.run_echo(frames)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(frames, result.stdout)
        self.assertEqual(
            b"echoing 0 bytes\nechoing 258 bytes\n",
            result.stderr.replace(b"\r\n", b"\n"),
        )

    def test_empty_stdin_exits_without_a_response(self):
        result = self.run_echo(b"")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(b"", result.stdout)
        self.assertEqual(b"", result.stderr)

    def test_truncated_frame_exits_nonzero_without_a_response(self):
        result = self.run_echo(b"\0\0\0\x03a")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(b"", result.stdout)
        self.assertIn(b"ProtocolError", result.stderr)

    def test_invalid_length_exits_nonzero_without_a_response(self):
        result = self.run_echo(b"\x80\0\0\0")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(b"", result.stdout)
        self.assertIn(b"ProtocolError", result.stderr)

    def test_handler_exception_exits_nonzero_without_an_error_frame(self):
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
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(b"", result.stdout)
        self.assertIn(b"RuntimeError: application failed", result.stderr)

    def test_response_is_flushed_before_eof_and_worker_remains_alive(self):
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
                self.assertEqual(frame, responses.get(timeout=10))
                self.assertIsNone(process.poll())
            process.stdin.close()
            self.assertEqual(0, process.wait(timeout=10))
        finally:
            if process.poll() is None:
                process.kill()
            process.wait(timeout=10)
            reader.join(timeout=10)
            process.stdin.close()
            process.stdout.close()
        self.assertFalse(reader.is_alive())


if __name__ == "__main__":
    unittest.main()
