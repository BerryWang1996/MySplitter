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
- Add JDBC `XADataSource` adapter support.
- Enlist each routed physical datasource as a branch.
- Implement two-phase commit and rollback.
- Persist global and branch transaction state.
- Add recovery for in-doubt branches.
- Integrate with Spring transactions in the starter.

Exit criteria:

- One transaction can update two different routed XA datasources atomically.
- Crash/restart recovery can finish prepared branches.
- Unsupported datasources fail clearly before transaction work starts.

### v1.2.0 - Heterogeneous XA Compatibility Matrix

Goal: prove XA behavior across database brands.

- Add Testcontainers suites for MySQL, PostgreSQL, and at least one additional database target if licensing/tooling allows.
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
      logStore: jdbc
    recovery:
      enabled: true
      interval: 10s
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
