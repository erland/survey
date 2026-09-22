#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
blocked=0

printf '\n== Frontend ==\n'
if command -v npm >/dev/null 2>&1; then
  cd "$ROOT/frontend"
  if [[ -f package-lock.json ]]; then
    npm ci --no-audit --no-fund
  else
    npm install --no-audit --no-fund
  fi
  npm run build
  npm test
else
  echo "BLOCKED: npm is not installed"
  blocked=1
fi

printf '\n== Backend ==\n'
if command -v mvn >/dev/null 2>&1; then
  cd "$ROOT/backend"
  mvn -B test
  mvn -B package -DskipTests
else
  echo "BLOCKED: Maven 3.9+ is not installed"
  blocked=1
fi

printf '\n== Compose syntax ==\n'
if command -v docker >/dev/null 2>&1; then
  cd "$ROOT"
  docker compose config >/dev/null
  echo "PASS: docker compose config"
elif command -v podman >/dev/null 2>&1 && podman compose version >/dev/null 2>&1; then
  cd "$ROOT"
  podman compose config >/dev/null
  echo "PASS: podman compose config"
else
  echo "BLOCKED: neither Docker Compose nor Podman Compose is available"
  blocked=1
fi

if [[ "$blocked" -ne 0 ]]; then
  printf '\nBootstrap verification is BLOCKED because required local tooling is unavailable.\n'
  exit 2
fi

printf '\nBootstrap verification PASSED.\n'
