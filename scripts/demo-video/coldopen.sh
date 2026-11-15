#!/usr/bin/env bash
# Build the cold open: a driver who meets a junction for the first time and loses the way. Staged from
# Commons clips (shake, push-in, freeze) with a heartbeat, turn signal, a horn behind, and the driver's
# and the navigation's voices (coldopen.ko.txt).
# Usage: coldopen.sh [out-dir]      Output: <out>/coldopen.mp4 (+ coldopen.srt, credits in clips/credits.tsv)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="${1:-$here/out}"
work="$out/coldopen"; clips="$out/clips"
W=2560; H=1440; FPS=30
mkdir -p "$work" "$clips"

# ---- scene: shots and sound times (seconds in the cold open) ---------------------------
HIGHWAY="Great Eastern Highway and Roe Highway interchange.ogv"
DRIVER="Distracted Driving Spot (1018169).webm"
HORN="Car Horn.wav"
# file|in|out|length|zoom from|zoom to|shake (pixels)
SHOTS=(
  "$HIGHWAY|28.5|32.5|4.2|1.04|1.10|6"     # lanes split, arrows on the road
  "$DRIVER|5.0|7.9|4.4|1.06|1.22|10"       # the driver, slowed down (the clip turns to a phone at 8.5s)
  "$HIGHWAY|34.0|38.0|3.2|1.06|1.45|16"    # the exit sign rushes up
)
FREEZE=1.6          # last frame held, draining to grey, while the narrator starts the intro
HEART_FROM=2.8      # heartbeat speeds up from here until the freeze
SIGNAL_FROM=4.6; SIGNAL_TO=8.6
HORN_AT=8.9
# -------------------------------------------------------------------------------------

python3 -I "$here/fetch-commons.py" "$clips" "$HIGHWAY" "$DRIVER" "$HORN"
total=$FREEZE; for s in "${SHOTS[@]}"; do IFS='|' read -r _ _ _ len _ _ _ <<< "$s"; total=$(awk -v a="$total" -v b="$len" 'BEGIN{print a+b}'); done
freeze_at=$(awk -v t="$total" -v f="$FREEZE" 'BEGIN{print t-f}')

CUES="$here/coldopen.ko.txt" SRT_NAME=coldopen.srt TAIL=0 "$here/narrate.sh" "$total" "$work"

