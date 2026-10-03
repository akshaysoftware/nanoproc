# Nanoproc

Nanoproc: tiny, safe child processes and persistent workers for Java.

**Status:** pre-release. Java 21+; zero runtime dependencies.

Nanoproc provides a small layer above `ProcessBuilder`: concurrent process I/O,
execution deadlines, bounded captured output, and best-effort cleanup.
Persistent framed workers are planned for v0.1.0.

Here, **safe** means bounded process management. Nanoproc is **not a sandbox**:
children retain filesystem, network, environment, and OS permissions.
It does not impose CPU or child-memory quotas or make untrusted code safe.

## Development

Use JDK 25 by default; the library compiles for Java 21.

```bash
./mvnw spotless:apply
./mvnw verify
```

On Windows PowerShell, use `.\mvnw.cmd` in place of `./mvnw`.

## Contributing

Bug reports, documentation improvements, and small focused PRs are welcome.
See [CONTRIBUTING.md](CONTRIBUTING.md), [the roadmap](docs/roadmap.md),
and [the design contract](docs/design.md).

## License

Apache-2.0. See [LICENSE](LICENSE).