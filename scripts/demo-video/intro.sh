#!/usr/bin/env bash
# Build a narrated segment: one scene per narration cue, cut to the cue's length. A scene is a Wikimedia
# Commons clip, or "slide:<id>" for a slide rendered by slides.sh (out/slides/<id>.png).
# Usage: intro.sh [out-dir]        NAME=next intro.sh [out-dir]   (default NAME=intro)
# Reads <NAME>.ko.txt and <NAME>.clips.txt. Output: <out>/<NAME>.mp4 (+ <NAME>.srt, credits in clips/credits.tsv)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="${1:-$here/out}"
name="${NAME:-intro}"
work="$out/$name"; clips="$out/clips"
W=2560; H=1440; FPS=30
mkdir -p "$work" "$clips"

# ---- 1. Fetch clips (cached by file name) and record their license ------------------
clip_names=(); clip_ins=(); clip_crops=(); clip_ends=()
while IFS='|' read -r file in crop end; do
  [[ -z "$file" || "$file" == \#* ]] && continue
  clip_names+=("$file"); clip_ins+=("${in:-0}"); clip_crops+=("${crop:-0}"); clip_ends+=("${end:-}")
done < "$here/$name.clips.txt"
commons=(); for file in "${clip_names[@]}"; do [[ "$file" == slide:* ]] || commons+=("$file"); done
(( ${#commons[@]} == 0 )) || python3 -I "$here/fetch-commons.py" "$clips" "${commons[@]}"

# ---- 2. Narration with packed timing ---------------------------------------------------
CUES="$here/$name.ko.txt" SRT_NAME="$name.srt" "$here/narrate.sh" auto "$work"
total=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$work/narration.m4a")

# ---- 3. One video segment per cue: from this cue's start to the next cue's start --------
starts=(); while IFS=$'\t' read -r _ start _; do starts+=("$start"); done < "$work/cues.tsv"
(( ${#starts[@]} == ${#clip_names[@]} )) || { echo "FAIL: ${#starts[@]} cues but ${#clip_names[@]} clips" >&2; exit 1; }
: > "$work/segments.txt"
for i in "${!starts[@]}"; do
  from=$([[ $i == 0 ]] && echo 0 || echo "${starts[$i]}")
  to="${starts[$((i+1))]:-$total}"
  seg=$(awk -v a="$from" -v b="$to" 'BEGIN{printf "%.3f", b-a}')
  if [[ "${clip_names[$i]}" == slide:* ]]; then
    slide="$out/slides/${clip_names[$i]#slide:}.png"
    [[ -s "$slide" ]] || { echo "FAIL: $slide missing. Run slides.sh first." >&2; exit 1; }
    # Text stays sharp without a zoom; fade in only when the slide changes.
    fade=""; [[ $i -gt 0 && "${clip_names[$((i-1))]}" == "${clip_names[$i]}" ]] || fade=",fade=t=in:st=0:d=0.35"
    ffmpeg -loglevel error -y -loop 1 -t "$seg" -i "$slide" \
      -vf "scale=$W:$H,setsar=1,fps=$FPS$fade,format=yuv420p" -t "$seg" -c:v libx264 -preset veryfast -crf 18 "$work/seg$i.mp4"
    echo "file 'seg$i.mp4'" >> "$work/segments.txt"
    continue
  fi
  src="$clips/${clip_names[$i]// /_}.media"
  crop="${clip_crops[$i]}"
  # With an end time, read only [in, end] and stretch it to the segment so the next shot never shows.
  read_len="$seg"; stretch=1
  if [[ -n "${clip_ends[$i]}" ]]; then
    read_len=$(awk -v a="${clip_ins[$i]}" -v b="${clip_ends[$i]}" 'BEGIN{print b-a}')
    stretch=$(awk -v s="$seg" -v r="$read_len" 'BEGIN{print (s>r) ? s/r : 1}')
  fi
  # Crop the requested top/bottom band, fill 16:9, and hold the last frame if the clip runs out.
  ffmpeg -loglevel error -y -ss "${clip_ins[$i]}" -t "$read_len" -i "$src" -an \
    -vf "setpts=$stretch*(PTS-STARTPTS),crop=iw:ih*(1-2*$crop):0:ih*$crop,scale=$W:$H:force_original_aspect_ratio=increase,crop=$W:$H,setsar=1,fps=$FPS,tpad=stop_mode=clone:stop_duration=$seg" \
    -t "$seg" -c:v libx264 -preset veryfast -crf 20 -pix_fmt yuv420p "$work/seg$i.mp4"
  echo "file 'seg$i.mp4'" >> "$work/segments.txt"
done

ffmpeg -loglevel error -y -f concat -safe 0 -i "$work/segments.txt" -i "$work/narration.m4a" \
  -map 0:v -map 1:a -c:v copy -c:a aac -b:a 192k -t "$total" "$out/$name.mp4"
cp "$work/$name.srt" "$out/$name.srt"
echo "$name: $out/$name.mp4 ($(printf '%.1f' "$total")s), credits: $clips/credits.tsv"
