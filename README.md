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
  enablePasswordEncryption: true
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
            driverClassName: com.mysql.jdbc.Driver
            connectionTimeout: 1000
      writers:
        writer-1:
          configuration:
            jdbcUrl: jdbc:mysql://localhost:3306/user
            username: root
            password: root
            driverClassName: com.mysql.jdbc.Driver
            connectionTimeout: 1000
```

## Current Configuration Model

- `filters` is supported and executed before opening the target connection.
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
- The starter still targets the legacy `Spring Boot 1.5.x` registration model, while the demo remains on `Spring Boot 2.0.x` until the next migration slice.
- The demo module remains a sample application and is not the recommended production baseline.
