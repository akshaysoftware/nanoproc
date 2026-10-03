# Contributing

Thanks for helping improve Nanoproc. Keep changes small and easy to review.

## Workflow

1. Open an issue before a substantial API change; small fixes can go straight to a PR.
2. Fork the repository and create a branch.
3. Add regression tests for new behaviour and fixes.
4. Run `./mvnw fmt:format` and `./mvnw verify`.
5. Explain the problem, resulting behaviour, and validation in your PR.

Linux, macOS, and Windows all matter. Lifecycle tests should use explicit readiness
signals and generous deadlines rather than assuming fast subprocess startup.

Use JDK 25 for development. CI also checks Java 21. The wrapper pins Maven.

## Principles

- Keep the public API small and documented.
- Use JDK classes; preserve zero runtime dependencies.
- Never add an implicit shell.
- Keep all captured data bounded.
- Preserve interruption and honest cleanup guarantees.
- Avoid unrelated refactoring, framework adapters, and speculative abstractions.

## Commits and review

Use Conventional Commits, for example `feat: execute bounded processes`,
`fix: handle partial stdin writes`, or `docs: clarify worker framing`.
Breaking changes use `feat!:` with an explanation.

Akshay reviews contributions. Bot updates also require review; no automatic merges.
Maintainer-authored PRs still receive CI and a deliberate manual merge.
Release Please manages release PRs and the changelog.

Security reports belong in private vulnerability reporting, not public issues.