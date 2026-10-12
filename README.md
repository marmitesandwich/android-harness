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

## Tools

41 tools. **Read** tools run automatically. **Write** tools show an approve/deny
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
10. Check the tools that parse device output on your Android version. These
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
| `backend/` | `ShellBackend` (Shizuku), `UiBackend` (AccessibilityService), clipboard, notes store, HTTP fetcher |

The agent loop sends the conversation to the model, runs any requested tools
after gating, appends `role: tool` results, and repeats until the model replies
without tool calls, calls `done`, the step budget (default 40) is exhausted, or
you press **Stop**. Each step is logged under the `Harness` tag, and the chat
warns when a run is close to the cap.

`tap_element`, `find_element`, `wait_for_ui` and `scroll_until` work by parsing
the `ui_tree` text dump, so they need no extra accessibility plumbing.

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

## License

The code is MIT licensed, see [LICENSE](LICENSE).

The bundled fonts keep their own license, the SIL Open Font License 1.1:
Instrument Sans ([licenses/OFL-instrumentsans.txt](licenses/OFL-instrumentsans.txt))
and Martian Mono ([licenses/OFL-martianmono.txt](licenses/OFL-martianmono.txt)).
