#!/usr/bin/env bash
#
# Boot the emulator, build, install, launch. One command, because the four of
# them in order is what you actually want and the paths on this project are not
# the defaults.
#
#   ./scripts/emulator.sh            boot if needed, then build + install + launch
#   ./scripts/emulator.sh up         boot and wait, nothing else
#   ./scripts/emulator.sh install    build + install + launch (assumes it is up)
#   ./scripts/emulator.sh shot [f]   screenshot to f (default ./captures/<time>.png)
#   ./scripts/emulator.sh log        follow logcat, filtered to this app
#   ./scripts/emulator.sh kill       shut the emulator down
#
# The SDK is NOT at the usual ~/Library/Android/sdk here — it comes from
# Homebrew's android-commandlinetools. Resolution order below is deliberate:
# an explicit ANDROID_HOME wins, then local.properties (which is what Gradle
# itself reads, so the script and the build can never disagree), then the
# Homebrew default.
set -euo pipefail

# ⚠️ Brace any variable followed by non-ASCII text. `"$AVD…"` is not
# `$AVD` plus an ellipsis — bash reads the ellipsis bytes as part of the
# name and dies with `AVD?: unbound variable`, naming a variable that does
# not appear anywhere in the source.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AVD="${TUJI_AVD:-tuji_api36}"
APP_ID="app.tuji.android.debug"
ACTIVITY="$APP_ID/app.tuji.android.MainActivity"

resolve_sdk() {
  if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then
    echo "$ANDROID_HOME"; return
  fi
  local from_props
  from_props=$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" 2>/dev/null | head -1 || true)
  if [ -n "$from_props" ] && [ -d "$from_props" ]; then
    echo "$from_props"; return
  fi
  echo "/opt/homebrew/share/android-commandlinetools"
}

SDK="$(resolve_sdk)"
ADB="$SDK/platform-tools/adb"
EMU="$SDK/emulator/emulator"

for tool in "$ADB" "$EMU"; do
  if [ ! -x "$tool" ]; then
    echo "Not found: $tool" >&2
    echo "Set ANDROID_HOME, or put sdk.dir in local.properties. See README.md." >&2
    exit 1
  fi
done

# Gradle needs a JDK and this project needs 21. Only exported if it is missing,
# so a machine with its own JAVA_HOME keeps it.
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@21 ]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21
fi
export ANDROID_HOME="$SDK"

running() {
  "$ADB" devices | grep -q "^emulator-.*device$"
}

booted() {
  [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]
}

cmd_up() {
  if running && booted; then
    echo "already up: $("$ADB" devices | sed -n '2p')"
    return
  fi
  if ! "$EMU" -list-avds | grep -qx "$AVD"; then
    echo "No AVD named '$AVD'. Available:" >&2
    "$EMU" -list-avds | sed 's/^/  /' >&2
    echo "Create one, or set TUJI_AVD=<name>." >&2
    exit 1
  fi
  echo "booting ${AVD}…"
  # swiftshader: the host GPU path is the usual source of a window that opens
  # and then renders nothing on a headless or remote session.
  nohup "$EMU" -avd "$AVD" -no-snapshot -no-boot-anim -gpu swiftshader_indirect -no-audio \
    >/dev/null 2>&1 &
  # `adb wait-for-device` returns as soon as adbd answers, which is well before
  # the system is usable — an install here fails with a device that says it is
  # online. sys.boot_completed is the real signal.
  "$ADB" wait-for-device
  local waited=0
  until booted; do
    sleep 2
    waited=$((waited + 2))
    if [ "$waited" -ge 300 ]; then
      echo "gave up after 5 minutes" >&2
      exit 1
    fi
  done
  echo "up in ${waited}s"
}

cmd_install() {
  echo "building…"
  (cd "$ROOT" && ./gradlew --console=plain -q :app:assembleDebug)
  "$ADB" install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
  "$ADB" shell am force-stop "$APP_ID"
  "$ADB" shell am start -W -n "$ACTIVITY" | sed -n 's/^TotalTime: /launched in /p'
}

cmd_shot() {
  local out="${1:-$ROOT/captures/$(date +%Y%m%d-%H%M%S).png}"
  mkdir -p "$(dirname "$out")"
  "$ADB" shell screencap -p /sdcard/tuji-shot.png
  # adb reports transfer progress on stderr, so both have to go — the point
  # of this command is that `f=$(emulator.sh shot)` is a usable path.
  "$ADB" pull -a /sdcard/tuji-shot.png "$out" >/dev/null 2>&1
  "$ADB" shell rm -f /sdcard/tuji-shot.png
  echo "$out"
}

case "${1:-run}" in
  run)     cmd_up; cmd_install ;;
  up)      cmd_up ;;
  install) cmd_install ;;
  shot)    cmd_shot "${2:-}" ;;
  log)     "$ADB" logcat --pid="$("$ADB" shell pidof "$APP_ID")" ;;
  kill)    "$ADB" emu kill && echo "shutting down" ;;
  *)       sed -n '2,20p' "$0" | sed 's|^# \{0,1\}||' ; exit 1 ;;
esac
