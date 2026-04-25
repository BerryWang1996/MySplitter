# Development Plan

## Status

Stage one stabilization is complete.

The `1.0.0`, `1.0.1`, and `1.0.2` release tags are cut. The `1.0.2` release is the current documentation and CI closure point on `vibe-coding`.

## Version Strategy

- `1.0.x`: post-release hardening and production-readiness fixes. Focus on verification honesty, packaging, documentation closure, security posture, and semantic correctness.
- `1.1.x`: distributed transaction SPI and XA MVP. Multi-datasource transactions are a core project capability, not an optional observability add-on.
- `1.2.x`: heterogeneous XA compatibility and failure-recovery matrix across database brands.
- `1.3.x`: AT-style automatic compensation for selected relational database dialects.
- `1.4.x`: TCC and Saga extension points for heterogeneous resources that cannot safely use XA or AT.
- `2.0.0`: reserved for breaking platform shifts such as a Java 17 baseline, Spring Boot 3.x, and Jakarta migration.

Completed baseline work:

- Removed high-risk initialization and shutdown behaviors such as `System.exit(...)` and uncontrolled stack trace printing.
- Switched starter configuration loading to `Resource` and `InputStream`.
- Improved `DataSource`, `Connection`, and `Statement` proxy lifecycle behavior.
- Wired `filters` into the execution path.
- Added Maven Wrapper and unified UTF-8 build settings.
- Cleaned up the README and sample configuration files.
- Added the first Testcontainers-based routing regression slice for the core transaction model.
- Split regression tests into the dedicated `mysplitter-tests` module.
- Added in-module reflection-based regression tests so `mvn -pl mysplitter test` and `verify` are no longer empty or false-green.
- Landed the Spring Boot `2.7.x` starter baseline with dual registration metadata.
- Upgraded the demo consumer to the same Spring Boot `2.7.x` generation as the starter baseline.
- Added an always-on H2 routing integration slice so release checks no longer depend solely on Docker-backed MySQL coverage.
- Documented the `v1.0.0` compatibility matrix.
- Cut and pushed the `1.0.1` and `1.0.2` maintenance releases after the full release gate passed.

Current checkpoint:

- The `v0.11` logical connection model is landed and covered by both module-level and integration-level regression tests.
- The `v0.12.1` regression cleanup is complete: filter hooks now run per routed SQL, `abort()` is terminal for the logical connection, nullable state resets are replayed correctly, and the permanent `maven.test.skip` bypass is gone.
- The latest review list contains several stale items that no longer match the current tree, so the focus has shifted from replaying old regressions to hardening the remaining `v0.12` routing and health behavior.
- Concurrent health-state transitions now have dedicated regression coverage, including stale recovery and version-matched healing scenarios.
- Bounded failover now uses per-call candidate snapshots so a node that fails during one route/default-connection attempt is not immediately retried again in the same call as an ill-node fallback.
- The `v0.12` routing/health design is now documented in `docs/v0.12-routing-health-design.md`.
- Failover observability remains deferred to `v1.1.0`; `v1.0.3` is now prioritized first to close production-readiness blockers found during review.
- The `v1.0.0` release-baseline slices are now documented in `docs/v1.0-release-baseline-plan.md`.
- The Java 8 baseline is now landed in the Maven build and regression dependency stack.
- Cold-reactor compile smoke checks are now green for both `mysplitter-spring-boot-starter` and `demo` without relying on install-first verification.
- Starter bootstrap behavior is now isolated behind a dedicated configuration loader and covered by starter-module tests.
- HikariCP is now the documented and regression-validated primary pool for the `v1.0.0` release baseline.
- The Boot `2.7.x` starter implementation slice is now landed with both `spring.factories` and `AutoConfiguration.imports`.
- The demo consumer now compiles on Spring Boot `2.7.18` with the matching MyBatis baseline.
- The `v1.0.0` compatibility matrix is now documented in `docs/v1.0-compatibility-matrix.md`.
- End-to-end routing release checks now include an always-on H2 path, while the Docker-backed MySQL slice remains supplemental.
- The `1.0.1` maintenance line now includes the release-gate hardening slice.
- A repo-root release-gate script now drives the canonical release verification flow for the `1.0.x` maintenance line.
- The `1.0.1` release commit and tag have been pushed to the remote `vibe-coding` branch.
- GitHub Actions now runs the canonical release gate on PRs, manual dispatch, and pushes to `master` / `vibe-coding`.
- The `1.0.2` release commit and tag have been pushed to the remote `vibe-coding` branch.
- The current tree is a formal `1.0.2` release version, not a `-SNAPSHOT` development version.
- The first `1.0.3` hardening slices are underway: YAML loading now uses SafeConstructor-based primitive mapping, password handling now has explicit `plain`, `environment`, and `legacy-rsa` source modes, and local transactions now fail fast before spanning multiple physical connections.

