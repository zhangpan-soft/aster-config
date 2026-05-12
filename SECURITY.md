# Security Policy

## Supported Versions

The project is starting with the `0.1.x` line. Security fixes will target the latest `0.1.x` release until a new supported line is announced.

## Reporting a Vulnerability

Please report suspected vulnerabilities privately to the project maintainers instead of opening a public issue first.

Include:

- affected version or commit
- reproduction steps
- impact assessment
- any known workaround

## Production Embedding

The embedded admin canvas is designed to be hosted inside a business management system. Production deployments should provide their own `EmbedTokenValidator` and `UserProvider` beans so Aster can reuse the host system's identity, permission, and audit model.
