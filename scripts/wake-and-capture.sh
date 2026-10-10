#!/usr/bin/env bash
# Wake the screen, bring Harness to the front and capture proof it renders.
set -u
export MSYS_NO_PATHCONV=1

ADB="${ADB_BIN:-adb}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"
OUT="$ROOT/app/build/harness_smoke.png"

echo "--- wake + unlock"
"$ADB" shell input keyevent KEYCODE_WAKEUP
sleep 1
"$ADB" shell input keyevent KEYCODE_MENU

echo "--- launch"
"$ADB" shell am start -n dev.harness/dev.harness.ui.MainActivity
sleep 4

echo "--- wakefulness"
"$ADB" shell dumpsys power | grep -E "mWakefulness=" | head -n 2

echo "--- focused window"
"$ADB" shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp"

echo "--- process alive?"
"$ADB" shell pidof dev.harness

echo "--- crashes"
"$ADB" logcat -d -t 300 | grep -i -E "AndroidRuntime|FATAL EXCEPTION" | tail -n 30

echo "--- capture"
"$ADB" shell screencap -p /sdcard/harness_smoke.png
"$ADB" pull /sdcard/harness_smoke.png "$OUT"
"$ADB" shell rm -f /sdcard/harness_smoke.png
ls -la "$OUT"

echo "--- done"
