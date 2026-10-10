#!/usr/bin/env bash
# Set the model field in Harness Settings. Usage: MODEL=glm-5.3-flash set-model.sh
set -u
export MSYS_NO_PATHCONV=1

ADB="${ADB_BIN:-adb}"
LOCAL_XML="/tmp/harness_ui.xml"
MODEL="${MODEL:-glm-5.3-flash}"

dump_ui() {
  local i size
  for i in 1 2 3 4 5 6; do
    "$ADB" shell uiautomator dump --compressed /sdcard/harness_ui.xml >/dev/null 2>&1
    size=$("$ADB" shell "wc -c < /sdcard/harness_ui.xml 2>/dev/null" | tr -d '[:space:]')
    [ "${size:-0}" -gt 2000 ] && break
    sleep 2
  done
  : > "$LOCAL_XML"
  "$ADB" shell cat /sdcard/harness_ui.xml >> "$LOCAL_XML" 2>/dev/null
}

bounds_of() {
  tr '>' '\n' < "$LOCAL_XML" | grep -E "$1" | head -n1 | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | head -n1
}

centre() {
  local b="$1" x1 y1 x2 y2
  x1=$(echo "$b" | sed -n 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\1/p')
  y1=$(echo "$b" | sed -n 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\2/p')
  x2=$(echo "$b" | sed -n 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\3/p')
  y2=$(echo "$b" | sed -n 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\4/p')
  echo "$(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))"
}

echo "--- launch Harness"
"$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
"$ADB" shell am force-stop dev.harness >/dev/null 2>&1
"$ADB" shell am start -n dev.harness/dev.harness.ui.MainActivity >/dev/null 2>&1
sleep 4

echo "--- open Settings"
dump_ui
SB="$(bounds_of 'text="Settings"')"
if [ -z "$SB" ]; then echo "  Settings button not found"; exit 0; fi
"$ADB" shell input tap $(centre "$SB")
sleep 2

echo "--- find the Model field (2nd EditText)"
dump_ui
MB="$(tr '>' '\n' < "$LOCAL_XML" | grep 'class="android.widget.EditText"' | sed -n '2p' | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | head -n1)"
if [ -z "$MB" ]; then echo "  model field not found"; exit 0; fi
echo "  model field: $MB -> tap $(centre "$MB")"
"$ADB" shell input tap $(centre "$MB")
sleep 1

echo "--- clear it"
"$ADB" shell input keyevent KEYCODE_MOVE_END
DELS="$(printf '67 %.0s' $(seq 1 40))"
"$ADB" shell input keyevent $DELS
sleep 1

echo "--- type new model: $MODEL"
"$ADB" shell "input text '$MODEL'"
sleep 1

echo "--- field now:"
dump_ui
tr '>' '\n' < "$LOCAL_XML" | grep 'class="android.widget.EditText"' | sed -n '2p' | grep -o 'text="[^"]*"'

echo "--- save"
SAB="$(bounds_of 'text="Save"')"
if [ -n "$SAB" ]; then
  "$ADB" shell input tap $(centre "$SAB")
  sleep 1
  echo "  saved"
fi

echo "--- done"
