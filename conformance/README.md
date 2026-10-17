# Conformance

**Status: scaffolding. A shared conformance runner is not implemented yet.**

This directory will hold language-independent fixtures and black-box tests for
the [worker protocol](../spec/README.md). The `fixtures/` directory is reserved
for independently constructed wire data; no SDK is certified by this scaffold.

Coverage should include valid and fragmented frames, empty and binary payloads,
exact and oversized limits, truncated headers/payloads, blocked writes, missing
responses, stderr flooding, exit, interruption, and close races. Lifecycle tests
must use explicit readiness signals and bounded fallback process cleanup.

The current Java tests live in [java/src/test/java](../java/src/test/java).
Cross-language smoke tests will be added when the Python worker and additional
host SDKs exist. Protocol/profile support is independent of SDK package versions.
