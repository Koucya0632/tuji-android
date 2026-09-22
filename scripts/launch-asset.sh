#!/usr/bin/env bash
#
# Render the native launch image from the composable it hands over to.
#
#   ./scripts/launch-asset.sh
#
# `windowBackground` is what the window shows before Compose draws its first
# frame — about a second on a debug build. A flat colour there means the app
# opens on an empty page. iOS covers the same gap with `LaunchLockupPeekStart`,
# an asset it renders from `TujiBrandLockup(entrance: .start)` rather than
# drawing by hand, so the static frame and the animation that continues from it
# cannot drift apart. This is that, for Android.
#
# ⚠️ **Not a `./gradlew connectedAndroidTest`.** That task uninstalls the app
# when it finishes, which deletes the app's external files directory — and the
# rendered PNG with it, silently, after a green test run. The instrumentation
# is driven directly here so the file is still there to pull.
#
# The asset is xxhdpi (3x), so the render has to happen at 480dpi. On any other
# device the density is overridden for the run and reset afterwards; the test
# itself asserts the density rather than trusting this script.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_ID="app.tuji.android.debug"
TEST_ID="$APP_ID.test"
OUT="$ROOT/app/src/main/res/drawable-xxhdpi/launch_lockup_peek_start.png"
REMOTE="/sdcard/Android/data/$APP_ID/files/launch_lockup_peek_start.png"

resolve_sdk() {
  if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then echo "$ANDROID_HOME"; return; fi
  local p
  p=$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" 2>/dev/null | head -1 || true)
  if [ -n "$p" ] && [ -d "$p" ]; then echo "$p"; return; fi
  echo "/opt/homebrew/share/android-commandlinetools"
}

SDK="$(resolve_sdk)"
ADB="$SDK/platform-tools/adb"
[ -x "$ADB" ] || { echo "Not found: $ADB — see README.md" >&2; exit 1; }
[ -n "${JAVA_HOME:-}" ] || [ ! -d /opt/homebrew/opt/openjdk@21 ] || export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export ANDROID_HOME="$SDK"

if [ -z "${ANDROID_SERIAL:-}" ]; then
  n="$("$ADB" devices | sed '1d;/^$/d' | wc -l | tr -d ' ')"
  if [ "$n" -ne 1 ]; then
    echo "Say which device — export ANDROID_SERIAL=<serial>:" >&2
    "$ADB" devices | sed '1d;/^$/d' | sed 's/^/  /' >&2
    exit 1
  fi
fi

echo "building…"
(cd "$ROOT" && ./gradlew --console=plain -q :app:assembleDebug :app:assembleDebugAndroidTest)

"$ADB" install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
"$ADB" install -r "$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" >/dev/null

# Reset on every exit, including the failure paths — a device left on an
# overridden density is a confusing thing to hand back.
restore_density() { "$ADB" shell wm density reset >/dev/null 2>&1 || true; }
trap restore_density EXIT
"$ADB" shell wm density 480 >/dev/null
sleep 2

echo "rendering…"
"$ADB" shell am instrument -w \
  -e class app.tuji.android.LaunchAssetTest \
  "$TEST_ID/androidx.test.runner.AndroidJUnitRunner" | tee /tmp/tuji-launch-asset.log

grep -q "^OK" /tmp/tuji-launch-asset.log || { echo "instrumentation did not pass" >&2; exit 1; }

mkdir -p "$(dirname "$OUT")"
"$ADB" pull "$REMOTE" "$OUT" >/dev/null 2>&1
echo "$OUT"
