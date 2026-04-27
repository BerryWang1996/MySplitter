# MySplitter

MySplitter is a lightweight JDBC middleware for read/write splitting, multi-database routing, datasource failover, load balancing, and routed transaction management through `DataSource` / `Connection` / `Statement` proxies.

## Status

| Area | Current state |
| --- | --- |
| Latest release | `1.1.0` |
| Current development line | `1.2.0-SNAPSHOT` |
| Java baseline | Java 8+ |
| Spring Boot starter baseline | Spring Boot `2.7.x` |
| Primary documented pool | HikariCP |
| Production transaction mode | `local` |
| Experimental transaction mode | `xa`, gated by `mysplitter.experimental.xa.enabled=true` |

`1.1.0` ships the distributed transaction foundation and an experimental XA MVP for development validation. Production deployments should keep `transaction.mode: local` unless they explicitly accept the current experimental XA contract.

## Modules

- `mysplitter`: core library.
- `mysplitter-tests`: regression and integration verification module.
- `mysplitter-spring-boot-starter`: Spring Boot integration.
- `demo`: runnable sample application.

## Maven Coordinates

Core library:

```xml
<dependency>
    <groupId>com.mysplitter</groupId>
    <artifactId>mysplitter</artifactId>
    <version>1.1.0</version>
</dependency>
```

Spring Boot starter:

```xml
<dependency>
    <groupId>com.mysplitter</groupId>
    <artifactId>mysplitter-spring-boot-starter</artifactId>
    <version>1.1.0</version>
</dependency>
```

## Build

This repository includes Maven Wrapper, so a local Maven installation is not required.

```bash
./mvnw clean package -DskipTests
```

On Windows:

```powershell
.\mvnw.cmd clean package -DskipTests
```

## Release Validation

Use the release gate before cutting or promoting a release:

```powershell
.\scripts\release-gate.ps1
```

On Unix-like shells:

```bash
./scripts/release-gate.sh
```

The release gate runs core verification, starter tests, the dedicated regression module, demo compile checks, and release packaging. Docker-backed MySQL and PostgreSQL slices run when Docker is available; the H2 routing and XA baselines stay always-on.

## Spring Boot Quick Start

`application.yml`

```yaml
spring:
  datasource:
    mysplitter:
      configuration-file: classpath:mysplitter.yml
```

`mysplitter.yml`

```yaml
mysplitter:
  passwordSource: plain
  transaction:
    mode: local
  databasesRoutingHandler: com.example.DatabaseRouter
  readAndWriteParser: com.example.ReadAndWriteParser
  illAlertHandler: com.example.DataSourceIllAlertHandler
  common:
    dataSourceClass: com.zaxxer.hikari.HikariDataSource
    loadBalance:
      read:
        enabled: true
        strategy: polling
        failTimeout: 30s
      write:
        enabled: true
        strategy: polling
        failTimeout: 30s
  databases:
    database-a:
      readers:
        reader-1:
          configuration:
            jdbcUrl: jdbc:mysql://localhost:3306/user
            username: root
            password: root
            driverClassName: com.mysql.cj.jdbc.Driver
            connectionTimeout: 1000
      writers:
        writer-1:
          configuration:
            jdbcUrl: jdbc:mysql://localhost:3306/user
            username: root
            password: root
            driverClassName: com.mysql.cj.jdbc.Driver
            connectionTimeout: 1000
```

## Configuration Model

- `filters` are executed for every routed SQL, including reused and transaction-pinned routes.
- `transaction.mode: local` is the safe production default and guards against pretending multi-physical-connection local commits are atomic.
- `transaction.mode: xa` is available in `1.1.0` only when `mysplitter.experimental.xa.enabled=true` is set.
- `passwordSource: plain` keeps local YAML passwords explicit for development convenience.
- `passwordSource: environment` resolves datasource passwords from `${ENV_OR_PROPERTY}` placeholders or `passwordEnv`.
- `passwordSource: legacy-rsa` and `enablePasswordEncryption: true` remain compatibility paths only; they are not production-grade secret management.
- `common.loadBalance.read` and `common.loadBalance.write` define default load-balance strategies.
- Each logical database may define either `integrates` or `readers` plus `writers`.
- `classpath:` and `file:` resource locations are supported by the Spring Boot starter.

## Experimental XA Preview

XA mode is intentionally gated. Enable it only for validation and recovery testing:

```powershell
$env:JAVA_TOOL_OPTIONS="-Dmysplitter.experimental.xa.enabled=true"
```

Example configuration shape:

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
```

Current XA capabilities:

- Transaction SPI, embedded coordinator, branch model, and XA datasource adapter.
- Two-phase prepare, commit, and rollback.
- Append-only file transaction log store and durable commit / rollback decisions.
- Recovery executor based on `XAResource.recover(...)`.
- Always-on H2 routed XA lifecycle coverage.
- Docker-gated MySQL and PostgreSQL prepared-branch recovery coverage.

Important limits:

- XA is not the production default in `1.1.0`.
- Every XA node must configure a valid `xaDataSourceClass`.
- PostgreSQL requires `max_prepared_transactions > 0`.
- Multi-process coordinator fencing, admin APIs, metrics, and structured recovery events are still planned work.

## Pool Support

- HikariCP is the first-class documented and regression-validated pool for the current release line.
- Druid, DBCP2, C3P0, BoneCP, and Tomcat JDBC remain compatibility options, not the primary documented baseline.
- Pool-specific configuration keys map directly to the target `DataSource` setters, so HikariCP examples use `jdbcUrl` and `connectionTimeout`.
- See `docs/v1.0-pool-support-policy.md` for support tiers and configuration guidance.

## Documentation

- See `DEVELOPMENT_PLAN.md` for the current roadmap and release progression.
- See `docs/release-notes.md` for release highlights.
- See `docs/v1.0-upgrade-guide.md` for migration guidance from `0.9.x` to `1.0.x`.
- See `docs/v1.0-compatibility-matrix.md` for the runtime, starter, pool, and demo support matrix.
- See `docs/v1.0.3-production-readiness.md` for password-source, YAML-loading, batch, parser, and local transaction hardening notes.
- See `docs/distributed-transaction-roadmap.md` for the XA, AT, TCC, and Saga roadmap.
- See `docs/v1.1-xa-compatibility-matrix.md` and `docs/v1.1-xa-operations.md` for the gated experimental XA MVP status.
- See `demo/README.md` for local demo startup guidance.

## Notes

- The starter publishes both `spring.factories` and `AutoConfiguration.imports`.
- The demo module compiles against Spring Boot `2.7.x`.
- The demo module is a sample application and is not the recommended production baseline.
