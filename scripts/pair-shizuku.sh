#!/usr/bin/env bash
# Tap "Pair" in Harness and accept the Shizuku permission dialog.
set -u
export MSYS_NO_PATHCONV=1

ADB="${ADB_BIN:-adb}"
LOCAL_XML="/tmp/harness_ui.xml"

dump_ui() {
  local i size
  for i in 1 2 3 4 5 6; do
    "$ADB" shell uiautomator dump --compressed /sdcard/harness_ui.xml >/dev/null 2>&1
    size=$("$ADB" shell "wc -c < /sdcard/harness_ui.xml 2>/dev/null" | tr -d '[:space:]')
    [ "${size:-0}" -gt 2000 ] && break
    sleep 2
  done
  "$ADB" shell cat /sdcard/harness_ui.xml > "$LOCAL_XML" 2>/dev/null
  echo "  dump bytes: $(wc -c < "$LOCAL_XML" 2>/dev/null || echo 0)"
}

# Prints "x y" centre of the first node whose text or content-desc equals $1
centre_of() {
  local target="$1" bounds b x1 y1 x2 y2
  b=$(tr '>' '\n' < "$LOCAL_XML" \
        | grep -E "(text|content-desc)=\"$target\"" \
        | head -n1 \
        | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"')
  [ -z "$b" ] && return 1
  # awk -F'[][,]' yields an empty field for the "][" junction, so parse with sed.
  x1=$(echo "$b" | sed -n 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\1/p')
  y1=$(echo "$b" | sed -n 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\2/p')
  x2=$(echo "$b" | sed -n 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\3/p')
  y2=$(echo "$b" | sed -n 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\4/p')
  echo "$(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))"
}

echo "--- bring Harness forward (verify focus)"
"$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
# force-stop so the activity restarts on the chat screen, not a sub-screen it was left on
"$ADB" shell am force-stop dev.harness >/dev/null 2>&1
for i in 1 2 3 4 5; do
  "$ADB" shell am start -n dev.harness/dev.harness.ui.MainActivity >/dev/null 2>&1
  sleep 2
  FOCUS=$("$ADB" shell dumpsys window | grep -m1 mCurrentFocus)
  echo "  attempt $i: $FOCUS"
  case "$FOCUS" in *dev.harness*) break ;; esac
  sleep 2
done
sleep 1

echo "--- dump before"
dump_ui

echo "--- tap Pair"
COORDS=$(centre_of "Pair")
if [ -z "${COORDS:-}" ]; then
  echo "  'Pair' not found in the Harness UI"
  grep -o 'text="[^"]*"' "$LOCAL_XML" | sort -u | head -n 30
  exit 0
fi
echo "  tapping at $COORDS"
"$ADB" shell input tap $COORDS
sleep 3

echo "--- dump after Pair (looking for the Shizuku dialog)"
dump_ui
echo "  visible text:"
grep -o 'text="[^"]*"' "$LOCAL_XML" | sort -u | head -n 30
echo "  focused package:"
grep -o 'package="[^"]*"' "$LOCAL_XML" | sort | uniq -c

for CANDIDATE in "Allow" "ALLOW" "Allow always" "Grant" "Yes" "OK"; do
  COORDS=$(centre_of "$CANDIDATE") && {
    echo "--- tap '$CANDIDATE' at $COORDS"
    "$ADB" shell input tap $COORDS
    sleep 3
    break
  }
done

echo "--- dump after grant"
dump_ui
grep -o 'text="[^"]*"' "$LOCAL_XML" | sort -u | head -n 30

echo "--- done"