# ---- video: each shot stretched to its length, pushed in and shaken -------------------------
: > "$work/shots.txt"
i=0; at=0
for s in "${SHOTS[@]}"; do
  IFS='|' read -r name in to len z0 z1 shake <<< "$s"
  frames=$(awk -v l="$len" -v f="$FPS" 'BEGIN{printf "%d", l*f}')
  stretch=$(awk -v l="$len" -v a="$in" -v b="$to" 'BEGIN{print l/(b-a)}')
  # zoompan per frame: zoom eases in, the crop window wobbles like a hand-held camera.
  zoom="$z0+($z1-$z0)*on/$frames"
  wobble="$shake*(sin(on*0.9)+0.6*sin(on*2.3))"
  extra=""
  # The horn: a red flash and a harder jolt.
  if (( i == ${#SHOTS[@]} - 1 )); then
    flash=$(awk -v h="$HORN_AT" -v a="$at" 'BEGIN{print h-a}')
    extra=",drawbox=t=fill:color=red@0.10:enable='between(t,$flash,$flash+0.25)'"
  fi
  ffmpeg -loglevel error -y -ss "$in" -t "$(awk -v a="$in" -v b="$to" 'BEGIN{print b-a}')" -i "$clips/${name// /_}.media" -an \
    -vf "setpts=$stretch*(PTS-STARTPTS),fps=$FPS,scale=$W:$H:force_original_aspect_ratio=increase,crop=$W:$H,zoompan=z='$zoom':x='iw/2-iw/zoom/2+$wobble':y='ih/2-ih/zoom/2+0.7*$wobble':d=1:s=${W}x$H:fps=$FPS,vignette=angle=PI/4.5$extra,setsar=1" \
    -frames:v "$frames" -c:v libx264 -preset veryfast -crf 20 -pix_fmt yuv420p "$work/shot$i.mp4"
  echo "file 'shot$i.mp4'" >> "$work/shots.txt"
  at=$(awk -v a="$at" -v l="$len" 'BEGIN{print a+l}'); i=$((i+1))
done
# Freeze: hold the last frame, drain the colour and dim it.
ffmpeg -loglevel error -y -sseof -0.1 -i "$work/shot$((i-1)).mp4" -frames:v 1 "$work/last.png"
ffmpeg -loglevel error -y -loop 1 -t "$FREEZE" -i "$work/last.png" \
  -vf "fps=$FPS,hue=s='max(0,1-t/0.6)':b='-3*min(1,t/$FREEZE)',setsar=1,format=yuv420p" -c:v libx264 -preset veryfast -crf 20 "$work/freeze.mp4"
echo "file 'freeze.mp4'" >> "$work/shots.txt"
ffmpeg -loglevel error -y -f concat -safe 0 -i "$work/shots.txt" -c copy "$work/video.mp4"

# ---- sound: road rumble, heartbeat, turn signal, horn, then silence at the freeze ----------
ffmpeg -loglevel error -y -f lavfi -i "aevalsrc='0.9*sin(2*PI*52*t)*exp(-28*t)+0.6*sin(2*PI*46*(t-0.17))*exp(-28*(t-0.17))*gte(t,0.17)':s=48000:d=0.6" "$work/beat.wav"
ffmpeg -loglevel error -y -f lavfi -i "aevalsrc='0.35*sin(2*PI*2100*t)*exp(-350*t)':s=48000:d=0.06" "$work/tick.wav"
ffmpeg -loglevel error -y -f lavfi -i "aevalsrc='0.30*sin(2*PI*1500*t)*exp(-350*t)':s=48000:d=0.06" "$work/tock.wav"
python3 -I - "$work" "$total" "$freeze_at" "$HEART_FROM" "$SIGNAL_FROM" "$SIGNAL_TO" "$HORN_AT" "$clips/${HORN// /_}.media" <<'EOF'
import subprocess, sys
work, total, freeze, heart, sig_from, sig_to, horn_at, horn = sys.argv[1], *map(float, sys.argv[2:8]), sys.argv[8]
inputs, chains = [], []
def place(path, at, gain=1.0, extra=""):
    n = len(inputs) // 2; inputs.extend(["-i", path]); ms = int(at * 1000)
    chains.append(f"[{n}:a]aformat=sample_rates=48000:channel_layouts=stereo,volume={gain}{extra},adelay={ms}|{ms}[s{n}]")
t = heart
while t < freeze - 0.2:                       # 70 bpm rising to about 140 bpm
    place(f"{work}/beat.wav", t, 0.9)
    t += 0.85 - 0.45 * (t - heart) / (freeze - heart)
t, k = sig_from, 0
while t < sig_to:
    place(f"{work}/{'tick' if k % 2 == 0 else 'tock'}.wav", t, 0.8); t += 0.38; k += 1
place(horn, horn_at, 0.5, ",atrim=0:1.4,afade=t=out:st=1.0:d=0.4")
place(f"{work}/narration.m4a", 0, 1.0)
n = len(inputs) // 2
rumble = f"anoisesrc=color=brown:amplitude=0.5:sample_rate=48000,lowpass=f=260,volume=0.35,aformat=channel_layouts=stereo,afade=t=out:st={freeze}:d=0.15,atrim=0:{total}[rumble]"
mix = "".join(f"[s{i}]" for i in range(n)) + "[rumble]"
graph = ";".join(chains + [rumble, f"{mix}amix=inputs={n + 1}:normalize=0,alimiter=limit=0.9,atrim=0:{total}[out]"])
subprocess.run(["ffmpeg", "-loglevel", "error", "-y", *inputs, "-filter_complex", graph, "-map", "[out]",
                "-ar", "48000", "-ac", "2", f"{work}/sound.wav"], check=True)
EOF

ffmpeg -loglevel error -y -i "$work/video.mp4" -i "$work/sound.wav" -map 0:v -map 1:a -t "$total" \
  -c:v copy -c:a aac -b:a 192k "$out/coldopen.mp4"
cp "$work/coldopen.srt" "$out/coldopen.srt"
echo "cold open: $out/coldopen.mp4 (${total}s)"
