#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
adb_bin="$(command -v adb || true)"
if [[ -z "$adb_bin" ]]; then
  sdk_dir=""
  if [[ -f "$repo_root/local.properties" ]]; then
    sdk_dir="$(sed -n 's/^sdk\.dir=//p' "$repo_root/local.properties" | head -n 1)"
  fi
  if [[ -z "$sdk_dir" ]]; then sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"; fi
  if [[ -x "$sdk_dir/platform-tools/adb" ]]; then adb_bin="$sdk_dir/platform-tools/adb"; fi
fi

if ! python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1", 8080)); s.close()' 2>/dev/null; then
  echo "Port 8080 is already in use; stopping before changing adb reverse or opening a server."
  exit 1
fi
adb_serial=""
if [[ -n "$adb_bin" ]]; then
  adb_serial="$("$adb_bin" devices | awk '$2 == "device" { print $1; exit }')"
fi

cleanup() {
  if [[ -n "$adb_serial" ]]; then "$adb_bin" -s "$adb_serial" reverse --remove tcp:8080 >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT INT TERM

if [[ -n "$adb_serial" ]]; then
  "$adb_bin" -s "$adb_serial" reverse tcp:8080 tcp:8080
  echo "Android device $adb_serial can reach this server at localhost:8080."
else
  echo "No connected Android device; browser testing is available, connect one before app testing."
fi

python3 "$repo_root/scripts/roadview/serve.py"
