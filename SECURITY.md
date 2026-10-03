# Security policy

## Reporting

Use GitHub's private vulnerability reporting for this repository.
Do not report security-sensitive bugs in public issues. Include a minimal
reproduction, Java version, operating system, and affected Nanoproc version.

## Boundary

Nanoproc is **not a sandbox**. It bounds captured I/O and initiates termination
when deadlines or output limits are exceeded. It does not restrict filesystem,
network, environment access, native code, child memory, CPU, or process creation.
Descendant termination is best effort; processes can escape observation.

Do not use Nanoproc as the isolation boundary for untrusted code.

## Supported versions

No release is currently supported. A version policy will accompany v0.1.0.