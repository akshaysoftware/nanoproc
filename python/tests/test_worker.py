import io

import pytest
from nanoproc import DEFAULT_MAX_FRAME_BYTES, ProtocolError, serve


def unexpected_handler(_):
    pytest.fail("handler must not be called")


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


def test_default_limit_matches_java():
    assert DEFAULT_MAX_FRAME_BYTES == 1024 * 1024


@pytest.mark.parametrize(
    ("payload", "frame"),
    [
        (b"", b"\x00\x00\x00\x00"),
        (b"\x01\x80\xff", b"\x00\x00\x00\x03\x01\x80\xff"),
        (b"x" * 258, b"\x00\x00\x01\x02" + b"x" * 258),
    ],
    ids=["empty", "binary", "big-endian-258"],
)
def test_independent_wire_vectors(payload, frame):
    received = []
    output = io.BytesIO()

    def handler(request):
        received.append(request)
        return request

    serve(handler, input_stream=io.BytesIO(frame), output_stream=output)
    assert received == [payload]
    assert output.getvalue() == frame


def test_multiple_requests_preserve_handler_state():
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
    assert requests == [b"a", b"", b"b"]
    assert output.getvalue() == b"\0\0\0\x01\x01\0\0\0\x01\x02\0\0\0\x01\x03"
    assert output.flushes == 3


def test_fragmented_reads_and_short_writes():
    frame = b"\0\0\0\x05\0\n\r\x80\xff"
    output = ShortOutput()
    serve(lambda data: data, input_stream=FragmentedInput(frame), output_stream=output)
    assert output.getvalue() == frame
    assert output.flushes == 1


def test_clean_eof_does_not_call_handler_or_close_streams():
    source = io.BytesIO()
    output = ShortOutput()
    serve(unexpected_handler, input_stream=source, output_stream=output)
    assert output.getvalue() == b""
    assert output.flushes == 0
    assert not source.closed
    assert not output.closed


def test_response_can_be_empty():
    output = ShortOutput()
    serve(lambda _: b"", input_stream=io.BytesIO(b"\0\0\0\x01a"), output_stream=output)
    assert output.getvalue() == b"\0\0\0\0"
    assert output.flushes == 1


def test_exact_request_and_response_limits():
    frame = b"\0\0\0\x03abc"
    output = io.BytesIO()
    serve(
        lambda data: data,
        max_frame_bytes=3,
        input_stream=io.BytesIO(frame),
        output_stream=output,
    )
    assert output.getvalue() == frame


@pytest.mark.parametrize("frame", [b"\0", b"\0\0", b"\0\0\0"])
def test_truncated_headers_fail_without_calling_handler(frame):
    output = io.BytesIO()
    with pytest.raises(ProtocolError):
        serve(unexpected_handler, input_stream=io.BytesIO(frame), output_stream=output)
    assert output.getvalue() == b""


@pytest.mark.parametrize("payload", [b"", b"a", b"ab"])
def test_truncated_payloads_fail_without_calling_handler(payload):
    output = io.BytesIO()
    with pytest.raises(ProtocolError):
        serve(
            unexpected_handler,
            input_stream=io.BytesIO(b"\0\0\0\x03" + payload),
            output_stream=output,
        )
    assert output.getvalue() == b""


@pytest.mark.parametrize("header", [b"\x80\0\0\0", b"\xff\xff\xff\xff", b"\0\0\0\x04"])
def test_invalid_lengths_are_rejected_before_reading_payload(header):
    class HeaderOnly:
        def __init__(self):
            self.reads = 0

        def read(self, size):
            self.reads += 1
            assert self.reads == 1 and size == 4, "payload must not be read"
            return header

    output = io.BytesIO()
    with pytest.raises(ProtocolError):
        serve(
            unexpected_handler,
            max_frame_bytes=3,
            input_stream=HeaderOnly(),
            output_stream=output,
        )
    assert output.getvalue() == b""


