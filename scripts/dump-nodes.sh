#!/usr/bin/env bash
# Diagnostic: print the nodes relevant to composing a message, with bounds.
set -u
export MSYS_NO_PATHCONV=1

ADB="${ADB_BIN:-adb}"
LOCAL_XML="/tmp/harness_ui.xml"

echo "--- screen size"
"$ADB" shell wm size
"$ADB" shell wm density

echo "--- focus"
"$ADB" shell dumpsys window | grep -m1 mCurrentFocus

echo "--- dump"
for i in 1 2 3 4 5 6; do
  "$ADB" shell uiautomator dump --compressed /sdcard/harness_ui.xml >/dev/null 2>&1
  size=$("$ADB" shell "wc -c < /sdcard/harness_ui.xml 2>/dev/null" | tr -d '[:space:]')
  [ "${size:-0}" -gt 2000 ] && break
  sleep 2
done
"$ADB" shell cat /sdcard/harness_ui.xml > "$LOCAL_XML" 2>/dev/null
echo "  bytes: $(wc -c < "$LOCAL_XML")"

echo "--- nodes containing Send / Ask / EditText"
tr '>' '\n' < "$LOCAL_XML" \
  | grep -E 'Send|Ask the device|EditText' \
  | sed -n 's/.*\(text="[^"]*"\).*\(class="[^"]*"\).*\(bounds="[^"]*"\).*/\1 \2 \3/p' \
  | head -n 20

echo "--- all nodes in the bottom third"
tr '>' '\n' < "$LOCAL_XML" \
  | grep -o 'text="[^"]*"[^>]*bounds="\[[0-9,]*\]\[[0-9,]*\]"' \
  | tail -n 20

echo "--- done"
