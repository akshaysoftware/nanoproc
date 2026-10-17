# TypeScript SDK

**Status: planned. No npm package is provided yet.**

This directory is reserved for a Node.js host SDK with an idiomatic asynchronous
API for managed local workers. It will implement the shared
[protocol contract](../spec/README.md) and reuse workers independently of their
implementation language.

This is server/desktop-backend functionality, not a browser subprocess API.
Support for other JavaScript runtimes will require explicit compatibility tests.
See the [roadmap](../docs/roadmap.md).
