#!/usr/bin/env bash
# Copies 場面練習's content from a tuji-ios checkout into app/src/main/assets/scenes:
# the scene JSON, every clip, and the stage images the JSON names. tuji-ios is
# where the content is written and recorded; this app ships the same files.
#
#   scripts/sync-scenes.sh [path/to/tuji-ios]     (default: ../tuji-ios)
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
ios="$(cd "${1:-$here/../tuji-ios}" && pwd)"
src="$ios/Tuji/Resources/Scenes"
dst="$here/app/src/main/assets/scenes"
[[ -f "$src/index.json" ]] || { echo "no scenes at $src" >&2; exit 1; }

rm -rf "$dst"
mkdir -p "$dst/audio" "$dst/images"
cp "$src"/*.json "$dst/"
cp "$src"/audio/*.mp3 "$dst/audio/"

python3 - "$src" <<'PY' | while read -r name; do
import glob, json, os, sys
names = set()
for f in glob.glob(os.path.join(sys.argv[1], "*.json")):
    if f.endswith("index.json"):
        continue
    for step in (json.load(open(f)).get("stage") or {}).get("steps", {}).values():
        names.add(step["background"])
        names.update(step.get("props") or [])
        for reply in step.get("replies") or []:
            names.update(reply.get("show") or [])
print("\n".join(sorted(names)))
PY
  set_dir="$ios/Tuji/Assets.xcassets/$name.imageset"
  file="$(ls "$set_dir" | grep -Ei '\.(jpg|jpeg|png|webp)$' | head -1)"
  [[ -n "$file" ]] || { echo "no image for $name" >&2; exit 1; }
  cp "$set_dir/$file" "$dst/images/$name.${file##*.}"
done
echo "synced $(ls "$dst"/*.json | wc -l | tr -d ' ') json, $(ls "$dst/audio" | wc -l | tr -d ' ') clips, $(ls "$dst/images" | wc -l | tr -d ' ') images from $(git -C "$ios" rev-parse --short HEAD)"
