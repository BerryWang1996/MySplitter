# MySplitter Demo

This module is a Spring Boot `2.7.18` sample application that demonstrates:

- Spring Boot starter auto-configuration
- HikariCP datasource configuration
- multi-database routing with SQL prefixes
- read/write parsing
- transaction rollback across routed operations
- datasource health status exposure

The demo is intentionally small. It is useful for acceptance checks and local exploration, but it is not a production application template.

## Prerequisites

- Java 8+
- A local MySQL-compatible server
- Two schemas:
  - `user`
  - `dept`

Create the sample tables:

```sql
CREATE DATABASE IF NOT EXISTS user;
CREATE DATABASE IF NOT EXISTS dept;

CREATE TABLE IF NOT EXISTS user.user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64),
  age INT
);

CREATE TABLE IF NOT EXISTS dept.dept (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64)
);
```

## Configuration

The demo loads MySplitter configuration from:

- `demo/src/main/resources/application.yml`
- `demo/src/main/resources/mysplitter.yml`

The committed `mysplitter.yml` is an example configuration. Before running locally, update these fields for each datasource node:

- `jdbcUrl`
- `username`
- `password`
- `publicKey`, if password encryption remains enabled

For a quick local-only run, you can also disable password encryption and use a plain password:

```yaml
mysplitter:
  enablePasswordEncryption: false
```

Then remove the `publicKey` entries and set each `password` to the local MySQL password.

## Build

From the repository root:

```powershell
.\mvnw.cmd -q -pl demo -am "-Dmaven.test.skip=true" clean compile
```

On Unix-like shells:

```bash
./mvnw -q -pl demo -am -Dmaven.test.skip=true clean compile
```

## Run

From the repository root:

```powershell
.\mvnw.cmd -q -pl mysplitter,mysplitter-spring-boot-starter -am "-Dmaven.test.skip=true" install
.\mvnw.cmd -pl demo spring-boot:run
```

On Unix-like shells:

```bash
./mvnw -q -pl mysplitter,mysplitter-spring-boot-starter -am -Dmaven.test.skip=true install
./mvnw -pl demo spring-boot:run
```

The application starts on Spring Boot's default port `8080` unless overridden.

## Endpoints

```text
GET /status
GET /user/save?name=Alice&age=20
GET /user/list
GET /dept/save?name=Engineering
GET /exception?id=0
GET /exception?id=1
GET /exception?id=2
```

Endpoint notes:

- `/status` returns MySplitter datasource health state.
- `/user/save` writes to `database-a`.
- `/user/list` reads from `database-a`.
- `/dept/save` writes to `database-b`.
- `/exception?id=0`, `/exception?id=1`, and `/exception?id=2` intentionally throw at different points in a transaction to exercise rollback behavior.

## Routing Markers

The demo mappers use SQL prefixes to choose the logical database:

```sql
[database-a] SELECT * FROM user
[database-b] INSERT INTO dept(name) VALUES(...)
```

`DatabaseRouter` strips the prefix before SQL reaches the physical database.

