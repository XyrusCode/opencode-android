# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.2] - 2026-10-16

### Added
- Rich response rendering: tool-call cards, file/snapshot/patch diffs and error
  parts now arrive from the bridge as structured parts and render as expandable
  cards instead of flat text lines.
- Model, agent and project pickers, backed by the catalogs the bridge pushes on
  connect. The selection is re-sent with every message so it applies per-message
  (the only setter current opencode builds support).
- Interactive permission prompts (once / always / reject) and question prompts
  (single- and multi-select) surface on the phone, so an agent turn can finish
  while the laptop is locked away.
- In-app self-update: pulls a `version.json` manifest and an APK from R2,
  downloads crash-safely and installs via FileProvider. Reachable from a settings
  action on the pairing screen.
- Video-Plucker glass UI kit (GlassCard, GlowButton, SectionHeader, gradient
  download bar) and its red-accent theme.

### Fixed
- The ported gradient progress indicator could not compile: it tried to read
  colors back off a `Brush`, which exposes none. It now takes a `List<Color>`.
- The R2 publish path and the updater disagreed on the APK name; CI now strips
  the leading `v` so the published key matches what the app requests.

## [0.2.0] - 2026-10-15

### Added
- Real bridge transport: the desktop plugin now speaks opencode's HTTP + SSE
  server API instead of piping text into the TUI's stdin.
- Session picker and attach, so a phone can continue a session started on the
  desktop, with its history loaded into the transcript.
- Live token streaming: assistant output arrives as deltas and lands on a
  single in-progress line.
- Cleartext traffic allowed for LAN addresses, which `ws://` requires.
- Unit tests for the wire-frame parser.

### Fixed
- Build was broken and could not have compiled: the plugin block lived in the
  module rather than the root, there was no root build file, and no
  repositories were declared, so Gradle could not resolve the Android plugin.
- Duplicate `MainActivity` declarations in two packages.
- Manifest used `android.intent.action.MAIN_ACTION`, which is not a real action,
  and pointed at resources that did not exist.
- Release build referenced a signing config that was never defined.

### Changed
- Release APK is signed with the debug key so it is installable for testing.
  Replace with a real keystore before distributing.

## [0.1.0] - 2026-10-14

### Added
- Initial scaffolding: pairing screen, Compose theme, WebSocket client.