The next goal is to close the `1.0.3` production-readiness blockers before starting the `1.1.0` distributed transaction foundation.

## Review Reconciliation

- Closed: nullable connection-state clearing is preserved for later routed connections.
- Closed: `Connection.abort(...)` now closes the logical proxy and prevents further use.
- Closed: `DataSourceFilterAdvise` runs for reused and transaction-pinned routes as well as newly opened routes.
- Closed: `mysplitter` module verification no longer relies on permanently skipped tests.
- Not present in the current tree: the previous `systemPath` self-dependency issue is no longer in `mysplitter/pom.xml`.
- Closed: the health-manager race finding referenced an older `LinkedHashSet` design; the current implementation uses concurrent maps and has dedicated concurrent transition coverage.
- Closed: the Spring Boot baseline finding is stale; the parent build now uses Spring Boot `2.7.18`, and the starter ships both `spring.factories` and `AutoConfiguration.imports`.
- Closed: the snapshot release blocker is stale; the current tree is versioned as `1.0.2`, and release tags point at formal release commits.
- Mitigated: Docker-backed MySQL validation can still skip when Docker is unavailable, but the release gate now includes an always-on H2 routing integration path for baseline routing and transaction coverage.
- Closed for `1.0.3`: configuration password protection now has a clearer mode model. Development users may choose plain YAML values for convenience, while production users can resolve passwords from system properties or environment variables; the legacy RSA helper is documented as compatibility-only.
- Closed for `1.0.3`: YAML parsing now uses SnakeYAML safe construction and manual primitive mapping instead of unsafe type construction.
- Closed for `1.0.3`: cross-physical-connection local transactions now fail fast instead of attempting non-atomic best-effort commit/rollback across multiple physical connections.
- Re-scoped: distributed multi-datasource transactions are now a core roadmap item. `1.0.3` keeps unsafe local multi-connection transactions blocked, while `1.1.0+` introduces a real transaction coordinator instead of pretending local JDBC commits are atomic.
- Open for `1.0.3`: `Statement` batch execution is unsafe across multiple routed statements because `executeBatch()` only delegates to the current physical statement.
- Open for `1.0.3`: the default read/write parser is too naive for production SQL semantics such as comments, `WITH`, `SELECT FOR UPDATE`, vendor hints, and administrative statements.

## Planning Principles

- Stabilize semantics before expanding features.
- Separate routing, connection lifecycle, and health management responsibilities.
- Keep behavior observable and testable.
- Upgrade platform compatibility only after the core model is reliable.

## Roadmap

### v0.11 - Core Transaction Model

Goal: make logical connections and transaction behavior reliable.

Design reference:

- `docs/v0.11-connection-context-design.md`

Scope:

- Introduce `ConnectionContext` for logical connection state.
- Reuse physical connections per target node inside one logical connection.
- Add transaction pinning.
- Force reads to use writer nodes while a transaction is active.
- Replace reflection-style standby replay with explicit state propagation.

Primary areas:

- `mysplitter/src/main/java/com/mysplitter/MySplitterConnectionProxy.java`
- `mysplitter/src/main/java/com/mysplitter/MySplitterStatementProxy.java`
- `mysplitter/src/main/java/com/mysplitter/MySplitterDataSourceManager.java`
- `mysplitter/src/main/java/com/mysplitter/MySplitterConnectionHolder.java`
- `mysplitter/src/main/java/com/mysplitter/MySplitterStatementHolder.java`

Exit criteria:

- Transaction commit and rollback are deterministic.
- Connection attributes are applied consistently across reused physical connections.
- Read-after-write behavior inside a transaction is stable.

### v0.12 - Routing and Health Refactor

Goal: decouple node selection from node health and failure handling.

Scope:

- Extract `RoutePlanner`.
- Extract `DataSourceRegistry`.
- Extract `HealthManager`.
- Keep load balancers responsible only for selection.
- Model node weights explicitly instead of repeating list entries.
- Replace recursive failover with bounded retry policies.

