#!/usr/bin/env bash
# Turn narration cues into one timed narration track (+ SRT captions + cue timing).
# Usage: narrate.sh <video-duration-seconds|auto> [out-dir]
#   TTS=edge (default): Microsoft neural voice via edge-tts (uv tool install edge-tts), needs network
#   TTS=say: macOS built-in voice, offline
#   CUES=<file>  cue file (default narration.ko.txt). A start of "+" means "right after the previous cue".
#   A start of "@name+2.5" means 2.5 s after event "name" in EVENTS (default out/events.tsv, written by
#   record.sh). If that would overlap the previous cue, the cue waits for it (and a WARN says so).
#   An optional 4th field picks another speaker for that cue: driver (the driver's own words) or navi
#   (a navigation voice through the car speaker). Empty means the narrator.
#   With duration "auto" the track ends TAIL seconds after the last cue.
# Writes <out>/narration.m4a, <out>/<SRT_NAME> and <out>/cues.tsv (index, start, speech end).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
duration="${1:?video duration in seconds, or auto}"
out="${2:-$here/out}"
cues="${CUES:-$here/narration.ko.txt}"
srt_name="${SRT_NAME:-deja-vroom-demo.srt}"
events="${EVENTS:-$here/out/events.tsv}"
engine="${TTS:-edge}"
pack_gap="${PACK_GAP:-0.6}"   # silence between cues placed with "+"
max_gap="${MAX_GAP:-2}"       # silence longer than this between cues is reported
tail_len="${TAIL:-0.8}"

case "$engine" in
  edge)
    edge_tts="${EDGE_TTS:-$(command -v edge-tts || echo "$HOME/.local/bin/edge-tts")}"
    voice="${VOICE:-ko-KR-InJoonNeural}"
    rate="${RATE:-+0%}"
    ext=mp3
    [[ -x "$edge_tts" ]] || { echo "FAIL: edge-tts not found. Install with 'uv tool install edge-tts' or use TTS=say" >&2; exit 1; }
    ;;
  say)
    voice="${VOICE:-Yuna}"
    rate="${RATE:-185}"
    ext=aiff
    command -v say >/dev/null || { echo "FAIL: macOS 'say' not found" >&2; exit 1; }
    say -v '?' | grep -q "^$voice " || { echo "FAIL: voice '$voice' not installed (System Settings > Accessibility > Spoken Content)" >&2; exit 1; }
    ;;
  *) echo "FAIL: TTS must be 'edge' or 'say' (got '$engine')" >&2; exit 1 ;;
esac
command -v ffmpeg >/dev/null || { echo "FAIL: ffmpeg not found. brew install ffmpeg" >&2; exit 1; }

# Each cue is "start|caption[|spoken[|speaker]]"; spoken text exists for words the voice misreads.
starts=(); texts=(); spokens=(); speakers=()
while IFS='|' read -r start text spoken speaker; do
  [[ -z "$start" || "$start" == \#* ]] && continue
  starts+=("$start"); texts+=("$text"); spokens+=("${spoken:-$text}"); speakers+=("${speaker:-}")
done < "$cues"

speak() {  # speak <text> <out-file> <speaker>
  local v="$voice" r="$rate"
  if [[ "$engine" == edge ]]; then
    case "$3" in
      driver) v="${DRIVER_VOICE:-ko-KR-SunHiNeural}"; r="${DRIVER_RATE:-+8%}" ;;
      navi) v="${NAVI_VOICE:-ko-KR-HyunsuMultilingualNeural}"; r="${NAVI_RATE:-+5%}" ;;
    esac
    "$edge_tts" --voice "$v" --rate="$r" --text "$1" --write-media "$2" 2>/dev/null
  else
    case "$3" in driver) v="${DRIVER_VOICE:-Yuna}" ;; navi) v="${NAVI_VOICE:-Yuna}" ;; esac
    say -v "$v" -r "$r" -o "$2" "$1"
  fi
}
# The navigation voice comes out of a car speaker: narrow band, a little quieter.
speaker_fx() { [[ "$1" == navi ]] && echo ",highpass=f=350,lowpass=f=3200,volume=0.8" || true; }

if [[ "${DRY_RUN:-0}" == 1 ]]; then
  for i in "${!starts[@]}"; do echo "[dry-run] $engine (${speakers[$i]:-$voice}, $rate) \"${spokens[$i]}\"  (at ${starts[$i]}s)"; done
  echo "[dry-run] ffmpeg adelay+amix -> $out/narration.m4a, captions -> $out/$srt_name"
  exit 0
