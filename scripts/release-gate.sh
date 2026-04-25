#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
maven_wrapper="${repo_root}/mvnw"

run_step() {
  local description="$1"
  shift
  echo "==> ${description}"
  "${maven_wrapper}" "$@"
}

run_step "Validate core module" -q -pl mysplitter verify
run_step "Validate starter module" -q -pl mysplitter-spring-boot-starter -am test
run_step "Run regression suite" -q -pl mysplitter-tests -am test
run_step "Compile demo consumer" -q -pl demo -am -Dmaven.test.skip=true clean compile
run_step "Package release artifacts" -q -pl mysplitter,mysplitter-spring-boot-starter -am -Dmaven.test.skip=true package
