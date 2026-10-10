# Android AI Harness — Design

## Goal
Personal Android app acting as an agent harness: chat with an LLM (OpenCode Go first) that can call native tools to inspect device settings, drive other apps, and debug issues.

## Decisions (agreed)
- Native Kotlin + Jetpack Compose, single module `app/`.
- Tool privilege: layered — Shizuku (shell uid) for shell tools, AccessibilityService for UI tools. Android 11+.
- Gating: tiered. READ tools auto-run; WRITE tools need in-app approve/deny with exact args, per-tool "always allow" (except `run_shell`).
- LLM: hand-rolled OpenAI-compatible chat-completions client with SSE streaming. Default base URL `https://opencode.ai/zen/go/v1`, Bearer key from opencode.ai/auth. Model + base URL configurable. Some Go models use `/responses` or `/messages` routes — out of scope for v1; pick a chat-completions model.
- Out of scope (YAGNI): MCP server, multi-provider UI, chat history DB, voice, root. `Tool` interface keeps MCP exposure possible later.

## Components
| Package | Responsibility |
|---|---|
| `ui/` | Compose chat screen, approval card, settings (key, model, base URL), Shizuku/Accessibility status banner |
| `agent/` | `AgentLoop`: messages + tool schemas → LLM → if `tool_calls`: gate → execute → append `role:tool` → repeat. Max steps (default 15), cancel button. |
| `llm/` | `LlmClient` interface; `OpenAiCompatClient` (OkHttp + kotlinx.serialization, SSE parser) |
| `tools/` | `Tool { name, description, jsonSchema, risk: READ/WRITE, run(args): String }`, registry, output truncation (~N KB) |
| `backend/` | `ShellBackend` (Shizuku UserService), `UiBackend` (AccessibilityService) |

## v1 tools
- Shell READ: `get_setting(ns,key)`, `logcat(filter,lines)`, `dumpsys(service)`, `list_packages`, `app_info(pkg)`, `getprop`- Shell WRITE: `put_setting`, `launch_app`, `force_stop`, `run_shell`
- Accessibility: `ui_tree` (READ), `screenshot` (READ), `tap`, `swipe`, `type_text`, `press_back`, `press_home` (WRITE)

## Data flow
user msg → AgentLoop → `LlmClient.stream()` (deltas to UI) → tool_calls → Gate (READ passes; WRITE suspends on UI approval via coroutine) → `Tool.run` on `Dispatchers.IO` → tool result message → loop. Ends on plain reply, max steps, or cancel. System prompt: device is Android; read before write; explain before mutating.

## Errors
- Tool failure → result string `ERROR: <msg>`; loop never crashes.
- Shizuku unavailable / permission missing, Accessibility off → tool returns clear message; UI banner shows fix.
- HTTP 401/429/5xx → shown in chat with manual retry; no silent retries.
- Approval denied → tool result `user denied`.

## Security
- API key in EncryptedSharedPreferences.
- `run_shell` always WRITE, never always-allow.
- Tool output (logs, screen text) is untrusted — may contain prompt injection; WRITE gating applies regardless of model reasoning.

## Testing
- JVM unit tests: AgentLoop with scripted fake `LlmClient`, Gate, SSE parser, truncation, tools vs fake backend.
- Manual device checklist in README: pair Shizuku, enable accessibility service, ask "what is my screen off timeout?".

## Open assumptions
- Personal sideload only (no Play Store policy concerns for Accessibility).
- Last session persisted to a file, not a DB.

## Verified on device (2026-10-10)
- OpenCode Go requires a client `User-Agent` and a stable `x-opencode-session` header, or requests are refused with a pointer to the Go docs. Implemented in `OpenAiCompatClient`.
- Model ids are hyphenated and per-endpoint (for example `glm-5.3-flash`, `deepseek-v4.1-flash` on `/chat/completions`). The default model must be a real chat-completions id.
- Tool result messages must not carry a `name` field; at least one endpoint rejects them with `[unsupported_parameter]`.
- `screen_timeout` is not a real Android setting key; use `screen_off_timeout`.
