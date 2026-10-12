#!/usr/bin/env bash
# Send one prompt to Harness in a fresh chat, answer approval cards, wait for the run to end,
# then print what was on screen and the step log.
#
#   PROMPT="what app is in the foreground" scripts/run-prompt.sh
#   APPROVE=deny PROMPT="..." scripts/run-prompt.sh   # tap Deny instead of Allow once
#   NEW_CHAT=0 ...                                      # keep the current chat
#   MAX_WAIT=120 ...                                    # seconds before giving up (default 90)
#
# Run it under the device lock (see the adb-mutex skill) when other agents share the phone.
set -u
export MSYS_NO_PATHCONV=1

ADB="${ADB_BIN:-adb}"
PROMPT="${PROMPT:?set PROMPT}"
APPROVE="${APPROVE:-allow}"
NEW_CHAT="${NEW_CHAT:-1}"
MAX_WAIT="${MAX_WAIT:-90}"
XML="${TMPDIR:-/tmp}/harness_run_prompt.xml"

dump_ui() {
  local i size
  for i in 1 2 3 4 5 6; do
    "$ADB" shell uiautomator dump --compressed /sdcard/harness_ui.xml >/dev/null 2>&1
    size=$("$ADB" shell "wc -c < /sdcard/harness_ui.xml 2>/dev/null" | tr -d '[:space:]')
    [ "${size:-0}" -gt 2000 ] && break
    sleep 2
  done
  "$ADB" shell cat /sdcard/harness_ui.xml > "$XML" 2>/dev/null
}

# bounds of the first node whose line matches the given ERE, as "x y" of its centre
centre_of() {
  local b
  b=$(tr '>' '\n' < "$XML" | grep -E "$1" | head -n1 | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | head -n1)
  [ -z "$b" ] && return 1
  echo "$b" | sed -n 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\1 \2 \3 \4/p' |
    awk '{ printf "%d %d", ($1 + $3) / 2, ($2 + $4) / 2 }'
}

tap() { "$ADB" shell input tap $1; }
on_screen() { tr '>' '\n' < "$XML" | grep -qE "$1"; }

echo "--- focus Harness"
"$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
"$ADB" shell am start -n dev.harness/dev.harness.ui.MainActivity >/dev/null 2>&1
sleep 3
"$ADB" logcat -c

if [ "$NEW_CHAT" = 1 ]; then
  dump_ui
  if C=$(centre_of 'content-desc="New chat"'); then tap "$C"; sleep 1; else echo "  (no New chat button; continuing)"; fi
fi

echo "--- type and send: $PROMPT"
dump_ui
C=$(centre_of 'class="android.widget.EditText"') || { echo "  no composer found"; exit 1; }
tap "$C"; sleep 1
"$ADB" shell "input keyboard text '$PROMPT'"
sleep 1
dump_ui
S=$(centre_of 'content-desc="Send"') || { echo "  Send not found"; exit 1; }
tap "$S"

echo "--- watching the run (approvals: $APPROVE)"
# The app logs "finished <reason>" when the loop ends, so that, not the screen, says when to stop. The
# agent may leave Harness (e.g. open Settings); an approval card is only visible when Harness is in front.
waited=0
last_steps=-1
quiet=0
while [ "$waited" -lt "$MAX_WAIT" ]; do
  sleep 3
  waited=$((waited + 3))
  LOG=$("$ADB" logcat -d -s Harness)
  if echo "$LOG" | grep -q ' finished '; then echo "  [${waited}s] $(echo "$LOG" | grep ' finished ' | tail -n1 | sed 's/.*Harness *: //')"; break; fi

  steps=$(echo "$LOG" | grep -c 'step ')
  if [ "$steps" != "$last_steps" ]; then last_steps=$steps; quiet=0; else quiet=$((quiet + 3)); fi

  FOCUS=$("$ADB" shell dumpsys window | grep -m1 mCurrentFocus)
  if ! echo "$FOCUS" | grep -q 'dev.harness'; then
    # Nothing new for a while and we are elsewhere: the run may be waiting on an approval card.
    if [ "$quiet" -ge 45 ]; then
      echo "  [${waited}s] quiet for ${quiet}s outside Harness; bringing it forward to check for an approval"
      "$ADB" shell am start -n dev.harness/dev.harness.ui.MainActivity >/dev/null 2>&1
      sleep 2
      quiet=0
    else
      continue
    fi
  fi

  dump_ui
  if on_screen 'text="Allow once"'; then
    label="Allow once"; [ "$APPROVE" = deny ] && label="Deny"
    tool=$(tr '>' '
' < "$XML" | grep -o 'text="[^"]*"' | grep -B3 'text="Deny"' | head -n1)
    C=$(centre_of "text=\"$label\"") && { echo "  [${waited}s] approval card ($tool) -> $label"; tap "$C"; }
    quiet=0
  fi
done
[ "$waited" -ge "$MAX_WAIT" ] && echo "  gave up after ${MAX_WAIT}s (run may still be going)"

# Come back to the conversation so the final screen dump shows it.
"$ADB" shell am start -n dev.harness/dev.harness.ui.MainActivity >/dev/null 2>&1
sleep 2

echo "--- on screen"
dump_ui
tr '>' '\n' < "$XML" | grep -o 'text="[^"]*"' | sed 's/^text=//' | grep -v '^""$' | head -n 60

echo "--- step log"
"$ADB" logcat -d -s Harness | tail -n 30

echo "--- crashes"
"$ADB" logcat -d -b crash | grep -i -E "AndroidRuntime|FATAL" | tail -n 20
echo "  (no crash lines above = clean)"
