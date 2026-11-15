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
MAX_SECONDS="${MAX_SECONDS:-110}"          # screenrecord limit is 180
# adb cannot type Korean, so the route comes from config/search.yml: the build installed for the
# recording uses these places, and the file is put back right after the install.
ORIGIN="${ORIGIN:-광교중앙역}"
DESTINATION="${DESTINATION:-현대자동차 양재 본사}"
ANALYZE_LABEL="복잡 구간 분석"
OPEN_CARD_LABEL="용인서울고속도로 진출로"   # the card the demo opens (4th of 6 on the default route)
# Coordinates (x y) measured on the 2560x1440 Pleos Connect emulator: fallbacks when a label is not in the
# UI dump, and the buttons tapped directly after the road view ("다음 지점", "목록", "주행 시작"): a UI dump
# stalls 3-6 s while the road view page is loaded.
ANALYZE_XY="${ANALYZE_XY:-1416 943}"
NEXT_ROADVIEW_XY="${NEXT_ROADVIEW_XY:-2090 1210}"
BACK_TO_LIST_XY="${BACK_TO_LIST_XY:-1096 224}"
START_DRIVE_XY="${START_DRIVE_XY:-2255 1209}"
# Card carousel swipe (x1 y x2) to show the later cards, and how wide a card title must be to count as on screen.
CARDS_SWIPE="${CARDS_SWIPE:-2300 700 1300}"
CARD_MIN_WIDTH="${CARD_MIN_WIDTH:-220}"
# -------------------------------------------------------------------------------------

run() { if [[ "${DRY_RUN:-0}" == 1 ]]; then echo "[dry-run] $*"; else "$@"; fi; }
pause() { if [[ "${DRY_RUN:-0}" == 1 ]]; then echo "[dry-run] sleep $1"; else sleep "$1"; fi; }
# Wait until $1 seconds after the recording started, so slow UI dumps do not shift later cues.
at() {
  if [[ "${DRY_RUN:-0}" == 1 ]]; then echo "[dry-run] wait until ${1}s"; return; fi
  local left=$(( $1 - (SECONDS - rec_start) ))
  if (( left > 0 )); then sleep "$left"; fi
}

