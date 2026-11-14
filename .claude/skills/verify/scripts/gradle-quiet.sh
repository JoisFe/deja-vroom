#!/usr/bin/env bash
# Run Gradle tasks, keep the full log on disk, print only failures. Exit code is Gradle's.
# Usage: gradle-quiet.sh <task> [task...] [gradle args...]
set -uo pipefail

root="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
cd "$root" || exit 1

if [[ ! -x ./gradlew ]]; then
  echo "FAIL: ./gradlew not found in $root (scaffold first: /pleos-getting-started)" >&2
  exit 127
fi
[[ $# -eq 0 ]] && set -- build

log_dir="$root/build/claude-logs"
mkdir -p "$log_dir"
log="$log_dir/gradle-$(date +%Y%m%d-%H%M%S).log"

./gradlew --console=plain "$@" >"$log" 2>&1
code=$?
ln -sf "$log" "$log_dir/last.log"

if [[ $code -eq 0 ]]; then
  summary=$(grep -E '^(BUILD SUCCESSFUL|[0-9]+ tests? completed)' "$log" | tail -2)
  echo "PASS (exit 0) ${summary//$'\n'/ | }"
else
  echo "FAIL (exit $code) — full log: $log"
  # Compiler errors, test failures, and Gradle's "What went wrong" block, capped.
  grep -nE '(error:|FAILED|Exception|AssertionFailedError|expected:|What went wrong|Could not resolve|> Task .* FAILED)' "$log" \
    | grep -vE '^\s*at (org\.gradle|java\.base|jdk\.internal|org\.junit\.platform)' \
    | head -60
fi
exit $code
