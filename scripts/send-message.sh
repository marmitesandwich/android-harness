#!/usr/bin/env bash
# Type a prompt into Harness, send it, and report the conversation.
# Targets the composer by widget class and verifies each step before the next.
set -u
export MSYS_NO_PATHCONV=1

ADB="${ADB_BIN:-adb}"
LOCAL_XML="/tmp/harness_ui.xml"
PROMPT="${PROMPT:-what is my screen timeout}"

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
  echo "  dump bytes: $(wc -c < "$LOCAL_XML")"
}

# bounds of the first node whose line matches the given ERE
bounds_of() {
  tr '>' '\n' < "$LOCAL_XML" | grep -E "$1" | head -n1 | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | head -n1
}

centre() {
  # bounds="[x1,y1][x2,y2]" -> "centreX centreY".
  # Note: awk -F'[][,]' yields an empty field for the "][" junction, so parse with sed.
  local b="$1" x1 y1 x2 y2
  x1=$(echo "$b" | sed -n 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\1/p')
  y1=$(echo "$b" | sed -n 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\2/p')
  x2=$(echo "$b" | sed -n 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\3/p')
  y2=$(echo "$b" | sed -n 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\4/p')
  echo "$(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))"
}

edittext_bounds() { bounds_of 'class="android.widget.EditText"'; }

echo "--- focus Harness"
"$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
"$ADB" shell am force-stop dev.harness >/dev/null 2>&1
"$ADB" shell am start -n dev.harness/dev.harness.ui.MainActivity >/dev/null 2>&1
sleep 4
"$ADB" shell dumpsys window | grep -m1 mCurrentFocus

echo "--- focus the composer"
dump_ui
CB="$(edittext_bounds)"
if [ -z "$CB" ]; then echo "  no EditText found"; exit 0; fi
echo "  composer bounds: $CB -> tap $(centre "$CB")"
"$ADB" shell input tap $(centre "$CB")
sleep 1

dump_ui
if tr '>' '\n' < "$LOCAL_XML" | grep -q 'class="android.widget.EditText"[^/]*focused="true"'; then
  echo "  composer focused: yes"
else
  echo "  composer focused: NO (continuing anyway)"
fi

echo "--- type: $PROMPT"
# `input text` mangles spaces on this OEM build; `input keyboard text` handles them.
"$ADB" shell input keyboard text "$PROMPT" >/dev/null 2>&1 \
  || "$ADB" shell input text "$(echo "$PROMPT" | tr ' ' '%s')"
sleep 1

dump_ui
echo "  field now contains:"
tr '>' '\n' < "$LOCAL_XML" | grep 'class="android.widget.EditText"' | head -n1 | grep -o 'text="[^"]*"'

echo "--- tap Send"
SB="$(bounds_of 'text="Send"')"
if [ -z "$SB" ]; then echo "  Send not found"; exit 0; fi
echo "  send bounds: $SB -> tap $(centre "$SB")"
"$ADB" shell input tap $(centre "$SB")

echo "--- wait for the agent loop"
sleep 25

echo "--- conversation"
dump_ui
tr '>' '\n' < "$LOCAL_XML" | grep -o 'text="[^"]*"' | sed 's/^text=//' | grep -v '^""$' | head -n 40

echo "--- crashes"
"$ADB" logcat -d -b crash | grep -i -E "AndroidRuntime|FATAL" | tail -n 20
echo "  (no crash lines above = clean)"

echo "--- done"
