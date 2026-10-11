# Changelog

All notable Companion releases. Desktop Anya has its own changelog in [rururunu/Anya](https://github.com/rururunu/Anya).

## [0.1.9] - 2026-10-11

Improvements to inbox results, code reads, and defaults for new chats.

### Added

- Expand a successful code-read activity to see its captured snapshot; when the desktop supports `session.readCodePreview`, compare it with the current file while retaining the snapshot if the file changed
- Start new chats with compose and model defaults from the paired desktop, cached per device
- Show permission scopes as Read-only / Workspace-write / Full-access, with confirmation before enabling Full-access

### Improved and fixed

- Group inbox results by chat, show chat and result totals, refine the clear action and group cards, and fix clear confirmation doing nothing
- Avoid showing sessions from archived workspaces as extra workspace groups
- Refresh the app icon and launch animation, and update Android build tools

### Notes

Current-file comparison requires the desktop `session.readCodePreview` RPC. The captured snapshot remains available without it. Full settings and credentials are not synchronized to the phone.

## [0.1.8] - 2026-09-20

Parity with desktop Anya 0.2.23: Diff review, session/workspace management, Slash commands, environment context, and chart export.

### Added

- **Review changes**: top-bar entry with round / uncommitted / session scopes; Diff viewer renders unified only
- Long-press a chat to **rename**; long-press a workspace to **pin / rename**
- Type `/` for Slash commands (`/model` `/thinking` `/history` `/work` `/context` `/settings` `/clear` `/exit`)
- `/context` fetches live desktop environment context; message bubbles render image-analysis cards, selection cards, and foldable SoftInject
- Chart cards can **export PNG** for sharing

### Changed

- Liquid glass is one stack now: `backdrop` 1.0.2 → 1.0.6, the bottom nav's selected pill is a real glass layer that slides between items, and the chat top bar plus page chrome (settings, archived chats) refract through that same layer instead of a second blur library

### Notes

New RPCs (`session.rename`, `workspace.pin`, `workspace.update`, `workspace.workingTreeDiff`, `context.environment`) need desktop Anya ≥ **0.2.23**. Older desktops fail those actions with a message; other features keep working.

## [0.1.6] - 2026-09-03

Staged queue, in-app theme, and a fix for chats that went blank after the agent stopped.

### Added

- While the agent is running, sending a message stages it (same queue as the desktop). **引导** soft-injects now; when the turn ends the next staged message is sent automatically
- Theme in **Settings → General**: follow system, light, or dark
- Conversation cache hit rate in the context-usage sheet (DeepSeek prompt cache, same formula as desktop)
- A jelly-bounce haptic when you tap the mascot

### Fixed

- Chat history going empty after the agent finished, or after sitting in a thread: staged-queue RPCs return `{ messages: [] }` and were applied as session history
- Composer becoming a dead zone after attaching chips: the draft field is editable and scrollable again

### Notes

The staged queue needs desktop Anya ≥ 0.2.18 (`session.staged.*`). Theme and the empty-chat fix are phone-only.

## [0.1.5] - 2026-09-03

Image-mode toolbar, album reference images, and archive / inbox parity with desktop 0.2.18.

### Added

- Image mode: ratio, resolution, style, count, and switching the desktop image model (`imageGen.options` / `imageGen.setModel`)
- Gallery photos upload via `file.upload` and attach as `path:` references
- Inbox results grouped by conversation, with collapsible headers
- Archive a workspace (long-press the workspace row); the archive page matches the chat list (Ask / Workspace) and supports search

### Notes

Album references, image-gen params, and workspace archive need desktop Anya ≥ 0.2.18.

## [0.1.4] - 2026-09-02

New mascot everywhere, plus parity with desktop Anya 0.2.13–0.2.17: image mode, generated-image cards and Mermaid diagrams.

### Added

- Blob mascot ported from desktop: launcher / splash icon, connect and About pages, loading states and the empty conversation. It blinks, glances around, follows your finger, turns around when the agent state changes and beams when tapped; the empty chat shows it holding the current mode's prop (phone / wrench / clipboard / brush)
- **Image** chat mode (desktop `generate_image`). Selecting it no longer breaks compose sync with a desktop running 0.2.13+
- Generated-image tool cards: parse the desktop result, fetch the workspace copy through the gateway on tap and preview it fullscreen. `![alt](path:…)` images inside the reply become the same fetchable tiles instead of broken links
- ```` ```mermaid ```` fences render as pan / pinch / zoom diagrams themed to light and dark mode, with a source toggle

- Desktop parity for the remaining agent surfaces: ANSI-coloured terminal cards on a dark body, inline diff hunks with line numbers on file-edit cards (fullscreen viewer still one tap away), a sub-agent inspector sheet built from the parent tool's child activities, per-message DeepSeek cache hit rate, and turn rewind ("回退到此处" on user messages / "撤销本轮更改" under code changes) via the new `session.rewind` RPC
- "以此图编辑" and "选区涂抹" on cached images: paint a region, the phone builds the same dimmed spotlight preview as the desktop and attaches `![image](path:…)` so the desktop Images API edits the original

### Changed

- Agent turns are rendered like the desktop work timeline: reasoning, tool calls and narration interleave in the order they happened while streaming; once done, the preamble folds under "已完成" (tools · searches · thinking) while generated images and the final reply stay open. Non-showcase tools show as "变体 · 摘要" rows with expandable detail / result; sub-agent rows nest their child work; `update_tasks` / `todo_write` render as embedded task lists
- Turn footer mirrors the desktop: a small mascot narrating the current state (思考中 / 执行工具中 / 回复中 / 等待你的输入), "已处理 N s" live and after completion, "≈ N tokens" and the copy button. Transient statuses (compressing context, stream retry n/m, analyzing images) show as an activity line

### Fixed

- `hello.appVersion` now reports the installed version instead of a hard-coded `0.1.2`
- `chat.finished` no longer overrides a pending plan-gate / tool-approval state with Idle

### Notes

Pairs with desktop Anya ≥ 0.2.18: the gateway now sends `workTimeline`, `completedAtEpochMs`, `estimatedTokens`, `chat.status` and `chat.userContent`, and serves generated images from app-data / `peek-public` as well as workspaces. Against older desktops the timeline is reconstructed from live events only and quick-ask images cannot be fetched. Mermaid rendering loads the library from jsDelivr and needs network access.

## [0.1.3] - 2026-08-21

Chat now follows desktop model metadata more closely, with provider branding, per-model reasoning controls, and a live context estimate.

### Added

- Provider-grouped model picker with desktop-provided names and logos, plus built-in branding for common providers
- Per-model thinking controls: desktop thinking variants where available, otherwise a compatible reasoning-effort selector
- Context-usage meter that refreshes for the selected model and current draft

### Changed

- Persist and synchronize the selected reasoning effort with the desktop compose state
- Keep a locally selected reasoning effort when an older desktop response omits the field

### Notes

Requires a desktop Anya Remote Gateway that supports `context.usage` and compose `reasoningEffort`. Companion does not call model providers itself.

## [0.1.2] — 2026-08-15

Desktop → phone file fetch actually reaches the PC, including Windows paths.

### Fixed

- Rewrite desktop-minted download URLs (`http://127.0.0.1:8787/…`) onto the live LAN or tunnel origin
- Prefer a workspace-relative path for `file.download.begin`; fall back to `workspace.readFile` slices if HTTP begin fails
- Keep spaces in Windows `share_to_companion` paths instead of truncating at the first blank

### Notes

Requires desktop Anya with Remote Gateway. Companion does not call model providers itself.

## [0.1.1] — 2026-08-15

Multiple desktops, named hosts, and an inbox that still works after you leave the chat.

### Added

- Save multiple desktop hosts; switch from the Settings host list or by long-pressing the top-left logo
- Set a host display name (max 16 characters) while pairing; the top-bar “Anya” label uses that name
- Re-pair a saved host when its tunnel hostname, token, or other connection fields change (the local name is kept)
- Inbox **Results** for desktop `share_to_companion` files and links; background chunked download with a notification
- Inbox **Pending** still lists tool approvals and `ask_user` cards when you are not in that thread

### Notes

Requires desktop Anya with Remote Gateway. Companion does not call model providers itself.

## [0.1.0] — 2026-08-13

First public Android remote.

### Added

- Pair via desktop QR, manual host/token, or `anya://pair`
- Chat aligned with the PC: stream, cancel, Ask / Agent / Plan, model switch
- Tool approvals, `ask_user`, plan approve
- Workspace file catalog, skills / MCP lists
- Phone → desktop chunked upload and desktop → phone chunked download (500MB, 512KB slices)
- LAN `ws://` first, Cloudflare Quick Tunnel `wss://` fallback
- Boot cancel after 5s if `hello.ok` never arrives
- In-app updates from GitHub Releases (`latest.json`)
- Foreground keep-alive while connected

### Notes

Requires desktop Anya with Remote Gateway. Companion does not call model providers itself.

[0.1.9]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.9
[0.1.8]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.8
[0.1.6]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.6
[0.1.5]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.5
[0.1.4]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.4
[0.1.3]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.3
[0.1.2]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.2
[0.1.1]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.1
[0.1.0]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.0
