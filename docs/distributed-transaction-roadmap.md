# Distributed Transaction Roadmap

Distributed transactions are a core MySplitter capability, especially when one logical business operation spans multiple datasource nodes, multiple logical databases, or different database brands.

This roadmap follows the same broad transaction-mode separation used by systems such as Apache Seata, but keeps the MySplitter design aligned with its JDBC routing role.

## Design Position

MySplitter should support multiple transaction strategies instead of pretending one strategy fits every database and workload.

- `local`: single physical connection transaction. This is the safe default and must fail fast when a transaction would span multiple physical connections.
- `xa`: standards-based distributed transaction mode for databases and drivers that support `XADataSource`.
- `at`: automatic compensation mode for relational databases that support local ACID transactions and JDBC interception.
- `tcc`: business-defined try/confirm/cancel mode for resources that cannot be safely rolled back automatically.
- `saga`: long-running compensation mode for heterogeneous systems, legacy systems, or cross-service workflows.

## Why XA First

For multiple database brands, XA is the least surprising first distributed transaction target because it is based on a standard protocol and does not require SQL-specific undo generation.

The first production-grade distributed transaction milestone should therefore be:

- A transaction manager SPI.
- Branch registration and global transaction state.
- `XADataSource` resource enlistment.
- Two-phase prepare / commit / rollback.
- Durable transaction logs and recovery.
- A compatibility matrix for each supported database and driver.

This gives MySplitter a correct atomic baseline before adding AT-style SQL compensation.

## Why AT Later

AT mode is attractive because it can be non-intrusive for business code, but it is database-dialect-sensitive.

An AT implementation must understand:

- SQL parsing for `INSERT`, `UPDATE`, and `DELETE`.
- Primary key discovery.
- Before image and after image queries.
- Undo log table DDL per database brand.
- Global lock keys and lock conflict handling.
- Rollback validation when data has been changed by another transaction.
- Dialect differences across MySQL, PostgreSQL, Oracle, SQL Server, H2, and other supported databases.

Because of this, AT should start with one or two first-class dialects, then expand through a dialect SPI.

## Version Plan

### v1.0.3 - Safe Baseline

Goal: prevent unsafe semantics before distributed transaction support lands.

- Keep local cross-physical-connection transactions fail-fast.
- Document that `local` mode is single-connection only.
- Fix multi-route statement batch behavior.
- Improve default SQL classification so lock-sensitive SQL goes to writers.

### v1.1.0 - Distributed Transaction SPI And XA MVP

Goal: make multi-datasource transactions a first-class MySplitter subsystem.

- Add `transaction.mode`: `local`, `xa`.
- Add `transaction.coordinator` and `transaction.recovery` configuration so the public YAML shape is stable before the XA runtime lands.
- Keep `local` as the default mode and fail clearly for `xa` until the transaction manager can provide real two-phase semantics.
- Add `GlobalTransactionManager`, `BranchTransaction`, `TransactionCoordinator`, and `TransactionLogStore` interfaces.
- Route existing local commit, rollback, and single-physical-connection guardrails through the transaction manager abstraction.
- Add XA resource descriptors so each configured datasource node can report whether an `XADataSource` adapter is available.
- Add XA branch primitives around `XAResource`, `Xid`, prepare, commit, rollback, and read-only branch handling.
- Add JDBC `XADataSource` adapter support that can create `XAConnection`, physical `Connection`, `XAResource`, and branch transaction objects.
- Add an embedded coordinator that owns global transaction ids, branch enlistment, prepare/commit/rollback ordering, and log-state transitions.
- Enlist each routed physical datasource as a branch.
- Implement two-phase commit and rollback.
- Persist global decisions and branch transaction state.
- Add recovery for in-doubt branches.
- Integrate with Spring transactions in the starter.

Exit criteria:

- One transaction can update two different routed XA datasources atomically.
- Crash/restart recovery can finish prepared branches.
- Unsupported datasources fail clearly before transaction work starts.

Current implementation checkpoint:

