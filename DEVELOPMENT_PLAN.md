# Development Plan

## Status

Stage one stabilization is complete.

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

Current checkpoint:

- The `v0.11` logical connection model is landed and covered by both module-level and integration-level regression tests.
- The `v0.12.1` regression cleanup is complete: filter hooks now run per routed SQL, `abort()` is terminal for the logical connection, nullable state resets are replayed correctly, and the permanent `maven.test.skip` bypass is gone.
- The latest review list contains several stale items that no longer match the current tree, so the focus has shifted from replaying old regressions to hardening the remaining `v0.12` routing and health behavior.
- Concurrent health-state transitions now have dedicated regression coverage, including stale recovery and version-matched healing scenarios.
- The main active gap before a release baseline is bounded failover behavior and finishing the `v0.12` design/documentation alignment.

The next goal is to close the remaining `v0.12` health and failover risks, then move into release-baseline upgrades.

## Review Reconciliation

- Closed: nullable connection-state clearing is preserved for later routed connections.
- Closed: `Connection.abort(...)` now closes the logical proxy and prevents further use.
- Closed: `DataSourceFilterAdvise` runs for reused and transaction-pinned routes as well as newly opened routes.
- Closed: `mysplitter` module verification no longer relies on permanently skipped tests.
- Not present in the current tree: the previous `systemPath` self-dependency issue is no longer in `mysplitter/pom.xml`.
- Needs revalidation rather than assumption: the health-manager race finding referenced an older `LinkedHashSet` design; the current implementation uses concurrent maps, so the remaining work is to prove its semantics under contention with dedicated tests.

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

### v1.1.0 - Observability and Runtime Operations

Goal: make routing and failover visible in production environments.

Scope:

- Add Micrometer metrics.
- Expose route hits, retries, failovers, and unhealthy node counts.
- Add starter hooks for Actuator integration.
- Document extension points for parser, router, filter, and alert handler implementations.

Primary areas:

- `mysplitter/`
- `mysplitter-spring-boot-starter/`
- `README.md`

Exit criteria:

- Core metrics can be exported.
- Operational behavior is inspectable without debugging the source code.

### v1.2.0 - Quality System

Goal: make regression prevention part of normal development.

Scope:

- Add Testcontainers-based integration tests.
- Cover multi-database routing, read/write separation, failover, and transactional consistency.
- Add CI for build, unit tests, integration tests, and release validation.

Primary areas:

- `mysplitter/src/test/`
- `mysplitter-spring-boot-starter/src/test/`
- CI configuration files

Exit criteria:

- Critical routing and transaction scenarios are covered by automated tests.
- CI is sufficient to block unsafe releases.

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
10. Next: continue auditing bounded failover behavior in `MySplitterDataSourceManager`.
11. Done: reran and documented the stable verification command set for core, integration, starter, and demo modules in `docs/verification-commands.md`.
12. Next: update versioned design notes so `v0.12` completion criteria match the code that is already landed.

## Suggested Delivery Sequence

1. Finish `v0.12.2` health and failover hardening.
2. Reconfirm `v0.11` transaction semantics and `v0.12` routing semantics with green module and integration tests.
3. Upgrade release baseline for `v1.0.0`.
4. Add metrics and operational integration in `v1.1.0`.
5. Build out the full regression suite in `v1.2.0`.

## Risks To Watch

- Transaction semantics may change observable behavior for current users.
- Health recovery code can look correct in static review but still break under concurrent routing pressure, so it needs proof by test rather than inspection alone.
- Java and Spring upgrades may surface compatibility gaps in the starter.
- Docker is required to execute the Testcontainers regression slice; the test class now skips cleanly when Docker is unavailable.
