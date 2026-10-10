#!/usr/bin/env bash
# Print the full conversation as written on screen, and capture a screenshot.
set -u
export MSYS_NO_PATHCONV=1

ADB="${ADB_BIN:-adb}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"
LOCAL_XML="/tmp/harness_conv.xml"
SHOT="$ROOT/app/build/harness_conversation.png"

echo "--- dismiss the keyboard so the message list is visible"
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
echo "--- bytes: $(wc -c < "$LOCAL_XML")"

echo "--- every on-screen text node, in order"
tr '>' '\n' < "$LOCAL_XML" \
  | grep -o 'text="[^"]*"' \
  | sed 's/^text=//' \
  | grep -v '^""$'

echo "--- screenshot"
"$ADB" exec-out screencap -p > "$SHOT" 2>/dev/null
echo "  $SHOT ($(wc -c < "$SHOT") bytes)"

echo "--- done"