@pytest.mark.parametrize(
    ("limit", "error"),
    [
        (0, ValueError),
        (-1, ValueError),
        (1 << 31, ValueError),
        (None, TypeError),
        (True, TypeError),
        (1.5, TypeError),
        ("1024", TypeError),
    ],
)
def test_invalid_limits_are_rejected_before_io(limit, error):
    with pytest.raises(error):
        serve(unexpected_handler, max_frame_bytes=limit)


def test_maximum_signed_limit_is_accepted_without_allocating_it():
    serve(
        unexpected_handler,
        max_frame_bytes=(1 << 31) - 1,
        input_stream=io.BytesIO(),
        output_stream=io.BytesIO(),
    )


def test_non_callable_handler_is_rejected_before_io():
    with pytest.raises(TypeError):
        serve(None)


@pytest.mark.parametrize(
    "response", [None, "text", bytearray(b"a"), memoryview(b"a"), 42]
)
def test_invalid_response_type_emits_no_frame(response):
    output = io.BytesIO()
    with pytest.raises(TypeError, match="handler must return bytes"):
        serve(
            lambda _: response,
            input_stream=io.BytesIO(b"\0\0\0\0"),
            output_stream=output,
        )
    assert output.getvalue() == b""


def test_oversized_response_emits_no_frame():
    output = io.BytesIO()
    with pytest.raises(ValueError, match="response exceeds"):
        serve(
            lambda _: b"ab",
            max_frame_bytes=1,
            input_stream=io.BytesIO(b"\0\0\0\0"),
            output_stream=output,
        )
    assert output.getvalue() == b""


def test_handler_failure_propagates_and_does_not_consume_next_request():
    failure = RuntimeError("handler failed")

    def handler(_):
        raise failure

    source = io.BytesIO(b"\0\0\0\0\0\0\0\0")
    output = io.BytesIO()
    with pytest.raises(RuntimeError) as raised:
        serve(handler, input_stream=source, output_stream=output)
    assert raised.value is failure
    assert source.tell() == 4
    assert output.getvalue() == b""
    assert not source.closed
    assert not output.closed


def test_protocol_failure_does_not_resynchronize():
    output = io.BytesIO()
    source = io.BytesIO(b"\0\0\0\0\x80\0\0\0\0\0\0\0")
    calls = []

    def handler(data):
        calls.append(data)
        return data

    with pytest.raises(ProtocolError):
        serve(handler, input_stream=source, output_stream=output)
    assert calls == [b""]
    assert source.tell() == 8
    assert output.getvalue() == b"\0\0\0\0"


def test_input_transport_failure_propagates():
    class BrokenInput:
        def read(self, _):
            raise OSError("broken input")

    with pytest.raises(OSError, match="broken input"):
        serve(
            unexpected_handler, input_stream=BrokenInput(), output_stream=io.BytesIO()
        )


def test_nonblocking_input_is_rejected():
    class NonblockingInput:
        def read(self, _):
            return None

    with pytest.raises(OSError, match="blocking binary"):
        serve(
            unexpected_handler,
            input_stream=NonblockingInput(),
            output_stream=io.BytesIO(),
        )


@pytest.mark.parametrize("result", [None, 0])
def test_output_with_no_progress_is_rejected(result):
    class StalledOutput:
        def write(self, _):
            return result

    with pytest.raises(OSError, match="no progress"):
        serve(
            lambda data: data,
            input_stream=io.BytesIO(b"\0\0\0\0"),
            output_stream=StalledOutput(),
        )


def test_output_transport_failure_propagates():
    class BrokenOutput:
        def write(self, _):
            raise BrokenPipeError("broken output")

    with pytest.raises(BrokenPipeError):
        serve(
            lambda data: data,
            input_stream=io.BytesIO(b"\0\0\0\0"),
            output_stream=BrokenOutput(),
        )


def test_flush_failure_propagates():
    class BrokenFlush(io.BytesIO):
        def flush(self):
            raise OSError("broken flush")

    with pytest.raises(OSError, match="broken flush"):
        serve(
            lambda data: data,
            input_stream=io.BytesIO(b"\0\0\0\0"),
            output_stream=BrokenFlush(),
        )