Primary areas:

- `mysplitter/src/main/java/com/mysplitter/MySplitterDataSourceManager.java`
- `mysplitter/src/main/java/com/mysplitter/MySplitterDatabaseManager.java`
- `mysplitter/src/main/java/com/mysplitter/selector/`
- `mysplitter/src/main/java/com/mysplitter/config/`

Exit criteria:

- Unique node sets are preserved.
- Failover attempts are bounded and observable.
- Health transitions are independent from selection strategy.

### v0.12.1 - Post-Refactor Regression Closure

Status: complete.

Goal: close semantic regressions introduced during the `v0.11` and `v0.12` refactors before any platform upgrade work begins.

Scope:

- Restore `DataSourceFilterAdvise` execution for every routed SQL, including route reuse and transaction-pinned reads.
- Make `Connection.abort(...)` behave like a terminal lifecycle transition for the logical proxy.
- Remove the permanent `maven.test.skip` bypass from `mysplitter/pom.xml`.
- Add honest module-scoped regression coverage for `mysplitter`.
- Correct or expand integration tests so they validate per-SQL filter semantics and post-abort behavior.

Primary areas:

- `mysplitter/src/main/java/com/mysplitter/MySplitterDataSourceManager.java`
- `mysplitter/src/main/java/com/mysplitter/MySplitterConnectionProxy.java`
- `mysplitter/pom.xml`
- `mysplitter-tests/src/test/java/com/mysplitter/test/`

Exit criteria:

- Filter hooks fire once per routed SQL, even when the same physical connection is reused.
- `abort()` makes `isClosed()` return `true` and prevents further use of the logical connection.
- `mvn -pl mysplitter test` no longer passes by skipping the module tests.
- `mvn -pl mysplitter-tests -am test` remains green with the updated routing assertions.

### v0.12.2 - Health and Failover Hardening

Goal: finish the still-open `v0.12` work by validating concurrent health-state behavior and making failover semantics safer and easier to reason about.

Scope:

- Add concurrency-focused regression tests for `MySplitterDataSourceHealthManager`.
- Verify `markIll(...)`, `markHealthy(...)`, scheduled recovery, and selector cleanup under concurrent access.
- Tighten recovery semantics so stale recovery tasks cannot overwrite newer health decisions.
- Continue moving failover behavior toward bounded, observable retry rules instead of open-ended recovery assumptions.
- Record the supported verification command set for core-module tests, integration tests, starter compile checks, and demo compile checks.

Primary areas:

- `mysplitter/src/main/java/com/mysplitter/MySplitterDataSourceHealthManager.java`
- `mysplitter/src/main/java/com/mysplitter/MySplitterDataSourceManager.java`
- `mysplitter/src/test/java/com/mysplitter/`
- `mysplitter-tests/src/test/java/com/mysplitter/test/`
- `docs/`

Exit criteria:

- Health-state transitions stay correct under concurrent mark/recover cycles.
- Recovery tasks do not resurrect stale node state after a newer failure version is recorded.
- Verification guidance clearly distinguishes fast module checks from the full regression suite.
- The remaining `v0.12` behavior is ready to be treated as a stable baseline for `v1.0.0`.

### v1.0.0 - Release Baseline

Status: complete.

Goal: reach a publishable baseline for the core library and starter.

Scope:

- Move the minimum Java baseline to 8.
- Align the starter with Spring Boot 2.7.
- Define first-class HikariCP support.
- Keep other pools behind adapters or SPI-based integration.
- Finalize the public configuration model and compatibility notes.

Primary areas:

- `pom.xml`
- `mysplitter-spring-boot-starter/`
- `demo/`

Exit criteria:

- `mysplitter` and `mysplitter-spring-boot-starter` build and package cleanly.
- Demo can run without snapshot-only dependencies.
- Version compatibility matrix is documented.

### v1.0.1 - Release Gate Hardening

Status: complete.

Goal: make post-release verification honest, repeatable, and easy to run as one release gate.

Scope:

- Add a root-level release verification path that executes the core-module checks, starter checks, and the dedicated `mysplitter-tests` regression module together.
- Remove the remaining ambiguity where `mvn -pl mysplitter test` is honest for the module itself but does not represent the full regression surface.
- Document one canonical release-check command for local use and future CI integration.
- Keep the current `1.0.0` API and configuration model stable while tightening the build contract around it.

