# Verification Commands

Verified on 2026-04-25 for the current `vibe-coding` branch state.

## Canonical Release Gate

Use this as the official release-signoff entry point from the repo root:

```powershell
.\scripts\release-gate.ps1
```

On Unix-like shells:

```bash
./scripts/release-gate.sh
```

Notes:

- This is the canonical command for release validation because it executes the core-module verify path, starter tests, the dedicated `mysplitter-tests` regression module, demo compile smoke checks, and release packaging in one fail-fast sequence.
- Module-level commands below are still useful for fast feedback, but they are no longer presented as a substitute for the full release gate.

## Core Module

Run the fast module-level checks for the core library:

```powershell
.\mvnw.cmd -q -pl mysplitter test
.\mvnw.cmd -q -pl mysplitter verify
```

Notes:

- `mysplitter` keeps reflection-based module tests so `mvn -pl mysplitter test` is not false-green for module-local regressions.
- These checks are the fastest way to validate connection lifecycle and health-manager regressions inside the core module.
- They do not replace the canonical release gate because they do not execute the separate `mysplitter-tests` regression module.

## Regression Suite

Run the dedicated regression module for routing, selector, transaction, and health-manager coverage:

```powershell
.\mvnw.cmd -q -pl mysplitter-tests -am test
```

Notes:

- This command builds `mysplitter` first and then executes the extracted regression suite.
- The suite now includes an always-on H2 routing integration path, so reader routing and transactional writer pinning are validated even when Docker is unavailable.
- The Docker-backed MySQL Testcontainers slice remains a supplemental path and still skips cleanly when Docker is unavailable.

## Starter Module

Run the starter-module bootstrap checks that lock in configuration resource loading behavior:

```powershell
.\mvnw.cmd -q -pl mysplitter-spring-boot-starter -am test
```

Notes:

- Use `-am` from the repo root so Maven also builds the local `mysplitter` dependency for the starter module.
- These tests validate classpath resource loading, file resource loading, blank-path normalization, and missing-resource failures in the starter.
- They also verify the Boot `2.7.x` dual-registration metadata (`spring.factories` plus `AutoConfiguration.imports`).
- They are the fastest way to catch regressions in the starter bootstrap path before doing downstream compile checks.

## Release Packaging

Use this command to confirm the core library and starter still package cleanly for release:

```powershell
.\mvnw.cmd -q -pl mysplitter,mysplitter-spring-boot-starter -am "-Dmaven.test.skip=true" package
```

## Compile-Only Checks

Use these commands to confirm downstream modules still compile against the current reactor:

```powershell
.\mvnw.cmd -q -pl mysplitter-spring-boot-starter -am "-Dmaven.test.skip=true" clean compile
.\mvnw.cmd -q -pl demo -am "-Dmaven.test.skip=true" clean compile
```

Notes:

- In PowerShell, quote `"-Dmaven.test.skip=true"` so Maven receives it as one argument.
- These commands are intended as compatibility smoke checks, not as full runtime validation.
- The current reactor now validates directly from a cold `clean compile` state, so install-first smoke checks are no longer required for starter or demo.