# Tap the center of the first UI node whose text contains $1 (uiautomator dump), else $2 "x y".
tap_text() {
  local label="$1" fallback="${2:-}"
  if [[ "${DRY_RUN:-0}" == 1 ]]; then echo "[dry-run] tap text '$label' (fallback: ${fallback:-none})"; return; fi
  # A stale dump would match labels from the previous screen; the dump can fail while a WebView animates.
  "$ADB" shell rm -f /sdcard/ui.xml
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  local xy
  xy=$("$ADB" exec-out cat /sdcard/ui.xml 2>/dev/null | python3 -I -c '
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

# Seconds since the recording started, and a log of when things happened on screen. narration.ko.txt can
# start a cue at an event ("@orange1+0.2"), so the voice follows the screen even when loading times vary.
now() { python3 -I -c 'import sys, time; print("%.2f" % (time.time() - float(sys.argv[1])))' "$rec_t0"; }
mark() {
  if [[ "${DRY_RUN:-0}" == 1 ]]; then echo "[dry-run] event $1"; return; fi
  printf '%s\t%s\n' "$1" "$(now)" >> "$out/events.tsv"
}
# Wait until the road view turns orange ("바로 여기": the photo reached the junction), at most $1 s. Checked on
# the photo's left edge, which is orange only then (the card numbers are orange too, so not the banner row).
wait_orange() {
  if [[ "${DRY_RUN:-0}" == 1 ]]; then echo "[dry-run] wait for the orange banner"; return; fi
  local deadline=$(( SECONDS + $1 ))
  while (( SECONDS < deadline )); do
    "$ADB" exec-out screencap | python3 -I -c '
import struct, sys
d = sys.stdin.buffer.read(); w = struct.unpack("<I", d[:4])[0]; n = 0
for y in range(450, 1000, 2):
    for x in range(1010, 1042, 2):
        i = 16 + (y * w + x) * 4; r, g, b = d[i], d[i + 1], d[i + 2]
        n += r > 200 and 100 < g < 175 and b < 90
sys.exit(0 if n > 200 else 1)
' && return
    sleep 0.3
  done
  echo "WARN: no orange banner within $1 s" >&2
}

# Tap the carousel card whose text contains $1. Cards off screen are not in the UI dump, and a card cut
# by the screen edge is narrow there, so swipe toward it until it is fully on screen (at most 6 swipes).
tap_card() {
  local label="$1"
  if [[ "${DRY_RUN:-0}" == 1 ]]; then echo "[dry-run] swipe until card '$label' is on screen, tap it"; return; fi
  local try found
  for try in 1 2 3 4 5 6; do
    "$ADB" shell rm -f /sdcard/ui.xml
    "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
    # "x y width" of the card text, or nothing when it is not on screen.
    found=$("$ADB" exec-out cat /sdcard/ui.xml 2>/dev/null | python3 -I -c '
import re, sys
for m in re.finditer(r"<node [^>]*>", sys.stdin.read()):
    n = m.group(0); t = re.search(r" text=\"([^\"]*)\"", n)
    if t and sys.argv[1] in t.group(1):
        b = list(map(int, re.findall(r"\d+", re.search(r" bounds=\"([^\"]*)\"", n).group(1))))
        print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2, b[2] - b[0]); break
' "$label")
    if [[ -n "$found" ]]; then
      read -r cx cy cw <<< "$found"
      if (( cw >= CARD_MIN_WIDTH )); then "$ADB" shell input tap "$cx" "$((cy - 200))"; return; fi
      # Cut by an edge: nudge it inward.
      if (( cx < 1700 )); then "$ADB" shell input swipe 1300 700 1900 700 500; else "$ADB" shell input swipe 1900 700 1300 700 500; fi
    else
      "$ADB" shell input swipe 2100 700 1300 700 500
    fi
    sleep 1.5
  done
  echo "FAIL: card '$label' not found in the carousel" >&2; exit 1
}

if [[ "${DRY_RUN:-0}" != 1 ]]; then
  "$ADB" get-state >/dev/null 2>&1 || { echo "FAIL: no emulator attached. Start the Pleos emulator first." >&2; exit 1; }
fi
mkdir -p "$out"

search_yml="$repo/config/search.yml"
if [[ "${DRY_RUN:-0}" == 1 ]]; then
  echo "[dry-run] config/search.yml <- origin '$ORIGIN' (source: place), destination '$DESTINATION'; restored after install"
else
  cp "$search_yml" "$out/search.yml.orig"
  trap 'cp "$out/search.yml.orig" "$search_yml"' EXIT
  # Only the two places change; the rest (placeSearch provider) stays as configured.
  python3 -I - "$search_yml" "$ORIGIN" "$DESTINATION" <<'EOF'
import re, sys
path, origin, destination = sys.argv[1:]
text = open(path, encoding="utf-8").read()
text = re.sub(r"(?m)^(origin:\n(?:[ #].*\n)*?)  source: \w+", r"\1  source: place", text)
text = re.sub(r"(?m)^(origin:\n(?:[ #].*\n)*?)  place: .*", lambda m: m.group(1) + "  place: " + origin, text)
text = re.sub(r"(?m)^(destination:\n(?:[ #].*\n)*?)  place: .*", lambda m: m.group(1) + "  place: " + destination, text)
open(path, "w", encoding="utf-8").write(text)
EOF
fi
run "$repo/gradlew" -p "$repo" -q :app:installDebug
[[ "${DRY_RUN:-0}" == 1 ]] || { cp "$out/search.yml.orig" "$search_yml"; trap - EXIT; }
# A trip left over from an earlier run would mark cards as passed; start from a clean app.
# The app runs as the Pleos driver user (10), so clear that user's data, not user 0's.
run "$ADB" shell pm clear --user current "$PACKAGE"
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
rec_start=$SECONDS
rec_t0=$(python3 -I -c "import time; print(time.time())")
: > "$out/events.tsv"

# Timeline below matches the cue start seconds in narration.ko.txt.
echo "origin=$ORIGIN destination=$DESTINATION"
read -r sx sy ex <<< "$CARDS_SWIPE"
at 7                                      # 0-7s: the route
tap_text "$ANALYZE_LABEL" "$ANALYZE_XY"   # 7s: analyze (place search + OSRM + photos: cards by ~15s, photos ~18s)
at 26
run "$ADB" shell input swipe "$sx" "$sy" "$ex" "$sy" 800   # 26-34s: show the later cards
at 30
run "$ADB" shell input swipe "$sx" "$sy" "$ex" "$sy" 800
at 34
tap_card "$OPEN_CARD_LABEL"; mark open1   # open one point; the road view drives up to it (~16s)
pause 3
wait_orange 30; mark orange1              # "바로 여기"
pause 2.6                                 # the orange shows ~5 s in all: wait_orange itself notices it 2-3 s late
run "$ADB" shell input tap $NEXT_ROADVIEW_XY; mark next   # next point, plays the same way (no UI dump: it stalls over the road view)
pause 3
wait_orange 30; mark orange2
pause 1.2
run "$ADB" shell input tap $BACK_TO_LIST_XY; mark back    # back to the cards
pause 2
run "$ADB" shell input tap $START_DRIVE_XY; mark drive     # start driving: the points recapped in route order
pause 11                                  # recap

if [[ "${DRY_RUN:-0}" != 1 ]]; then
  kill -INT "$rec_pid" 2>/dev/null || true
  wait "$rec_pid" 2>/dev/null || true
  sleep 2
fi
run "$ADB" pull /sdcard/deja-vroom-demo.mp4 "$out/screen.mp4"
echo "screen recording: $out/screen.mp4"

# Taps are logged when they happen, but wait_orange notices the orange 2-3 s late (a full screen capture is
# slow). Replace the orange times with the moments found in the video itself.
if [[ "${DRY_RUN:-0}" != 1 ]]; then
  cp "$out/events.tsv" "$out/events.raw.tsv"
  cp "$out/events.raw.tsv" "$out/events.tsv"
  ffmpeg -loglevel error -i "$out/screen.mp4" -vf "fps=10,crop=32:550:1010:450" -f rawvideo -pix_fmt rgb24 - |
    python3 -I -c '
import sys
data = sys.stdin.buffer.read(); size = 32 * 550 * 3; onsets = []; was = False
for k in range(len(data) // size):
    f = data[k * size:(k + 1) * size]
    n = sum(1 for i in range(0, size, 12) if f[i] > 200 and 100 < f[i + 1] < 175 and f[i + 2] < 90)
    if (n > 50) != was:
        was = not was
        if was: onsets.append(k / 10)
events = [line.rstrip("\n").split("\t") for line in open(sys.argv[1], encoding="utf-8") if line.strip()]
marks = [float(t) for name, t in events if name.startswith("orange")]
if len(onsets) != len(marks):
    sys.exit(f"WARN: {len(onsets)} orange moments in the video but {len(marks)} in events; times left as measured")
found = iter(onsets)
times = [next(found) if name.startswith("orange") else float(t) for name, t in events]
with open(sys.argv[1], "w", encoding="utf-8") as out:
    for (name, _), t in zip(events, times): out.write(f"{name}\t{t:.2f}\n")
print("orange moments from the video: " + ", ".join(f"{o:.1f}s" for o in onsets))
' "$out/events.tsv" || true
fi
