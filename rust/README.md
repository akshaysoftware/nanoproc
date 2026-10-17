# Rust SDK

**Status: planned. No Rust crate is provided yet.**

This directory is reserved for helpers that expose native Rust computation as a
[compatible worker](../spec/README.md). A native executable will use the same
framing as other worker languages; it will not require a host-specific binding.

A Rust host SDK and C/C++ helpers may follow demonstrated demand. Existing Java
code remains an independent implementation, not a wrapper around a Rust core.
See the [roadmap](../docs/roadmap.md).
