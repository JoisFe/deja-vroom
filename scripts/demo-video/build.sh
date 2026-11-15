#!/usr/bin/env bash
# record -> narrate -> mux into out/deja-vroom-demo.mp4 (+ .srt, + optional burned-in captions).
# Usage: build.sh            DRY_RUN=1 build.sh       SKIP_RECORD=1 build.sh (reuse out/screen.mp4)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="$here/out"
mkdir -p "$out"

[[ "${SKIP_RECORD:-0}" == 1 ]] || "$here/record.sh" "$out"

if [[ "${DRY_RUN:-0}" == 1 ]]; then
  echo "[dry-run] $here/narrate.sh <screen.mp4 duration> $out"
  echo "[dry-run] ffmpeg -i $out/screen.mp4 -i $out/narration.m4a -c:v copy -c:a aac -shortest $out/deja-vroom-demo.mp4"
  [[ "${BURN_CAPTIONS:-0}" == 1 ]] && echo "[dry-run] ffmpeg -i $out/deja-vroom-demo.mp4 -vf subtitles=$out/deja-vroom-demo.srt $out/deja-vroom-demo.captioned.mp4"
  exit 0
fi

command -v ffmpeg >/dev/null || { echo "FAIL: ffmpeg not found. brew install ffmpeg" >&2; exit 1; }
[[ -s "$out/screen.mp4" ]] || { echo "FAIL: $out/screen.mp4 missing. Run record.sh first." >&2; exit 1; }
duration=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$out/screen.mp4")
"$here/narrate.sh" "$duration" "$out"

ffmpeg -loglevel error -y -i "$out/screen.mp4" -i "$out/narration.m4a" \
  -map 0:v -map 1:a -c:v copy -c:a aac -shortest "$out/deja-vroom-demo.mp4"
if [[ "${BURN_CAPTIONS:-0}" == 1 ]]; then
  ffmpeg -loglevel error -y -i "$out/deja-vroom-demo.mp4" \
    -vf "subtitles=$out/deja-vroom-demo.srt:force_style='FontName=Apple SD Gothic Neo,FontSize=22'" \
    -c:a copy "$out/deja-vroom-demo.captioned.mp4"
fi
echo "video: $out/deja-vroom-demo.mp4 ($(printf '%.0f' "$duration")s), captions: $out/deja-vroom-demo.srt"
