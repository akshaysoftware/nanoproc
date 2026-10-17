import io
import unittest

from nanoproc import DEFAULT_MAX_FRAME_BYTES, ProtocolError, serve


class FragmentedInput(io.BytesIO):
    def read(self, size=-1):
        return super().read(min(size, 1))


class ShortOutput(io.BytesIO):
    def __init__(self):
        super().__init__()
        self.flushes = 0

    def write(self, data):
        return super().write(data[:1])

    def flush(self):
        self.flushes += 1
        super().flush()


class WorkerTests(unittest.TestCase):
    def test_default_limit_matches_java(self):
        self.assertEqual(1024 * 1024, DEFAULT_MAX_FRAME_BYTES)

    def test_independent_wire_vectors(self):
        for payload, frame in [
            (b"", b"\x00\x00\x00\x00"),
            (b"\x01\x80\xff", b"\x00\x00\x00\x03\x01\x80\xff"),
            (b"x" * 258, b"\x00\x00\x01\x02" + b"x" * 258),
        ]:
            with self.subTest(size=len(payload)):
                received = []
                output = io.BytesIO()

                def handler(request):
                    received.append(request)
                    return request

                serve(handler, input_stream=io.BytesIO(frame), output_stream=output)
                self.assertEqual([payload], received)
                self.assertEqual(frame, output.getvalue())

    def test_multiple_requests_preserve_handler_state(self):
        requests = []

        def handler(request):
            requests.append(request)
            return bytes([len(requests)])

        output = ShortOutput()
        serve(
            handler,
            input_stream=io.BytesIO(b"\0\0\0\x01a\0\0\0\0\0\0\0\x01b"),
            output_stream=output,
        )
        self.assertEqual([b"a", b"", b"b"], requests)
        self.assertEqual(
            b"\0\0\0\x01\x01\0\0\0\x01\x02\0\0\0\x01\x03", output.getvalue()
        )
        self.assertEqual(3, output.flushes)

    def test_fragmented_reads_and_short_writes(self):
        frame = b"\0\0\0\x05\0\n\r\x80\xff"
        output = ShortOutput()
        serve(
            lambda data: data, input_stream=FragmentedInput(frame), output_stream=output
        )
        self.assertEqual(frame, output.getvalue())
        self.assertEqual(1, output.flushes)

    def test_clean_eof_does_not_call_handler_or_close_streams(self):
        source = io.BytesIO()
        output = ShortOutput()
        serve(self.fail, input_stream=source, output_stream=output)
        self.assertEqual(b"", output.getvalue())
        self.assertEqual(0, output.flushes)
        self.assertFalse(source.closed)
        self.assertFalse(output.closed)

    def test_response_can_be_empty(self):
        output = ShortOutput()
        serve(
            lambda _: b"", input_stream=io.BytesIO(b"\0\0\0\x01a"), output_stream=output
        )
        self.assertEqual(b"\0\0\0\0", output.getvalue())
        self.assertEqual(1, output.flushes)

    def test_exact_request_and_response_limits(self):
        frame = b"\0\0\0\x03abc"
        output = io.BytesIO()
        serve(
            lambda data: data,
            max_frame_bytes=3,
            input_stream=io.BytesIO(frame),
            output_stream=output,
        )
        self.assertEqual(frame, output.getvalue())

    def test_truncated_headers_fail_without_calling_handler(self):
        for frame in [b"\0", b"\0\0", b"\0\0\0"]:
            with self.subTest(frame=frame):
                output = io.BytesIO()
                with self.assertRaises(ProtocolError):
                    serve(
                        self.fail, input_stream=io.BytesIO(frame), output_stream=output
                    )
                self.assertEqual(b"", output.getvalue())

    def test_truncated_payloads_fail_without_calling_handler(self):
        for payload in [b"", b"a", b"ab"]:
            with self.subTest(payload=payload):
                output = io.BytesIO()
                with self.assertRaises(ProtocolError):
                    serve(
                        self.fail,
                        input_stream=io.BytesIO(b"\0\0\0\x03" + payload),
                        output_stream=output,
                    )
                self.assertEqual(b"", output.getvalue())

    def test_invalid_lengths_are_rejected_before_reading_payload(self):
        class HeaderOnly:
            def __init__(self, header):
                self.header = header
                self.reads = 0

            def read(self, size):
                self.reads += 1
                if self.reads != 1 or size != 4:
                    raise AssertionError("payload must not be read")
                return self.header

        for header in [b"\x80\0\0\0", b"\xff\xff\xff\xff", b"\0\0\0\x04"]:
            with self.subTest(header=header):
                output = io.BytesIO()
                with self.assertRaises(ProtocolError):
                    serve(
                        self.fail,
                        max_frame_bytes=3,
                        input_stream=HeaderOnly(header),
                        output_stream=output,
                    )
                self.assertEqual(b"", output.getvalue())

    def test_invalid_limits_are_rejected_before_io(self):
        for limit in [0, -1, 1 << 31]:
            with self.subTest(limit=limit), self.assertRaises(ValueError):
                serve(self.fail, max_frame_bytes=limit)
        for limit in [None, True, 1.5, "1024"]:
            with self.subTest(limit=limit), self.assertRaises(TypeError):
                serve(self.fail, max_frame_bytes=limit)

    def test_maximum_signed_limit_is_accepted_without_allocating_it(self):
        serve(
            self.fail,
            max_frame_bytes=(1 << 31) - 1,
            input_stream=io.BytesIO(),
            output_stream=io.BytesIO(),
        )

    def test_non_callable_handler_is_rejected_before_io(self):
        with self.assertRaises(TypeError):
            serve(None)

    def test_invalid_response_type_emits_no_frame(self):
        for response in [None, "text", bytearray(b"a"), memoryview(b"a"), 42]:
            with self.subTest(response=response):
                output = io.BytesIO()
                with self.assertRaisesRegex(TypeError, "handler must return bytes"):
                    serve(
                        lambda _: response,
                        input_stream=io.BytesIO(b"\0\0\0\0"),
                        output_stream=output,
                    )
                self.assertEqual(b"", output.getvalue())

    def test_oversized_response_emits_no_frame(self):
        output = io.BytesIO()
        with self.assertRaisesRegex(ValueError, "response exceeds"):
            serve(
                lambda _: b"ab",
                max_frame_bytes=1,
                input_stream=io.BytesIO(b"\0\0\0\0"),
                output_stream=output,
            )
        self.assertEqual(b"", output.getvalue())

    def test_handler_failure_propagates_and_does_not_consume_next_request(self):
        failure = RuntimeError("handler failed")

        def handler(_):
            raise failure

        source = io.BytesIO(b"\0\0\0\0\0\0\0\0")
        output = io.BytesIO()
        with self.assertRaises(RuntimeError) as raised:
            serve(handler, input_stream=source, output_stream=output)
        self.assertIs(failure, raised.exception)
        self.assertEqual(4, source.tell())
        self.assertEqual(b"", output.getvalue())
        self.assertFalse(source.closed)
        self.assertFalse(output.closed)

    def test_protocol_failure_does_not_resynchronize(self):
        output = io.BytesIO()
        source = io.BytesIO(b"\0\0\0\0\x80\0\0\0\0\0\0\0")
        calls = []

        def handler(data):
            calls.append(data)
            return data

        with self.assertRaises(ProtocolError):
            serve(handler, input_stream=source, output_stream=output)
        self.assertEqual([b""], calls)
        self.assertEqual(8, source.tell())
        self.assertEqual(b"\0\0\0\0", output.getvalue())

    def test_input_transport_failure_propagates(self):
        class BrokenInput:
            def read(self, _):
                raise OSError("broken input")

        with self.assertRaisesRegex(OSError, "broken input"):
            serve(self.fail, input_stream=BrokenInput(), output_stream=io.BytesIO())

    def test_nonblocking_input_is_rejected(self):
        class NonblockingInput:
            def read(self, _):
                return None

        with self.assertRaisesRegex(OSError, "blocking binary"):
            serve(
                self.fail, input_stream=NonblockingInput(), output_stream=io.BytesIO()
            )

    def test_output_with_no_progress_is_rejected(self):
        class StalledOutput:
            def __init__(self, result):
                self.result = result

            def write(self, _):
                return self.result

        for result in [None, 0]:
            with (
                self.subTest(result=result),
                self.assertRaisesRegex(OSError, "no progress"),
            ):
                serve(
                    lambda data: data,
                    input_stream=io.BytesIO(b"\0\0\0\0"),
                    output_stream=StalledOutput(result),
                )

    def test_output_transport_failure_propagates(self):
        class BrokenOutput:
            def write(self, _):
                raise BrokenPipeError("broken output")

        with self.assertRaises(BrokenPipeError):
            serve(
                lambda data: data,
                input_stream=io.BytesIO(b"\0\0\0\0"),
                output_stream=BrokenOutput(),
            )

    def test_flush_failure_propagates(self):
        class BrokenFlush(io.BytesIO):
            def flush(self):
                raise OSError("broken flush")

        with self.assertRaisesRegex(OSError, "broken flush"):
            serve(
                lambda data: data,
                input_stream=io.BytesIO(b"\0\0\0\0"),
                output_stream=BrokenFlush(),
            )


if __name__ == "__main__":
    unittest.main()
