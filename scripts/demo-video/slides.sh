#!/usr/bin/env bash
# Render the slides in slides.html to out/slides/<id>.png with headless Chrome (2560x1440).
# The "moments" slide shows one road view photo taken from the screen recording (ROADVIEW_AT seconds).
# Usage: slides.sh [out-dir] [id...]      (default: every <section id> in slides.html)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="${1:-$here/out}"; shift || true
chrome="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
# Second point's road view on the default route, sharp and still on the approach: 9.2 s after "다음 지점" (events.tsv).
ROADVIEW_AT="${ROADVIEW_AT:-$(awk -F'\t' '$1 == "next" {print $2 + 9.2; exit}' "$out/events.tsv" 2>/dev/null || true)}"
ROADVIEW_CROP="${ROADVIEW_CROP:-1480:720:1015:420}"   # w:h:x:y of the photo below the banner, in the 2560x1440 recording
mkdir -p "$out/slides"
dir="$(cd "$out/slides" && pwd)"   # absolute: Chrome needs a file:// URL
[[ -x "$chrome" ]] || { echo "FAIL: Chrome not found at $chrome (set CHROME=...)" >&2; exit 1; }

ids=("$@")
if (( ${#ids[@]} == 0 )); then
  while read -r id; do ids+=("$id"); done < <(grep -o '<section id="[a-z0-9-]*"' "$here/slides.html" | cut -d'"' -f2)
fi
cp "$here/slides.html" "$dir/slides.html"
if [[ -s "$out/screen.mp4" && -n "$ROADVIEW_AT" ]]; then
  ffmpeg -loglevel error -y -ss "$ROADVIEW_AT" -i "$out/screen.mp4" -frames:v 1 -vf "crop=$ROADVIEW_CROP" -q:v 3 "$dir/roadview.jpg"
fi
for id in "${ids[@]}"; do
  "$chrome" --headless=new --disable-gpu --hide-scrollbars \
    --user-data-dir="$dir/.chrome" --window-size=2560,1440 \
    --screenshot="$dir/$id.png" "file://$dir/slides.html?s=$id" >/dev/null 2>&1
  [[ -s "$dir/$id.png" ]] || { echo "FAIL: slide '$id' was not rendered" >&2; exit 1; }
done
echo "slides: ${ids[*]} -> $dir"
