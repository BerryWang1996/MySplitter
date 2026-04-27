# MySplitter

MySplitter is a lightweight JDBC middleware for:

- read/write splitting
- multi-database routing
- datasource failover
- load balancing through `DataSource` / `Connection` / `Statement` proxies

## Modules

- `mysplitter`: core library
- `mysplitter-spring-boot-starter`: Spring Boot integration
- `demo`: runnable sample application

## Build

This repository now includes Maven Wrapper, so a local Maven installation is not required.

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

The release gate runs core verification, starter tests, the dedicated regression module, demo compile checks, and release packaging.

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

## Current Configuration Model

- `filters` is supported and executed for every routed SQL, including reused and transaction-pinned routes.
- `transaction.mode: local` is the production default. Experimental XA support is under active `1.1.0-SNAPSHOT` development and remains gated by `mysplitter.experimental.xa.enabled=true`.
- `passwordSource: plain` keeps local YAML passwords explicit for development convenience.
- `passwordSource: environment` resolves datasource passwords from `${ENV_OR_PROPERTY}` placeholders or `passwordEnv`.
- `enablePasswordEncryption: true` is still accepted as legacy RSA compatibility mode, but it is not production-grade secret management.
- `common.loadBalance.read` and `common.loadBalance.write` define the default load-balance strategy.
- Each database may define either:
  - `integrates`
  - or `readers` plus `writers`
- `classpath:` and `file:` resource locations are supported by the Spring Boot starter.

## Pool Support

- `HikariCP` is the first-class pool for the current `v1.0.0` release baseline.
- `Druid`, `DBCP2`, `C3P0`, `BoneCP`, and `Tomcat JDBC` remain compatibility options, not the primary documented baseline.
- Pool-specific configuration keys still map directly to the target `DataSource` setters, so HikariCP examples use `jdbcUrl` and `connectionTimeout`.
- See `docs/v1.0-pool-support-policy.md` for the current support tiers and configuration guidance.

## Notes

- The current release baseline is `Java 8`.
- The starter release baseline now targets `Spring Boot 2.7.x` and publishes both `spring.factories` and `AutoConfiguration.imports`.
- The demo module now compiles against `Spring Boot 2.7.x`.
- See `docs/release-notes.md` for release highlights.
- See `docs/v1.0-upgrade-guide.md` for migration guidance from `0.9.x` to `1.0.x`.
- See `docs/v1.0-compatibility-matrix.md` for the current runtime, starter, pool, and demo support matrix.
- See `docs/v1.0.3-production-readiness.md` for password-source and YAML-loading hardening notes.
- See `docs/v1.1-xa-compatibility-matrix.md` and `docs/v1.1-xa-operations.md` for the gated experimental XA MVP status.
- See `demo/README.md` for local demo startup guidance.
- The demo module remains a sample application and is not the recommended production baseline.