Primary areas:

- `pom.xml`
- `mysplitter/pom.xml`
- `mysplitter-tests/pom.xml`
- `docs/verification-commands.md`
- future CI configuration files

Exit criteria:

- There is one documented release-gate command that validates the full supported regression surface.
- Core-module validation and cross-module validation are clearly separated and intentionally named.
- A broken `mysplitter-tests` suite can no longer be mistaken for a green release candidate.
- The release commit and `1.0.1` tag are published to the remote repository.

### v1.0.2 - Compatibility And Documentation Closure

Status: complete.

Goal: tighten the release story around the `1.0.x` baseline without expanding the feature surface.

Scope:

- Refine compatibility notes for Java, Spring Boot, and pool support.
- Improve README and demo startup guidance.
- Add release notes / upgrade notes for users coming from the `0.9.x` line.
- Clean up any remaining low-risk dependency or packaging inconsistencies found during `1.0.1`.
- Keep the README linked to the release notes, upgrade guide, compatibility matrix, and verification commands.

Primary areas:

- `README.md`
- `docs/`
- `demo/`
- `.github/workflows/release-gate.yml`
- release metadata files

Exit criteria:

- A new user can understand the supported baseline and verification path from the repo docs alone.
- The `1.0.x` maintenance line has explicit compatibility and upgrade notes.
- The release commit and `1.0.2` tag are published to the remote repository.

### v1.0.3 - Production Readiness Hardening

Status: next.

Goal: close the P1 review findings that block enterprise production deployment.

Scope:

- Define explicit password source modes: plain configuration for local development, Spring/environment placeholders for normal deployment, and external secret systems for production.
- Preserve plain YAML password support as an intentional developer-convenience mode chosen by the user.
- Deprecate the current RSA helper for production use, remove default embedded key material, and stop presenting config encryption as equivalent to secret management.
- Keep backward compatibility for existing encrypted sample configs during a transition period, but add warnings and migration guidance.
- Upgrade and harden YAML parsing so configuration loading does not use unsafe SnakeYAML construction.
- Define and enforce transaction guardrails for logical transactions that touch multiple physical connections.
- Correct `Statement` batch behavior across routes, or explicitly reject multi-route batches with a clear exception.
- Improve the default read/write parser enough to avoid dangerous reader routing for lock-sensitive or ambiguous SQL.
- Add regression tests for each production-readiness finding.
- Document remaining supported and unsupported production semantics.

Primary areas:

- `mysplitter/src/main/java/com/mysplitter/util/SecurityUtil.java`
- `mysplitter/src/main/java/com/mysplitter/util/ConfigurationUtil.java`
- `mysplitter/src/main/java/com/mysplitter/MySplitterConnectionProxy.java`
- `mysplitter/src/main/java/com/mysplitter/MySplitterStatementProxy.java`
- `mysplitter/src/main/java/com/mysplitter/DefaultReadAndWriteParser.java`
- `mysplitter/src/test/java/com/mysplitter/`
- `mysplitter-tests/src/test/java/com/mysplitter/test/`
- `docs/`

Exit criteria:

- SCA no longer flags the baseline YAML parser dependency for the reviewed SnakeYAML issue.
- Password handling documentation clearly separates local-development plain values from production external secret injection.
- Existing users have a migration path from RSA config encryption to environment/secret placeholders.
- Multi-route local transactions cannot be mistaken for atomic distributed transactions.
- Batch execution either runs all routed batches with deterministic result ordering or fails fast when a batch spans multiple routes.
- Default SQL classification routes ambiguous and lock-sensitive SQL conservatively to writers.
- The release gate includes regression coverage for the fixed semantics.

### v1.1.0 - Distributed Transaction SPI And XA MVP

Status: planned after `v1.0.3` production-readiness hardening is complete.

Goal: make multi-datasource transactions a first-class MySplitter subsystem, with XA as the first production-grade atomic transaction mode for different database brands.

Design reference:

- `docs/distributed-transaction-roadmap.md`

Scope:

- Add `transaction.mode`: `local`, `xa`.
- Add transaction manager, branch transaction, coordinator, and transaction log SPI.
- Add JDBC `XADataSource` adapter support.
- Enlist each routed physical datasource as a branch in one global transaction.
- Implement two-phase prepare, commit, rollback, and durable recovery.
- Integrate with Spring transactions in the starter.
- Fail clearly when a datasource or driver cannot support the selected distributed transaction mode.

