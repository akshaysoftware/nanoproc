# Worker protocol

**Status: draft. No stable protocol version or conformance certification is
published yet.**

Nanoproc's protocol separates a local worker's message framing from its payload
encoding and the host's lifecycle policy. SDK implementations are independent;
the specification and shared compatibility tests define interoperability.

- [Raw-byte worker draft](raw-byte-worker.md): framing, stream ownership, and
  the target request/response contract.
- [Design and implementation status](../docs/design.md): current Java behaviour
  and limitations.
- [Conformance work](../conformance/README.md): planned shared fixtures and
  cross-language verification.

The raw transport has no handshake, version field, method routing, or prescribed
payload codec. Do not add control messages to an existing raw exchange. A future
JSON or negotiated profile will need its own documented compatibility rules.

SDK package versions and protocol/profile versions are separate. Each future
SDK release must state which protocol/profile it implements. Planned SDKs do
not imply that cross-language compatibility has already been tested.
