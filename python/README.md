# Python SDK

**Status: planned. No installable Python package is provided yet.**

The first Python SDK will adapt a Python request handler to the
[raw-byte worker protocol](../spec/raw-byte-worker.md). Compatible Java and future
Node hosts should be able to use the same worker program.

The SDK will handle framing and the worker loop. Applications will supply their
Python environment, dependencies, and handler. Host-side Python APIs are outside
the initial milestone. See the [roadmap](../docs/roadmap.md).
