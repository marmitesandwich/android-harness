#!/usr/bin/env bash
# Install and launch the Harness app on the attached device, then smoke-check it.
set -u
export MSYS_NO_PATHCONV=1

ADB="${ADB_BIN:-adb}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"

echo "--- install"
"$ADB" install -r "$APK"

echo "--- clear logcat"
"$ADB" logcat -c || true

echo "--- launch"
"$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
"$ADB" shell am start -n dev.harness/dev.harness.ui.MainActivity
sleep 4

echo "--- focused window"
"$ADB" shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp"

echo "--- process alive?"
"$ADB" shell pidof dev.harness

echo "--- crashes"
"$ADB" logcat -d -b crash | grep -i -E "AndroidRuntime|FATAL" | tail -n 20
echo "(no output above means no crash)"

echo "--- done"
