# Roadmap

Nanoproc is pre-release. Milestones describe intended scope, not shipped SDKs or
delivery dates. Stable releases will document supported runtimes and profiles.

## Complete the Java foundation

- Harden worker cleanup under blocked writes/reads and coordinate active tasks.
- Preserve interruption and define completion-versus-close ordering.
- Validate timeout bounds before process launch or exchange admission.
- Add graceful EOF shutdown, best-effort descendant handling, and final-pipe drainage.
- Expand worker lifecycle tests, including concurrent calls/close and exit races.
- Correct fragmented-read fixtures and test independent wire-format vectors.
- Complete public API documentation before stable publication.

## Establish cross-language compatibility

- Review and stabilize the raw-byte protocol draft.
- Implement shared framing fixtures and black-box lifecycle tests in `conformance/`.
- Add a minimal Python worker SDK and a real Java-to-Python example.
- Add a Node/TypeScript host and run the same Python worker unchanged.
- Publish explicit SDK-to-protocol compatibility information.

## Expand from demonstrated use cases

- Rust worker helpers and native computation examples.
- Optional structured payload/error profiles with explicit compatibility rules.
- Additional host SDKs, bounded pools, or diagnostic tooling when users need them.

The Java API remains dependency-free at runtime. Planned SDKs will use idiomatic
runtime facilities rather than requiring a shared native binary or central daemon.
