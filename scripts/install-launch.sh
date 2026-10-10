#!/usr/bin/env bash
# Install, launch and smoke-check the Harness app on the attached device.
set -u

ADB="${ADB_BIN:-adb}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"

echo "--- install"
"$ADB" install -r "$APK"

echo "--- force-stop stale"
"$ADB" shell am force-stop dev.harness

echo "--- clear logcat"
"$ADB" logcat -c

echo "--- launch"
"$ADB" shell am start -n dev.harness/dev.harness.ui.MainActivity
sleep 5

echo "--- focused window"
"$ADB" shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp"

echo "--- process alive?"
"$ADB" shell pidof dev.harness

echo "--- crashes / app logs"
"$ADB" logcat -d -t 500 | grep -i -E "AndroidRuntime|FATAL|dev\.harness" | tail -n 60

echo "--- screenshot"
"$ADB" shell screencap -p /sdcard/harness_smoke.png
"$ADB" pull /sdcard/harness_smoke.png "C:/Users/Paul Cartwright/Documents/harness/app/build/harness_smoke.png"
"$ADB" shell rm -f /sdcard/harness_smoke.png

echo "--- done"
