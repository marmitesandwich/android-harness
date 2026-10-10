# Android AI Harness

A personal Android app that acts as an agent harness: chat with an LLM that can
call native tools to inspect device settings, drive other apps, and debug issues.

The model proposes tool calls; the app executes them on-device with a tiered
approval gate. Nothing leaves the device except the LLM conversation itself.

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
   chat-completions model for your provider. The default base URL is
   `https://opencode.ai/zen/go/v1`.

The banner disappears when all three prerequisites are satisfied.

## Tools

| Shell (read) | Shell (write) | Accessibility |
|---|---|---|
| `get_setting` | `put_setting` | `ui_tree` (read) |
| `logcat` | `launch_app` | `screenshot` (read) |
| `dumpsys` | `force_stop` | `tap` |
| `list_packages` | `run_shell` | `swipe` |
| `app_info` | | `type_text` |
| `getprop` | | `press_back`, `press_home` |

Read tools run automatically. Write tools require approve/deny in-app, with a
per-tool "always allow" except `run_shell`, which always asks.

## Manual device checklist

Run after installing:

1. Pair Shizuku and confirm `get_setting(system, screen_timeout)` returns a value
   when you ask "what is my screen timeout?".
2. Enable accessibility, then ask "what is on my screen?" and confirm the agent
   calls `ui_tree`.
3. Ask the agent to change a setting (e.g. screen timeout) and confirm the
   approval card appears with the exact arguments before anything changes.
4. Tap **Deny** once and confirm the tool result is `user denied` and no change
   is made.
5. Ask the agent to run `run_shell` twice and confirm it prompts both times.
6. Turn off Shizuku and confirm shell tools return a clear message instead of
   crashing.

## Architecture

| Package | Responsibility |
|---|---|
| `ui/` | Compose chat screen, approval card, settings, status banner |
| `agent/` | `AgentLoop`, tiered `ApprovalGate` |
| `llm/` | `LlmClient` + `OpenAiCompatClient` (OkHttp SSE) |
| `tools/` | `Tool` interface, `ToolRegistry`, shell + UI tools |
| `backend/` | `ShellBackend` (Shizuku), `UiBackend` (AccessibilityService) |

The agent loop sends the conversation to the model, runs any requested tools
after gating, appends `role: tool` results, and repeats until the model replies
without tool calls, the step budget (default 15) is exhausted, or you press
**Stop**.

## Testing

```powershell
./gradlew :app:testDebugUnitTest
```

JVM tests cover the agent loop (with scripted fake LLM), the approval gate,
SSE parsing, tool-call reassembly, output truncation and the shell/UI tools
against a fake backend.

## Security notes

- The API key is stored in `EncryptedSharedPreferences`.
- Tool output (logs, screen text) is untrusted and may contain prompt
  injection. Write gating applies regardless of what the model claims.
