# Release Notes

## 1.0.3

`1.0.3` is a production-readiness hardening release focused on closing P1 review findings before the distributed transaction roadmap begins.

Highlights:

- Upgraded SnakeYAML and replaced unsafe typed YAML construction with safe primitive mapping.
- Added explicit password source modes for local plain configuration, environment/system-property backed deployment configuration, and legacy RSA compatibility.
- Removed default embedded RSA key material and made legacy RSA config encryption compatibility-only.
- Added local transaction guardrails so cross-route local transactions fail fast instead of pretending best-effort JDBC commits are atomic.
- Fixed `Statement` batch execution so routed physical batches all run and update counts are merged in original `addBatch(...)` order.
- Made the default SQL parser conservative so lock-sensitive or ambiguous SQL routes to writers.
- Added a distributed transaction roadmap that makes XA, AT, TCC, and Saga first-class future transaction modes.

Validation command:

```powershell
.\scripts\release-gate.ps1
```

## 1.0.2

`1.0.2` is a maintenance release focused on documentation closure and CI release-gate enforcement.

Highlights:

- Added a `v1.0.x` upgrade guide for users moving from the `0.9.x` line.
- Added release notes for the `1.0.x` baseline.
- Added demo startup guidance with local MySQL schema setup, configuration notes, and endpoint examples.
- Wired GitHub Actions to run the canonical release gate on pull requests, manual dispatch, and pushes to `master` / `vibe-coding`.
- Linked README to release validation, release notes, upgrade guidance, compatibility notes, and demo startup guidance.

Validation command:

```powershell
.\scripts\release-gate.ps1
```

## 1.0.1

`1.0.1` is a maintenance release focused on release-gate honesty and repeatable validation.

Highlights:

- Added canonical repo-root release-gate scripts for Windows and Unix-like shells.
- Documented the release validation path in `docs/verification-commands.md`.
- Kept module-level checks honest while making the full cross-module regression suite the release sign-off entry point.
- Added always-on H2 routing integration coverage so baseline reader routing and transactional writer pinning are validated even when Docker is unavailable.
- Preserved Docker-backed MySQL Testcontainers coverage as a supplemental path when Docker is available.

Validation command:

```powershell
.\scripts\release-gate.ps1
```

## 1.0.0

`1.0.0` is the first release baseline after the stabilization and routing refactor work.

Highlights:

- Raised the supported Java baseline to Java 8.
- Landed the logical connection model with per-node connection reuse and transaction writer pinning.
- Separated health tracking from selector behavior.
- Bounded failover attempts to avoid retrying a node that already failed in the same acquisition path.
- Restored `DataSourceFilterAdvise` execution for every routed SQL.
- Made `Connection.abort(...)` a terminal logical connection transition.
- Switched the Spring Boot starter baseline to Spring Boot `2.7.x`.
- Published both `spring.factories` and `AutoConfiguration.imports` metadata for starter discovery.
- Made HikariCP the primary documented and regression-validated pool.
- Added Maven Wrapper and UTF-8 build configuration.
- Cleaned up README and sample configuration guidance.

Upgrade guide:

- See `docs/v1.0-upgrade-guide.md`.

Compatibility matrix:

- See `docs/v1.0-compatibility-matrix.md`.