fi
mkdir -p "$out/cues"
rm -f "$out"/cues/*

# 1) Synthesize every cue and measure it, 2) resolve "+" starts, 3) write captions and mix.
# Voices pad each file with ~0.7s of silence at both ends; trim it so gaps and lengths are what you hear.
trim="silenceremove=start_periods=1:start_threshold=-45dB,areverse,silenceremove=start_periods=1:start_threshold=-45dB,areverse"
lens=()
for i in "${!starts[@]}"; do
  speak "${spokens[$i]}" "$out/cues/$i.raw.$ext" "${speakers[$i]}"
  ffmpeg -loglevel error -y -i "$out/cues/$i.raw.$ext" -af "$trim$(speaker_fx "${speakers[$i]}")" -ar 48000 "$out/cues/$i.wav"
  lens+=("$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$out/cues/$i.wav")")
done
prev_end=0
for i in "${!starts[@]}"; do
  if [[ "${starts[$i]}" == "+" ]]; then
    starts[$i]=$(awk -v e="$prev_end" -v g="$pack_gap" -v i="$i" 'BEGIN{print (i==0) ? 0 : e+g}')
  elif [[ "${starts[$i]}" == @* ]]; then
    spec="${starts[$i]#@}"; event="${spec%%[+-]*}"; shift_by="${spec#"$event"}"
    at=$(awk -F'\t' -v n="$event" '$1 == n {print $2; exit}' "$events" 2>/dev/null || true)
    [[ -n "$at" ]] || { echo "FAIL: event '$event' (cue $i) not in $events. Run record.sh first." >&2; exit 1; }
    starts[$i]=$(awk -v a="$at" -v d="${shift_by:-0}" 'BEGIN{printf "%.2f", a+d}')
    if awk -v s="${starts[$i]}" -v e="$prev_end" 'BEGIN{exit !(s<e+0.3)}'; then
      echo "WARN: cue $i (@$spec = ${starts[$i]}s) waits for the previous cue to end at ${prev_end}s" >&2
      starts[$i]=$(awk -v e="$prev_end" 'BEGIN{printf "%.2f", e+0.3}')
    fi
  fi
  prev_end=$(awk -v s="${starts[$i]}" -v l="${lens[$i]}" 'BEGIN{print s+l}')
done
if [[ "$duration" == auto ]]; then
  duration=$(awk -v e="$prev_end" -v t="$tail_len" 'BEGIN{print e+t}')
fi

ts() { awk -v t="$1" 'BEGIN{h=int(t/3600);m=int((t%3600)/60);s=int(t%60);ms=int((t-int(t))*1000);printf "%02d:%02d:%02d,%03d",h,m,s,ms}'; }

srt="$out/$srt_name"; : > "$srt"
timing="$out/cues.tsv"; : > "$timing"
inputs=(); filters=""; labels=""
for i in "${!starts[@]}"; do
  start="${starts[$i]}"; len="${lens[$i]}"
  next="${starts[$((i+1))]:-$duration}"
  end=$(awk -v s="$start" -v l="$len" -v n="$next" 'BEGIN{e=s+l+0.3; if(e>n) e=n; print e}')
  if awk -v s="$start" -v l="$len" -v n="$next" 'BEGIN{exit !(s+l>n)}'; then
    echo "WARN: cue $i (${start}s) is ${len}s long and overlaps the next cue at ${next}s" >&2
  elif (( i + 1 < ${#starts[@]} )) && awk -v s="$start" -v l="$len" -v n="$next" -v m="$max_gap" 'BEGIN{exit !(n-(s+l)>m)}'; then
    echo "WARN: $(awk -v s="$start" -v l="$len" -v n="$next" 'BEGIN{printf "%.1f", n-(s+l)}')s of silence after cue $i (${start}s); lengthen it or move the next cue earlier" >&2
  fi
  printf '%d\n%s --> %s\n%s\n\n' "$((i+1))" "$(ts "$start")" "$(ts "$end")" "${texts[$i]}" >> "$srt"
  printf '%d\t%s\t%s\n' "$i" "$start" "$(awk -v s="$start" -v l="$len" 'BEGIN{print s+l}')" >> "$timing"
  inputs+=(-i "$out/cues/$i.wav")
  ms=$(awk -v s="$start" 'BEGIN{printf "%d", s*1000}')
  filters+="[$i:a]adelay=${ms}|${ms}[a$i];"
  labels+="[a$i]"
done

ffmpeg -loglevel error -y "${inputs[@]}" \
  -filter_complex "${filters}${labels}amix=inputs=${#starts[@]}:normalize=0,apad=whole_dur=${duration}[out]" \
  -map "[out]" -t "$duration" -ac 2 -ar 48000 "$out/narration.m4a"
echo "narration ($engine, $voice, ${duration}s): $out/narration.m4a, captions: $srt"
