#!/usr/bin/env bash
# Bring Harness to the front WITHOUT killing it (so an in-flight run survives) and capture.
set -u
export MSYS_NO_PATHCONV=1

ADB="${ADB_BIN:-adb}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"
LOCAL_XML="/tmp/harness_conv.xml"
SHOT="$ROOT/app/build/harness_conversation.png"

echo "--- bring Harness to front (no force-stop)"
"$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
for i in 1 2 3 4 5; do
  "$ADB" shell am start -n dev.harness/dev.harness.ui.MainActivity >/dev/null 2>&1
  sleep 2
  FOCUS=$("$ADB" shell dumpsys window | grep -m1 mCurrentFocus)
  echo "  attempt $i: $FOCUS"
  case "$FOCUS" in *dev.harness*) break ;; esac
  sleep 2
done
sleep 1

echo "--- dismiss keyboard"
"$ADB" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
sleep 1

for i in 1 2 3 4 5 6; do
  "$ADB" shell uiautomator dump --compressed /sdcard/harness_conv.xml >/dev/null 2>&1
  size=$("$ADB" shell "wc -c < /sdcard/harness_conv.xml 2>/dev/null" | tr -d '[:space:]')
  [ "${size:-0}" -gt 2000 ] && break
  sleep 2
done
: > "$LOCAL_XML"
"$ADB" shell cat /sdcard/harness_conv.xml >> "$LOCAL_XML" 2>/dev/null

echo "--- focused package"
grep -o 'package="[^"]*"' "$LOCAL_XML" | sort | uniq -c

echo "--- text nodes"
tr '>' '\n' < "$LOCAL_XML" | grep -o 'text="[^"]*"' | sed 's/^text=//' | grep -v '^""$'

echo "--- screenshot"
"$ADB" exec-out screencap -p > "$SHOT" 2>/dev/null
echo "  $SHOT ($(wc -c < "$SHOT") bytes)"

echo "--- done"
