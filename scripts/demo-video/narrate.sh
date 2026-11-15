#!/usr/bin/env bash
# Turn narration.ko.txt cues into one timed narration track (+ SRT captions).
# Usage: narrate.sh <video-duration-seconds> [out-dir]
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
duration="${1:?video duration in seconds}"
out="${2:-$here/out}"
cues="${CUES:-$here/narration.ko.txt}"
voice="${VOICE:-Yuna}"
rate="${RATE:-185}"

command -v say >/dev/null || { echo "FAIL: macOS 'say' not found" >&2; exit 1; }
say -v '?' | grep -q "^$voice " || { echo "FAIL: voice '$voice' not installed (System Settings > Accessibility > Spoken Content)" >&2; exit 1; }
if [[ "${DRY_RUN:-0}" == 1 ]]; then
  grep -v '^#' "$cues" | grep . | while IFS='|' read -r start text; do echo "[dry-run] say -v $voice -r $rate -o cue.aiff \"$text\"  (at ${start}s)"; done
  echo "[dry-run] ffmpeg adelay+amix -> $out/narration.m4a, captions -> $out/deja-vroom-demo.srt"
  exit 0
fi
mkdir -p "$out/cues"
rm -f "$out"/cues/*

srt="$out/deja-vroom-demo.srt"; : > "$srt"
starts=(); texts=()
while IFS='|' read -r start text; do
  [[ -z "$start" || "$start" == \#* ]] && continue
  starts+=("$start"); texts+=("$text")
done < "$cues"

ts() { awk -v t="$1" 'BEGIN{h=int(t/3600);m=int((t%3600)/60);s=int(t%60);ms=int((t-int(t))*1000);printf "%02d:%02d:%02d,%03d",h,m,s,ms}'; }

inputs=(); filters=""; labels=""
for i in "${!starts[@]}"; do
  f="$out/cues/$i.aiff"
  say -v "$voice" -r "$rate" -o "$f" "${texts[$i]}"
  len=$(afinfo "$f" | awk '/estimated duration/{print $3}')
  start="${starts[$i]}"
  next="${starts[$((i+1))]:-$duration}"
  end=$(awk -v s="$start" -v l="$len" -v n="$next" 'BEGIN{e=s+l+0.3; if(e>n) e=n; print e}')
  if awk -v s="$start" -v l="$len" -v n="$next" 'BEGIN{exit !(s+l>n)}'; then
    echo "WARN: cue $i (${start}s) is ${len}s long and overlaps the next cue at ${next}s" >&2
  fi
  printf '%d\n%s --> %s\n%s\n\n' "$((i+1))" "$(ts "$start")" "$(ts "$end")" "${texts[$i]}" >> "$srt"
  inputs+=(-i "$f")
  ms=$(awk -v s="$start" 'BEGIN{printf "%d", s*1000}')
  filters+="[$i:a]adelay=${ms}|${ms}[a$i];"
  labels+="[a$i]"
done

if ! command -v ffmpeg >/dev/null; then
  echo "ffmpeg not found: per-cue audio is in $out/cues. Install with 'brew install ffmpeg' to mix." >&2
  exit 2
fi
ffmpeg -loglevel error -y "${inputs[@]}" \
  -filter_complex "${filters}${labels}amix=inputs=${#starts[@]}:normalize=0,apad=whole_dur=${duration}[out]" \
  -map "[out]" -t "$duration" -ac 2 -ar 48000 "$out/narration.m4a"
echo "narration: $out/narration.m4a, captions: $srt"
