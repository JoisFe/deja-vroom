#!/usr/bin/env bash
# record -> narrate -> cold open + intro + app + closing -> captions.
# Usage: build.sh            DRY_RUN=1 build.sh       SKIP_RECORD=1 build.sh (reuse out/screen.mp4)
#   INTRO=0          app part only: no cold open (coldopen.sh), intro (intro.sh) or closing (NAME=next intro.sh)
#   BURN_CAPTIONS=0  skip the captioned copy (needs an ffmpeg with libass: brew install ffmpeg-full)
# Output: out/deja-vroom-demo.mp4, out/deja-vroom-demo.srt, out/deja-vroom-demo.captioned.mp4
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="$here/out"
mkdir -p "$out"
ffmpeg_burn="${FFMPEG_BURN:-/opt/homebrew/opt/ffmpeg-full/bin/ffmpeg}"
caption_style="FontName=Apple SD Gothic Neo,FontSize=13,BorderStyle=3,Outline=1,Shadow=0,BackColour=&H80000000,MarginV=18"

[[ "${SKIP_RECORD:-0}" == 1 ]] || "$here/record.sh" "$out"

if [[ "${DRY_RUN:-0}" == 1 ]]; then
  echo "[dry-run] $here/narrate.sh <screen.mp4 duration> $out/app"
  echo "[dry-run] trim the app part to 1.2s after the last cue -> $out/app.mp4"
  [[ "${INTRO:-1}" == 1 ]] && echo "[dry-run] $here/slides.sh $out; $here/coldopen.sh $out; $here/intro.sh $out; NAME=next $here/intro.sh $out; join coldopen + intro + app + next"
  [[ "${BURN_CAPTIONS:-1}" == 1 ]] && echo "[dry-run] $ffmpeg_burn -vf subtitles=deja-vroom-demo.srt -> deja-vroom-demo.captioned.mp4"
  exit 0
fi

command -v ffmpeg >/dev/null || { echo "FAIL: ffmpeg not found. brew install ffmpeg" >&2; exit 1; }
[[ -s "$out/screen.mp4" ]] || { echo "FAIL: $out/screen.mp4 missing. Run record.sh first." >&2; exit 1; }

# ---- App part: narration on the screen recording, trimmed shortly after the last cue -------------
duration=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$out/screen.mp4")
"$here/narrate.sh" "$duration" "$out/app"
app_len=$(awk -v d="$duration" -v e="$(tail -1 "$out/app/cues.tsv" | cut -f3)" 'BEGIN{t=e+1.2; print (t<d) ? t : d}')
ffmpeg -loglevel error -y -i "$out/screen.mp4" -i "$out/app/narration.m4a" -t "$app_len" \
  -map 0:v -map 1:a -vf "fps=30,format=yuv420p,setsar=1" -c:v libx264 -preset veryfast -crf 20 -c:a aac -b:a 192k \
  "$out/app.mp4"

# ---- Cold open + intro + app + closing -> final video and merged captions -----------------------
if [[ "${INTRO:-1}" == 1 ]]; then
  "$here/slides.sh" "$out"
  "$here/coldopen.sh" "$out"
  "$here/intro.sh" "$out"
  NAME=next "$here/intro.sh" "$out"
  parts=(coldopen intro app next)
  inputs=(); joins=""; srt_parts=(); offset=0
  for i in "${!parts[@]}"; do
    inputs+=(-i "$out/${parts[$i]}.mp4"); joins+="[$i:v][$i:a]"
    srt="$out/${parts[$i]}.srt"; [[ "${parts[$i]}" == app ]] && srt="$out/app/deja-vroom-demo.srt"
    srt_parts+=("$srt:$offset")
    offset=$(awk -v o="$offset" -v d="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$out/${parts[$i]}.mp4")" 'BEGIN{print o+d}')
  done
  ffmpeg -loglevel error -y "${inputs[@]}" \
    -filter_complex "${joins}concat=n=${#parts[@]}:v=1:a=1[v][a]" -map "[v]" -map "[a]" \
    -c:v libx264 -preset veryfast -crf 20 -pix_fmt yuv420p -c:a aac -b:a 192k "$out/deja-vroom-demo.mp4"
else
  cp "$out/app.mp4" "$out/deja-vroom-demo.mp4"
  srt_parts=("$out/app/deja-vroom-demo.srt:0")
fi
python3 -I - "$out/deja-vroom-demo.srt" "${srt_parts[@]}" <<'EOF'
import re, sys
def sec(t):
    h, m, s = t.replace(",", ".").split(":"); return int(h) * 3600 + int(m) * 60 + float(s)
def fmt(t):
    ms = round(t * 1000); return "%02d:%02d:%02d,%03d" % (ms // 3600000, ms // 60000 % 60, ms // 1000 % 60, ms % 1000)
blocks = []
for part in sys.argv[2:]:
    path, offset = part.rsplit(":", 1)
    for block in open(path, encoding="utf-8").read().strip().split("\n\n"):
        lines = block.splitlines()
        a, b = (sec(x.strip()) + float(offset) for x in lines[1].split("-->"))
        blocks.append((a, b, "\n".join(lines[2:])))
with open(sys.argv[1], "w", encoding="utf-8") as f:
    for i, (a, b, text) in enumerate(blocks, 1):
        f.write(f"{i}\n{fmt(a)} --> {fmt(b)}\n{text}\n\n")
EOF

if [[ "${BURN_CAPTIONS:-1}" == 1 ]]; then
  # Not grep -q: it exits early, ffmpeg gets SIGPIPE, and pipefail turns the match into a failure.
  if "$ffmpeg_burn" -hide_banner -filters 2>/dev/null | grep " subtitles " >/dev/null; then
    (cd "$out" && "$ffmpeg_burn" -loglevel error -y -i deja-vroom-demo.mp4 \
      -vf "subtitles=deja-vroom-demo.srt:force_style='$caption_style'" \
      -c:v libx264 -preset veryfast -crf 20 -c:a copy deja-vroom-demo.captioned.mp4)
  else
    echo "WARN: $ffmpeg_burn has no subtitles filter; skipped the captioned copy (brew install ffmpeg-full)" >&2
  fi
fi
total=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$out/deja-vroom-demo.mp4")
echo "video: $out/deja-vroom-demo.mp4 ($(printf '%.0f' "$total")s), captions: $out/deja-vroom-demo.srt"
[[ -s "$out/deja-vroom-demo.captioned.mp4" ]] && echo "captioned: $out/deja-vroom-demo.captioned.mp4"
