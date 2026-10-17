# Conformance

**Status: scaffolding. A shared conformance runner is not implemented yet.**

This directory will hold language-independent fixtures and black-box tests for
the [worker protocol](../spec/README.md). The `fixtures/` directory is reserved
for independently constructed wire data; no SDK is certified by this scaffold.

Coverage should include valid and fragmented frames, empty and binary payloads,
exact and oversized limits, truncated headers/payloads, blocked writes, missing
responses, stderr flooding, exit, interruption, and close races. Lifecycle tests
must use explicit readiness signals and bounded fallback process cleanup.

The current Java tests live in [java/src/test/java](../java/src/test/java), and
Python framing and subprocess tests in [python/tests](../python/tests). CI runs
the [Java-to-Python example](../examples/python/README.md) as a smoke test. These
checks do not replace the planned shared conformance runner or certify host
lifecycle guarantees. Protocol/profile support is independent of SDK versions.
