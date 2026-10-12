# Android AI Harness

A personal Android app that acts as an agent harness: chat with an LLM that can
call native tools to inspect device settings, drive other apps, and debug issues.

The model proposes tool calls; the app executes them on-device with a tiered
approval gate. Nothing leaves the device except the LLM conversation itself
(and any URL you approve for `http_fetch`).

## Requirements

- Android 11 (API 30) or newer, side-loaded (not a Play Store distribution).
- [Shizuku](https://shizuku.rikka.app/) running, for shell tools.
- An OpenAI-compatible chat-completions endpoint and API key.
- JDK 17 to build. Gradle is provided by the wrapper.

## Build

```powershell
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Install with
`adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Setup on the device

1. Install and start Shizuku, then open Harness and tap **Pair** in the banner
   to grant Shizuku permission.
2. Enable the accessibility service: **Settings > Accessibility > Harness UI
   control**. This is what powers the UI tools.
3. Open **Settings** in the app and paste your API key. Set the model to a
   chat-completions model id for your provider. The default base URL is
   `https://opencode.ai/zen/go/v1`; OpenCode Go model ids look like
   `glm-5.3-flash` or `deepseek-v4.1-flash` (see
   <https://opencode.ai/docs/go/#endpoints>). Models served on `/responses` or
   `/messages` are not supported in v1.

The banner disappears when all three prerequisites are satisfied.

## Using it

**Asking.** Type a question, or tap one of the three example prompts on the empty
chat. The examples are picked at random from a pool of 42 each time the empty chat
appears (open **New chat** for a fresh set). Once you have sent a few prompts of
your own, one of the three is a past prompt, chosen more often the more you have
used it. Your prompt history stays on the device in encrypted storage; it keeps
the 50 most-used. Tapping an example sends it straight away.

One example may also be written for the phone's state right now, for example "Why
isn't my Wi-Fi working?" when the network is down. It appears with a small "For
right now" label a moment after the screen opens. To write it, the app sends the
model a coarse status summary (battery, network, free storage, screen state) once
when the empty chat opens, and reuses the answer for 10 minutes. No notifications,
contacts, files or screen text are sent. It is skipped when there is no API key or
Shizuku is not running, and if the call fails or takes over 10 seconds you simply
see the usual examples.

**Approving.** Anything that changes the phone, reads private data (contacts,
calendar, texts, clipboard) or touches the network stops and shows a card with a
plain-language title, the exact arguments, and three buttons:

- **Deny**: nothing happens and the agent is told you said no.
- **Allow once**: run it this one time.
- **Always allow**: stop asking for that action in every chat. Undo it from
  **Settings**. Not offered for `run_shell`, `write_file`, `install_apk`,
  `uninstall_app` or `query_sms`.

If the agent has taken you to another app (say it opened Wi-Fi settings), Harness
is out of sight, so the same card appears **over that app**. This needs the
accessibility service switched on. With Harness open, the card appears in the app.

**Showing someone how to do something.** Ask in plain words, for example "show me
how to turn on Bluetooth". The agent opens the right screen, then uses `highlight`
to outline what to tap, with a short label. The outline pulses, lasts 20 seconds
by default (up to 60), and disappears when the screen changes. It does not block
touches, so the person taps the real button underneath. Then they return to
Harness and ask for the next step. This is the intended way to hand the app to
someone less technical: set up Shizuku and the accessibility service once, and
consider **Always allow** for "Open an app" and "Open a page or screen" so opening
screens does not need a tap each time.

**Long tasks.** The agent can keep a scratchpad with `notes` that survives a new
chat. Press **Stop** to end a run at any point. A run also ends when the model
calls `done` or reaches the 40-step cap; the chat says which.

## Tools

42 tools. **Read** tools run automatically. **Write** tools show an approve/deny
card with the exact arguments; most offer a per-tool "always allow" (see below).

### Reading the device (read)

| Tool | What it does |
|---|---|
| `get_setting`, `getprop` | Read an Android setting or system property |
| `logcat`, `dumpsys`, `list_packages`, `app_info` | Logs, services, installed apps |
| `current_app` | Foreground app and activity |
| `device_status` | Battery, network, storage, screen state in one call |
| `notifications` | Active notifications (package, title, text) |
| `read_file`, `list_files` | Files under `/sdcard`, `/storage/emulated/0`, `/data/local/tmp` only |
| `ui_tree`, `screenshot` | Screen contents (accessibility) |
| `find_element` | Search the screen by text, description or id, with tap coordinates |
| `highlight` | Outline an element with a short label like "Tap Wi-Fi", drawn over any app. It only draws and cannot tap anything |

### Waiting (read)

These block inside one call so a wait costs one step, not one per poll. Every
wait has a timeout of at most 60 seconds.

| Tool | What it does |
|---|---|
| `wait` | Pause 1-30 seconds |
| `wait_for_log` | Block until a *new* logcat line matches a regex |
| `wait_for_ui` | Block until an element appears or disappears |

### Agent-side (read)

| Tool | What it does |
|---|---|
| `notes` | Persistent scratchpad (read/write/append/clear, 8000 chars) that survives new chats and restarts |
| `done` | Ends the run with a visible summary |

### Changing the device (write)

| Tool | What it does |
|---|---|
| `put_setting`, `launch_app`, `force_stop` | Settings and app control |
| `run_shell` | Arbitrary shell. **Always asks, can never be remembered** |
| `start_intent` | Open a link, settings page, or explicit activity |
| `set_radio` | Wi-Fi, Bluetooth or mobile data on/off |
| `tap_element` | Tap by text, description or id |
| `tap`, `swipe`, `type_text`, `press_back`, `press_home` | Raw gestures and navigation |
| `scroll_until` | Swipe until an element is visible |
| `clipboard_set` | Replace the clipboard |
| `write_file` | Write or append under the allowed storage roots. **Never remembered** |
| `install_apk` | Install over the existing app, keeping data. **Never remembered** |
| `uninstall_app` | Keeps app data unless `keep_data=false`. **Never remembered** |

### Private data and network (write-gated)

These do not change anything on the device, but their output is sent to the LLM
provider, so each use needs approval.

| Tool | What it does |
|---|---|
| `query_contacts`, `query_calendar` | Contacts and upcoming events |
| `query_sms` | Inbox messages. **Never remembered** |
| `clipboard_get` | Clipboard text (Android may hide it from a background app) |
| `http_fetch` | HTTP GET of an `http(s)` URL. Gated because a URL can carry data out |

Not included on purpose: `send_sms` (use `start_intent` with an `sms:` URI, then
`tap_element`, so you approve each step) and an `ask_user` tool (the model asking
in a normal reply already ends the turn and waits for you).

## Manual device checklist

Run after installing:

1. Pair Shizuku and confirm `get_setting(system, screen_off_timeout)` returns a value
   when you ask "what is my screen off timeout?".
2. Enable accessibility, then ask "what is on my screen?" and confirm the agent
   calls `ui_tree` or `find_element`.
3. Ask the agent to change a setting (e.g. screen timeout) and confirm the
   approval card appears with the exact arguments before anything changes.
4. Tap **Deny** once and confirm the tool result is `user denied` and no change
   is made.
5. Ask the agent to run `run_shell` twice and confirm it prompts both times.
6. Turn off Shizuku and confirm shell tools return a clear message instead of
   crashing.
7. Ask it to open Settings > Wi-Fi with `start_intent`, then tap a row with
   `tap_element`, and confirm it used element text, not coordinates.
8. Ask it to wait for a log line (for example "tell me when `ActivityManager`
   logs something about Settings") and confirm one `wait_for_log` call, not a
   string of `logcat` calls.
9. Ask it to save a note, start a new chat, and read it back with `notes`.
10. Ask "open the Wi-Fi settings page and highlight Wi-Fi Assistant" and confirm
    an orange outline and a label appear over Settings, and clear when you leave
    the screen.
11. Ask it to open Settings and then tap something. Confirm the approval card
    appears **over Settings** (not inside Harness) and that Allow once lets the
    run continue.
12. Check the tools that parse device output on your Android version. These
    commands are verified only by unit tests, not on a device:
    `device_status`, `notifications`, `query_contacts`, `query_calendar`,
    `query_sms`, `clipboard_get`.

## Architecture

| Package | Responsibility |
|---|---|
| `ui/` | Compose chat screen, approval card, settings, status banner |
| `agent/` | `AgentLoop`, tiered `ApprovalGate`, system prompt |
| `llm/` | `LlmClient` + `OpenAiCompatClient` (OkHttp SSE) |
| `tools/` | `Tool` interface, `ToolRegistry`, shell, UI, wait, notes, clipboard and fetch tools |
| `backend/` | `ShellBackend` (Shizuku), `UiBackend` (AccessibilityService), `OverlayBackend` (highlight and approval card drawn over other apps), clipboard, notes store, HTTP fetcher |

The agent loop sends the conversation to the model, runs any requested tools
after gating, appends `role: tool` results, and repeats until the model replies
without tool calls, calls `done`, the step budget (default 40) is exhausted, or
you press **Stop**. Each step is logged under the `Harness` tag, and the chat
warns when a run is close to the cap.

`tap_element`, `find_element`, `wait_for_ui`, `scroll_until` and `highlight` work by
parsing the `ui_tree` text dump, so they need no extra accessibility plumbing.

The overlays are accessibility-overlay windows, which need no extra permission.
The highlight window is full-screen and untouchable. The approval card is a small
window at the bottom that takes touches; touches outside it still reach the app
underneath. A highlight is dropped when another window comes to the front.

## Driving the app from a computer

Scripts in `scripts/` drive the installed app over `adb` (set `ADB_BIN` if `adb`
is not on your PATH). They use `uiautomator`, so they need a connected device and
the screen unlocked.

| Script | What it does |
|---|---|
| `install-launch.sh` | Install the debug APK over the existing one (keeps data), launch, and check for crashes |
| `run-prompt.sh` | Send one prompt in a fresh chat, tap **Allow once** on approval cards, wait for the run to end, then print the screen and the step log |
| `send-message.sh` | Type a prompt and send it, then print the conversation after a fixed wait |
| `pair-shizuku.sh` | Tap **Pair** in Harness and accept the Shizuku dialog |
| `set-model.sh` | Set the model in Settings: `MODEL=glm-5.3-flash scripts/set-model.sh` |
| `show-conversation.sh`, `capture-harness.sh`, `wake-and-capture.sh` | Print the on-screen conversation and/or take a screenshot |
| `dump-nodes.sh` | Diagnostic: list the composer and Send nodes with bounds |

`run-prompt.sh` options, all environment variables:

```bash
PROMPT="what app is in the foreground" scripts/run-prompt.sh
APPROVE=deny PROMPT="..." scripts/run-prompt.sh   # tap Deny instead of Allow once
NEW_CHAT=0 ...                                      # keep the current chat
MAX_WAIT=120 ...                                    # seconds before giving up (default 90)
RETURN_TO_APP=0 ...                                 # stay on whatever app the agent left open
```

It ends when the app logs `finished`, so it works even when the agent leaves
Harness. With `RETURN_TO_APP=0` the final screen dump can disturb a highlight, so
take a screenshot yourself instead. If several tools share one phone, run it under
a device lock. Tests that tap things change the phone for real: an approved tap on
the Wi-Fi row turned Wi-Fi off during development.

## Testing

```powershell
./gradlew :app:testDebugUnitTest
```

JVM tests cover the agent loop (with scripted fake LLM), the approval gate,
SSE parsing, tool-call reassembly, output truncation, and every tool against a
fake backend. The wait tools run on virtual time, so their timeouts are tested
without sleeping.

## Security notes

- The API key is stored in `EncryptedSharedPreferences`.
- Tool output (logs, screen text, web pages, messages) is untrusted and may
  contain prompt injection. Write gating applies regardless of what the model
  claims.
- Tools that read private data (`query_*`, `clipboard_get`) or reach the network
  (`http_fetch`) are write-gated so an injected instruction cannot silently read
  or exfiltrate. The destructive or most sensitive ones can never be set to
  "always allow": `run_shell`, `write_file`, `install_apk`, `uninstall_app`,
  `query_sms`.
- File tools are confined to shared storage and `/data/local/tmp`; paths with
  `..` are rejected, so the shell user's wider access is not exposed.
- The overlay approval card needs the accessibility service, so it is only as
  trustworthy as that service being yours. With the service off, approvals stay
  inside Harness and a run that has left the app waits until you return.
- `highlight` is read-tier because it only draws. It cannot tap, and its window
  does not take touches.

## License

The code is MIT licensed, see [LICENSE](LICENSE).

The bundled fonts keep their own license, the SIL Open Font License 1.1:
Instrument Sans ([licenses/OFL-instrumentsans.txt](licenses/OFL-instrumentsans.txt))
and Martian Mono ([licenses/OFL-martianmono.txt](licenses/OFL-martianmono.txt)).