- The transaction manager SPI, XA branch primitives, XA datasource adapter, embedded coordinator, and in-memory log store are in place.
- Routed SQL connection opening now flows through the transaction manager, so internal XA tests can open, reuse, end, commit, rollback, and close enlisted branches from the logical connection context.
- An append-only file transaction log store can persist branch status and global decision changes, then rebuild latest branch state after process restart.
- An XA resource registry and recovery executor foundation can map logged `resourceId` values to XA-capable datasource adapters and use `XAResource.recover(...)` to finish branches that already have a durable commit/rollback decision.
- The embedded coordinator can delegate its `recover()` call to that recovery executor when runtime wiring supplies one.
- Datasource-manager initialization now creates the XA runtime only after datasource adapters are initialized, so a registry-backed XA manager can be built without guessing resource ids.
- `transaction.recovery.enabled` now has a datasource-manager scheduler hook for startup and periodic XA recovery scans when an XA manager is configured.
- XA recovery scans now use the portable `TMSTARTRSCAN` / `TMNOFLAGS` / `TMENDRSCAN` sequence instead of assuming a driver accepts combined recover flags.
- `XA_RDONLY` prepare results are recorded as completed branches, preventing read-only branches from being reported as in-doubt work.
- An always-on H2 XA integration baseline now verifies two routed branches through commit and rollback using the `MySplitterDataSource` path.
- XA mode now rejects non-XA-capable datasource nodes during datasource initialization instead of waiting until routed SQL reaches an unsupported node.
- A Docker-gated MySQL recovery suite now passes for file-log recovery decisions covering prepared branch commit and rollback.
- MySQL Testcontainers coverage disables SSL explicitly for the legacy MySQL 5.1 driver on JDK 17+.
- A Docker-gated PostgreSQL recovery suite now passes for file-log recovery decisions covering prepared branch commit and rollback when `max_prepared_transactions` is enabled.
- XA failure-injection coverage now verifies prepare failure, commit failure, rollback failure, recovery commit failure, recovery rollback failure, and recover-scan failure without losing recoverable branch state.
- A Docker-gated PostgreSQL recovery failure suite now verifies that prepared file-log branches stay recoverable when the real database is unavailable during recovery.
- XA operations notes now document file-log retention, unresolved branch behavior, resource-id stability, and current production caveats.
- `transaction.mode: xa` remains intentionally blocked by default in `v1.1.0`; it is available only through `mysplitter.experimental.xa.enabled=true` for development validation.

Remaining before opening `transaction.mode: xa`:

- Keep the MySQL/PostgreSQL compatibility matrix current in `docs/v1.1-xa-compatibility-matrix.md`.
- Keep operational recovery behavior current in `docs/v1.1-xa-operations.md`.
- Add admin visibility, stronger operational tooling, and explicit release support terms before promoting XA beyond the current experimental gate.

### v1.2.0 - Heterogeneous XA Compatibility Matrix

Goal: prove XA behavior across database brands.

- Maintain Testcontainers suites for MySQL and PostgreSQL, and add at least one additional database target if licensing/tooling allows.
- Document driver and datasource requirements per database.
- Add failure injection tests for prepare failure, commit failure, rollback failure, and recovery.
- Add metrics and logs for global transaction state.

Exit criteria:

- Supported database brands have verified XA examples.
- Unsupported or partially supported brands are documented honestly.

### v1.3.0 - AT Mode Foundation

Goal: provide non-intrusive compensation transactions for selected relational databases.

- Add `transaction.mode: at`.
- Add SQL parser/dialect SPI.
- Add undo log model and DDL generator.
- Implement before image / after image capture for simple DML.
- Add global lock table and conflict handling.
- Start with MySQL and H2 test coverage, then expand.

Exit criteria:

- Simple `INSERT`, `UPDATE`, and `DELETE` can rollback through undo logs on supported dialects.
- Unsupported SQL fails fast with a clear message.

### v1.4.0 - TCC And Saga SPI

Goal: support heterogeneous resources that cannot participate in XA or AT.

- Add TCC branch SPI for try/confirm/cancel.
- Add Saga compensation SPI for long-running workflows.
- Add idempotency, empty rollback, and hanging-prevention contracts.
- Keep this opt-in and explicit.

Exit criteria:

- Users can mix JDBC branches with explicit business compensation branches under one global transaction model.

## Configuration Direction

Example future configuration:

```yaml
mysplitter:
  transaction:
    mode: xa
    coordinator:
      type: embedded
      logStore: file
      logFile: ./target/mysplitter-xa.log
    recovery:
      enabled: true
      interval: 10s
  databases:
    database-a:
      writers:
        writer-1:
          configuration:
            xaDataSourceClass: com.mysql.jdbc.jdbc2.optional.MysqlXADataSource
            xaProperties:
              url: jdbc:mysql://127.0.0.1:3306/app
              user: ${DB_USER}
              password: ${DB_PASSWORD}
```

For `at`:

```yaml
mysplitter:
  transaction:
    mode: at
    undoLog:
      table: undo_log
    lock:
      table: global_lock
```

## Non-Goals For The First Milestone

- Do not implement automatic AT compensation before XA transaction state and recovery are reliable.
- Do not claim cross-database atomicity for non-XA datasources.
- Do not silently downgrade distributed transactions to local transactions.
- Do not support every SQL dialect at once.
