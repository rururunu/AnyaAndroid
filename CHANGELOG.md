# Changelog

All notable Companion releases. Desktop Anya has its own changelog in [rururunu/Anya](https://github.com/rururunu/Anya).

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

[0.1.2]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.2
[0.1.1]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.1
[0.1.0]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.0