Primary areas:

- `mysplitter/`
- `mysplitter-spring-boot-starter/`
- `mysplitter-tests/`
- `README.md`
- `docs/`

Exit criteria:

- One transaction can update two different routed XA datasources atomically.
- Crash/restart recovery can finish prepared branches.
- Unsupported datasources fail before transaction work starts.
- The compatibility and operational limits are documented.

### v1.2.0 - Heterogeneous XA Compatibility Matrix

Goal: prove XA behavior across multiple database brands and failure modes.

Scope:

- Add Testcontainers suites for MySQL, PostgreSQL, and at least one additional database target if licensing/tooling allows.
- Document driver and datasource requirements per database brand.
- Add failure-injection coverage for prepare failure, commit failure, rollback failure, and recovery.
- Add metrics and logs for global transaction state.

Primary areas:

- `mysplitter/`
- `mysplitter-tests/src/test/`
- `mysplitter-spring-boot-starter/src/test/`
- `docs/`
- CI configuration files

Exit criteria:

- Supported database brands have verified XA examples.
- Unsupported or partially supported brands are documented honestly.
- CI can block regressions in distributed transaction recovery behavior.

### v1.3.0 - AT Mode Foundation

Goal: provide non-intrusive compensation transactions for selected relational databases.

Scope:

- Add `transaction.mode: at`.
- Add SQL parser and dialect SPI.
- Add undo log model and DDL generator.
- Implement before image / after image capture for simple DML.
- Add global lock table and conflict handling.
- Start with MySQL and H2 coverage, then expand.

Primary areas:

- `mysplitter/`
- `mysplitter-tests/src/test/`
- `docs/`

Exit criteria:

- Simple `INSERT`, `UPDATE`, and `DELETE` can rollback through undo logs on supported dialects.
- Unsupported SQL fails fast with a clear message.

### v1.4.0 - TCC And Saga SPI

Goal: support heterogeneous resources that cannot participate in XA or AT.

Scope:

- Add TCC branch SPI for try/confirm/cancel.
- Add Saga compensation SPI for long-running workflows.
- Add idempotency, empty rollback, and hanging-prevention contracts.
- Keep these modes explicit and opt-in.

Primary areas:

- `mysplitter/`
- `mysplitter-spring-boot-starter/`
- `docs/`

Exit criteria:

- Users can mix JDBC branches with explicit business compensation branches under one global transaction model.

### v1.5.0 - Observability and Runtime Operations

Goal: make routing, failover, and distributed transaction state visible in production environments.

Scope:

- Add Micrometer metrics.
- Expose route hits, retries, failovers, unhealthy node counts, global transaction counts, branch states, and recovery queues.
- Add starter hooks for Actuator integration.
- Document extension points for parser, router, filter, alert handler, and transaction coordinator implementations.

Primary areas:

- `mysplitter/`
- `mysplitter-spring-boot-starter/`
- `README.md`

Exit criteria:

- Core routing and transaction metrics can be exported.
- Operational behavior is inspectable without debugging the source code.

## Immediate Backlog

