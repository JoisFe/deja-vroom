#!/usr/bin/env bash
# Install the debug app on a running emulator, drive the demo flow, and record the screen.
# Usage: record.sh [out-dir]      DRY_RUN=1 record.sh  (print steps only)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
out="${1:-$here/out}"

# ---- config: everything that depends on the current UI lives here -----------------
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
PACKAGE="ai.dejavroom.app"
ACTIVITY="$PACKAGE/.MainActivity"
MAX_SECONDS="${MAX_SECONDS:-75}"          # screenrecord limit is 180
ORIGIN="${ORIGIN:-수원시 영통구}"          # app default; left as is
DESTINATION="${DESTINATION:-강남역}"       # app default; left as is
ANALYZE_LABEL="복잡 구간 분석"
START_DRIVE_LABEL="주행 시작"
ROUTE_WAIT_SECONDS=15                      # Nominatim + OSRM round trip
# Fallback coordinates (x y) when a label is not found in the UI dump; tune per screen size.
ANALYZE_XY="${ANALYZE_XY:-}"
START_DRIVE_XY="${START_DRIVE_XY:-}"
# -------------------------------------------------------------------------------------

run() { if [[ "${DRY_RUN:-0}" == 1 ]]; then echo "[dry-run] $*"; else "$@"; fi; }
pause() { if [[ "${DRY_RUN:-0}" == 1 ]]; then echo "[dry-run] sleep $1"; else sleep "$1"; fi; }

# Tap the center of the first UI node whose text contains $1 (uiautomator dump), else $2 "x y".
tap_text() {
  local label="$1" fallback="${2:-}"
  if [[ "${DRY_RUN:-0}" == 1 ]]; then echo "[dry-run] tap text '$label' (fallback: ${fallback:-none})"; return; fi
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null
  local xy
  xy=$("$ADB" exec-out cat /sdcard/ui.xml | python3 -I -c '
import re, sys
label = sys.argv[1]
for m in re.finditer(r"<node [^>]*>", sys.stdin.read()):
    n = m.group(0)
    t = re.search(r" text=\"([^\"]*)\"", n); d = re.search(r" content-desc=\"([^\"]*)\"", n)
    if label in (t.group(1) if t else "") or label in (d.group(1) if d else ""):
        b = list(map(int, re.findall(r"\d+", re.search(r" bounds=\"([^\"]*)\"", n).group(1))))
        print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2); break
' "$label")
  if [[ -z "$xy" ]]; then
    [[ -n "$fallback" ]] || { echo "FAIL: '$label' not on screen and no fallback coordinates" >&2; exit 1; }
    xy="$fallback"
  fi
  # shellcheck disable=SC2086
  "$ADB" shell input tap $xy
}

if [[ "${DRY_RUN:-0}" != 1 ]]; then
  "$ADB" get-state >/dev/null 2>&1 || { echo "FAIL: no emulator attached. Start the Pleos emulator first." >&2; exit 1; }
fi
mkdir -p "$out"

run "$repo/gradlew" -p "$repo" -q :app:installDebug
run "$ADB" shell am force-stop "$PACKAGE"
run "$ADB" shell am start -W -n "$ACTIVITY"
pause 3

run "$ADB" shell rm -f /sdcard/deja-vroom-demo.mp4
if [[ "${DRY_RUN:-0}" == 1 ]]; then
  echo "[dry-run] adb shell screenrecord --time-limit $MAX_SECONDS /sdcard/deja-vroom-demo.mp4 &"
else
  "$ADB" shell screenrecord --time-limit "$MAX_SECONDS" /sdcard/deja-vroom-demo.mp4 &
  rec_pid=$!
fi

# Timeline below matches the cue start seconds in narration.ko.txt.
pause 7                                   # 0-7s: intro on the destination screen
echo "origin=$ORIGIN destination=$DESTINATION (app defaults)"
pause 11                                  # 7-18s: show the inputs
tap_text "$ANALYZE_LABEL" "$ANALYZE_XY"   # 18s: analyze
pause "$ROUTE_WAIT_SECONDS"               # 18-33s: route + scoring
pause 10                                  # 30-42s: candidate cards
run "$ADB" shell input swipe 900 700 900 300 600   # 42s: scroll the cards (compact layout)
pause 10
tap_text "$START_DRIVE_LABEL" "$START_DRIVE_XY"    # 52s: start driving
pause 12                                  # 52-64s: navigation screen + outro

if [[ "${DRY_RUN:-0}" != 1 ]]; then
  kill -INT "$rec_pid" 2>/dev/null || true
  wait "$rec_pid" 2>/dev/null || true
  sleep 2
fi
run "$ADB" pull /sdcard/deja-vroom-demo.mp4 "$out/screen.mp4"
echo "screen recording: $out/screen.mp4"
