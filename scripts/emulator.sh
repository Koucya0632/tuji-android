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
# With a phone attached as well, set ANDROID_SERIAL to choose — `install`,
# `shot` and `log` follow it. `up` always means the emulator.
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

# The emulator's serial, or empty. Everything about booting has to name it:
# with a phone plugged in as well, a bare `adb shell` picks whichever device
# adb feels like — which is how `up` once reported the emulator ready by
# reading the *phone's* boot state, having just started an emulator that was
# still black.
emulator_serial() {
  "$ADB" devices | awk '/^emulator-/ { print $1; exit }'
}

running() {
  [ -n "$(emulator_serial)" ]
}

booted() {
  local serial
  serial="$(emulator_serial)"
  [ -n "$serial" ] || return 1
  [ "$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]
}

cmd_up() {
  if running && booted; then
    # Name the emulator, not "whatever adb lists first" — that line printed a
    # phone's serial the moment one was plugged in.
    echo "already up: $(emulator_serial)"
    export ANDROID_SERIAL="$(emulator_serial)"
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
  # No `adb wait-for-device` here: it waits for *a* device, and with a phone
  # attached one is already there. Poll for the emulator by serial instead.
  local waited=0
  until booted; do
    sleep 2
    waited=$((waited + 2))
    if [ "$waited" -ge 300 ]; then
      echo "gave up after 5 minutes" >&2
      exit 1
    fi
  done
  echo "up in ${waited}s ($(emulator_serial))"
  # Later steps in this run target it explicitly, so `install` right after `up`
  # cannot land on a phone that happens to be plugged in.
  export ANDROID_SERIAL="$(emulator_serial)"
}

# With both a phone and an emulator attached, "which one" is a real question
# and the script does not get to answer it by guessing. `up` exports
# ANDROID_SERIAL for the rest of *its* process, which covers the common
# `emulator.sh` (run) path; invoked separately, the caller has to say.
require_single_target() {
  [ -n "${ANDROID_SERIAL:-}" ] && return 0
  local n
  n="$("$ADB" devices | sed '1d;/^$/d' | wc -l | tr -d ' ')"
  [ "$n" -le 1 ] && return 0
  echo "More than one device attached — say which:" >&2
  "$ADB" devices | sed '1d;/^$/d' | sed 's/^/  /' >&2
  echo "  export ANDROID_SERIAL=<serial>     # or ./scripts/emulator.sh kill" >&2
  exit 1
}

cmd_install() {
  require_single_target
  echo "building…"
  (cd "$ROOT" && ./gradlew --console=plain -q :app:assembleDebug)
  "$ADB" install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
  "$ADB" shell am force-stop "$APP_ID"
  "$ADB" shell am start -W -n "$ACTIVITY" | sed -n 's/^TotalTime: /launched in /p'
}

cmd_shot() {
  require_single_target
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
  log)     require_single_target; "$ADB" logcat --pid="$("$ADB" shell pidof "$APP_ID")" ;;
  kill)    "$ADB" emu kill && echo "shutting down" ;;
  *)       sed -n '2,20p' "$0" | sed 's|^# \{0,1\}||' ; exit 1 ;;
esac