1. Done: logical connection lifecycle and transaction rules are defined in `docs/v0.11-connection-context-design.md`.
2. Done: `MySplitterConnectionProxy` now uses a reusable `ConnectionContext` and transaction pinning model.
3. Done: health-state tracking is split from selector behavior through registry and health manager layers.
4. Done: removed the `demo` snapshot dependency on `org.openjfx:javafx.base:11.0.0-SNAPSHOT`.
5. Done: added the first Testcontainers integration test matrix for reader routing, writer routing, and transactional writer pinning.
6. Done: restored filter hook semantics for reused and pinned routes and updated routing assertions.
7. Done: fixed logical connection shutdown semantics for `abort()` and added regression coverage.
8. Done: removed the permanent `maven.test.skip` shortcut and made `mysplitter` verification honest with module-local tests.
9. Done: added concurrency and stale-recovery regression coverage for `MySplitterDataSourceHealthManager`.
10. Done: bounded failover in `MySplitterDataSourceManager` now avoids immediate same-call retries of nodes that already failed earlier in the same acquisition path.
11. Done: reran and documented the stable verification command set for core, integration, starter, and demo modules in `docs/verification-commands.md`.
12. Done: updated versioned design notes so `v0.12` completion criteria match the code that is already landed.
13. Done: deferred failover observability to `v1.1.0` to keep `v1.0.0` focused on release-baseline compatibility work.
14. Done: broke `v1.0.0` into concrete upgrade slices for Java baseline, Spring Boot starter compatibility, and pool support policy in `docs/v1.0-release-baseline-plan.md`.
15. Done: landed the Java 8 baseline in Maven build configuration and core regression dependencies.
16. Done: reran the full verification command set after the Java baseline upgrade.
17. Done: removed the install-first workaround by producing reactor-consumable jars during `compile` for upstream modules needed by downstream smoke checks.
18. Done: aligned `demo` clean-plugin configuration with the offline toolchain used by the rest of the repo so cold compile checks remain reproducible.
19. Done: documented the starter compatibility scope and remaining Boot 3.x limits in `docs/v1.0-starter-compatibility-scope.md`.
20. Done: isolated starter resource loading into a dedicated loader and added starter-module tests for classpath, file, and missing-resource bootstrap paths.
21. Done: landed the Hikari-first pool support policy in docs, demo configuration, and the main regression path, while keeping older pools in compatibility-validation mode.
22. Done: landed the Boot `2.7.x` starter slice with dual registration through `spring.factories` and `AutoConfiguration.imports`.
23. Done: upgraded the demo consumer in the same pass so the sample app matches the release starter baseline.
24. Done: documented the `v1.0.0` compatibility matrix covering Java, Spring Boot, demo, and pool support tiers.
25. Done: added an always-on H2 routing integration slice so release validation no longer relies entirely on Docker availability.
26. Done: cut the `1.0.0` release tag.
27. Done: added a root-level release-gate verification path for the `1.0.1` maintenance line.
28. Done: added repo-root `release-gate` scripts for Windows and Unix-like shells so the full release verification path is executable as one command.
29. Done: cut and pushed the `1.0.1` release tag after validating the release gate.
30. Done: added release notes and a `v1.0.x` upgrade guide for users adopting the baseline.
31. Done: wired GitHub Actions to run the canonical release gate directly.
32. Done: added demo startup guidance for the `1.0.x` baseline.
33. Done: cut and pushed the `1.0.2` release tag after validating the release gate.
34. Done: recorded the `1.0.2` release state and cleaned up stale plan wording.
35. Done: hardened YAML loading with SnakeYAML safe construction and regression coverage.
36. Done: added explicit password source modes for plain local config, environment-backed deployment config, and legacy RSA compatibility.
37. Done: defined and enforced transaction guardrails for logical transactions that touch multiple physical connections.
38. Next: correct or reject multi-route `Statement` batch execution.
39. Later: start distributed transaction SPI and XA MVP in `v1.1.0`.
40. Later: add heterogeneous XA compatibility coverage in `v1.2.0`.
41. Later: add AT-style automatic compensation in `v1.3.0`.
42. Later: add TCC/Saga extension modes in `v1.4.0`.

## Suggested Delivery Sequence

1. Finish `v1.0.3` production-readiness hardening.
2. Start distributed transaction SPI and XA MVP in `v1.1.0`.
3. Build out the heterogeneous database transaction matrix in `v1.2.0`.
4. Add AT compensation after XA state, logging, and recovery are reliable.
5. Plan the eventual Java 17 / Boot 3.x break in `2.0.0` rather than leaking it into the `1.x` line.

## Risks To Watch

- Transaction semantics may change observable behavior for current users.
- Fixing cross-route transactions may require failing fast in scenarios that previously attempted best-effort local commits.
- XA support depends on real driver and database behavior, so compatibility must be proven by database brand rather than assumed from JDBC interfaces.
- AT mode is SQL dialect sensitive and must not claim broad database support before dialect-specific undo-log coverage exists.
- TCC and Saga shift correctness into user-defined business compensation, so APIs must make idempotency and retry contracts explicit.
- Hardening password handling may require deprecating the current config-encryption helper rather than preserving its exact behavior.
- A conservative default SQL parser may route more statements to writers until a stronger parser is introduced.
- Health recovery code can look correct in static review but still break under concurrent routing pressure, so it needs proof by test rather than inspection alone.
- Java and Spring upgrades may surface compatibility gaps in the starter.
- Docker is no longer required for baseline release validation because the H2 routing slice is always-on, but it is still required for the supplemental MySQL Testcontainers path.
