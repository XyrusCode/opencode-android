# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
