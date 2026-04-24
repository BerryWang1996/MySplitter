# Verification Commands

Verified on 2026-04-24 for the current `vibe-coding` branch state.

## Core Module

Run the fast module-level checks for the core library:

```powershell
.\mvnw.cmd -q -pl mysplitter test
.\mvnw.cmd -q -pl mysplitter verify
```

Notes:

- `mysplitter` keeps reflection-based module tests so `mvn -pl mysplitter test` is not false-green.
- These checks are the fastest way to validate connection lifecycle and health-manager regressions inside the core module.

## Regression Suite

Run the dedicated regression module for routing, selector, transaction, and health-manager coverage:

```powershell
.\mvnw.cmd -q -pl mysplitter-tests -am test
```

Notes:

- This command builds `mysplitter` first and then executes the extracted regression suite.
- The Testcontainers slice skips cleanly when Docker is unavailable.

## Compile-Only Checks

Use these commands to confirm downstream modules still compile against the current reactor:

```powershell
.\mvnw.cmd -q "-Dmaven.test.skip=true" -pl mysplitter-spring-boot-starter -am compile
.\mvnw.cmd -q "-Dmaven.test.skip=true" -pl demo -am compile
```

Notes:

- In PowerShell, quote `"-Dmaven.test.skip=true"` so Maven receives it as one argument.
- These commands are intended as compatibility smoke checks, not as full runtime validation.
