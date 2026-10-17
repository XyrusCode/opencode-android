# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.5.1] - 2026-10-17

### Fixed
- In-app updates could never work: every release was signed with a different
  key, so Android treated each build as a separate app. Builds were cached as
  `~/.android/debug.keystore`, but Gradle resolves that directory from the SDK
  home, so the cache never found or saved anything and each run invented a new
  key. The key now comes from a repository secret, written before each build, and
  a release build without it fails instead of quietly signing differently.
  **You will need to uninstall the current app once before this version will
  install.** After that, updates work normally.
- Pairing could fail for reasons the screen never explained. A remote address
  typed without a scheme was forced to `http://`, which an HTTPS-only remote
  cannot serve. Addresses now default to HTTPS, staying on HTTP only for
  loopback and private LAN hosts. Pasting the whole pair link works in either
  field, and a rejected code is now distinguished from an unreachable address.

## [0.5.0] - 2026-10-17

### Fixed
- Sessions started in the opencode desktop app now appear on the phone. The two
  versions keep separate session tables in the same database, and the app was
  connected to the older server, which only ever saw the older table. It now
  talks to opencode's current API directly, so every session is visible.
- Live streaming works against the current API, including tool calls and
  permission prompts.

### Added
- Connect straight to opencode with either a pairing code (`opencode pair`,
  including `--remote`) or a host and password. Servers are saved so you only
  pair once, and the connect screen shows how long a pairing stays valid.
- The desktop bridge is still available as a fallback from the connect screen,
  so existing setups keep working.

## [0.4.3] - 2026-10-17

### Added
- Sessions can be renamed and deleted from the drawer. Each session row has a
  menu with Rename (inline text field) and Delete (with a confirmation, since
  opencode removes it and its messages for good). Renames and deletes are
  broadcast, so every paired phone updates without re-fetching the list.
- The updater now asks for the permissions it silently needed. Android 13+
  requires a notification permission before download progress or the
  ready-to-install notice can be shown, and Android 8+ refuses to install an APK
  until you allow the app to install unknown apps. Previously the download
  succeeded and tapping Install did nothing, which is why no install prompt ever
  appeared.

## [0.4.2] - 2026-10-17

### Fixed
- Sending a message to an older session could wedge it in a permanent "working"
  state. Sessions carry the model and agent they were last driven with, and those
  are now restored when you open one, so a prompt is never sent without a model.
  As a backstop the bridge fills in the session's own model and agent if the
  phone has none selected.
- One wedged session no longer blocks every other session. Switching sessions now
  clears the stuck working state instead of leaving the composer disabled
  app-wide.
- The drawer's "Settings" label was not tappable; only the gear icon was. The
  whole row is now one tap target.

### Changed
- All projects now appear in the drawer. The bridge previously capped the session
  list at the 50 newest overall, so one busy project crowded others out of the
  list entirely; it now returns everything opencode holds (327 sessions across 29
  project folders here) in a single request.
- Each project previews its 5 most recent sessions. Searching narrows to matching
  sessions before the cap, so a session older than the preview is still findable.

## [0.4.1] - 2026-10-17

### Fixed
- The in-app updater had no visible Download button: the screen did not scroll
  and the changelog pushed the button below the fold. It now scrolls, the primary
  action sits above a truncated (tap to expand) changelog, and a copyable direct
  download link is included as a fallback.
- Installing an update did nothing on Android 11+ (package-visibility made the
  installer lookup fail); the updater now launches the installer directly and
  reports a real error instead of failing silently.
- The drawer no longer shows throwaway sessions that ran in temp directories.

### Changed
- The bridge now sends up to 50 sessions spanning every project (previously 25,
  all scoped to the bridge's own working directory), so the drawer lists your real
  projects with their sessions and you can continue existing work.

## [0.4.0] - 2026-10-17

### Fixed
- Project navigation actually works now. The list showed opencode worktrees
  ("global", git-hash ids) that matched no sessions, so selecting one looked
  empty. Projects are derived from each session's real working directory.
- The app no longer draws under the status and navigation bars; content is
  constrained to the safe area like ChatGPT and Claude.

### Changed
- Renamed the app to **OpenCompanion** (it pairs to opencode rather than being
  opencode); the opencode wordmark and icon remain as the paired-tool branding.
- Navigation drawer is now Antigravity-style: New Conversation, search, and real
  projects that expand to reveal their sessions with relative ages.
- The composer project picker lists real project directories and creates new
  sessions in the picked project.

## [0.3.0] - 2026-10-17

### Changed
- Full UI overhaul to feel like a real AI harness (ChatGPT / Claude pattern)
  with opencode branding throughout.
- New theme: opencode's real palette (warm near-black, cream ink, peach primary,
  purple secondary) and bundled IBM Plex Mono for a monospace-first look,
  replacing the previous Video-Plucker glass styling.
- Chat transcript: user messages are right-aligned bubbles, assistant messages
  are full-width, and tool/file/diff parts are stacked cards with type icons.
- New pinned composer with opencode's placeholder, a circular send that becomes
  Stop while streaming, and model/agent/project chips attached beneath it.
- Conversation drawer for navigating chats (projects, search, Today/Older).

### Added
- Official opencode wordmark as the adaptive launcher icon.
- Markdown rendering for assistant text: fenced code blocks, bold, inline code
  and bullets, via a small hand-rolled renderer.

## [0.2.4] - 2026-10-16

### Fixed
- In-app updates failed with a signature/package error. Release builds use the
  debug signing config and CI generated a fresh debug keystore every run, so no
  two APKs shared a signature. CI now caches the keystore. The first install of
  this build must be done manually (uninstall the previous app once); after that
  the in-app updater works.

### Added
- A spinner on the connect button while the socket opens and authenticates.
- Search in the model, agent and project pickers.
- Project-first session list: sessions inside the selected project, grouped Today
  and Older, and searchable by title.
- The opencode wordmark shown when a new session has no messages yet.

## [0.2.3] - 2026-10-16

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
