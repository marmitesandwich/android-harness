#!/usr/bin/env bash
# Wake the screen, bring Harness to the front and capture proof it renders.
set -u
export MSYS_NO_PATHCONV=1

ADB="${ADB_BIN:-adb}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"
OUT="$ROOT/app/build/harness_smoke.png"

echo "--- wake"
"$ADB" shell input keyevent KEYCODE_WAKEUP
sleep 1

echo "--- launch"
"$ADB" shell am start -n dev.harness/dev.harness.ui.MainActivity
sleep 3

echo "--- wakefulness"
"$ADB" shell dumpsys power | grep -E "mWakefulness=" | head -n 1

echo "--- focused window"
"$ADB" shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp"

echo "--- process alive?"
"$ADB" shell pidof dev.harness

echo "--- crashes"
"$ADB" logcat -d -t 300 | grep -i -E "AndroidRuntime|FATAL EXCEPTION" | tail -n 30

echo "--- capture (retry until non-empty)"
BYTES=0
for i in 1 2 3 4 5; do
  "$ADB" exec-out screencap -p > "$OUT" 2>/dev/null
  BYTES=$(wc -c < "$OUT" 2>/dev/null || echo 0)
  [ "${BYTES:-0}" -gt 1000 ] && break
  echo "  attempt $i: ${BYTES:-0} bytes, retrying"
  sleep 2
done

if [ "${BYTES:-0}" -gt 1000 ]; then
  ls -la "$OUT"
else
  echo "  capture failed: screen likely still off or locked"
fi

echo "--- done"
