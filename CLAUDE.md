# Grimoire

Local-first worldbuilding + writing desktop app. Kotlin Multiplatform (`jvm("desktop")` only),
Compose Multiplatform, SQLDelight (SQLite 3.38 dialect, FTS5). Offline-only by design for now.
Product notes and review rules: `PROXYAI.md`. Local design notes/teardowns live in `docs/` (gitignored, machine-local).

## Build

No `java`/`gradle` on PATH — always: `JAVA_HOME=~/.jdks/jdk-21.0.12+8 ./gradlew build` (or `run`, `desktopTest`).
Dev database: `~/.local/share/grimoire/grimoire.db` (per-OS path in `DriverFactory.appDataDir`).

## Layout (`src/commonMain/kotlin/dev/grimoire/`)

- `db/` — SQLDelight output + `FtsMaintenance` (raw FTS commands SQLDelight can't compile).
- `id/` — UUIDv7 `IdGenerator`. All ids come from here.
- `json/` — JSON-in-TEXT column helpers (kotlinx.serialization, no compiler plugin yet).
- `<domain>/` (`world`, `entity`, `relationship`, `work`, `timeline`, `template`, `variant`, `wiki`,
  `sync`, `net`, `export`, `settings`, `tutorial`) —
  one `XRepository(db, ids, clock)` per aggregate; suspend writes on `Dispatchers.IO`, reads exposed as
  SQLDelight `Flow`s so the UI is live without manual refresh. `work/Markdown.kt` is the one parser behind
  both the AO3 HTML export and the live preview. File I/O stays behind commonMain interfaces
  (`ExportSink`, `SettingsStore`) with impls in desktopMain.
- `ui/` — screens are dumb `@Composable`s over plain state-holder classes (`Session` → `WorkspaceState` →
  `EntityEditorState`) that hold `mutableStateOf` fields and are testable without Compose.
`desktopMain` holds `Main.kt`, `DriverFactory`, and the I/O impls (`MarkdownVaultSink`, `FileSettingsStore`).

## Rules

- Schema (`Grimoire.sq`) is the data model. Adding *queries* is routine; *table/column* changes are design
  decisions — raise them, don't slip them in, and never edit the schema just to make a build pass.
- Schema changes = new `N.sqm` migration + regenerated `databases/(N+1).db` snapshot (never overwrite a
  shipped snapshot) + a MigrationTest case against real v1-era data (snapshots have `user_version 0` —
  stamp it before opening). A migration that rebuilds `entity`/`chapter` must register the matching
  `FtsMaintenance::rebuild*` in `Migrations.afterVersion`; `AfterVersion(N)` fires after `N.sqm` is
  applied (the N→N+1 step), not "at version N".
- Writes to user content: debounce, but flush synchronously on navigation and on window close
  (`Session.flush()`); write paths are `NonCancellable`. Soft-delete only.
- No new runtime dependencies without flagging it. Currently: coroutines, serialization-json, sqldelight.
- Wayland blocks xdotool — verify UI with Compose UI tests (`WorkspaceScreenTest`), not by scripting clicks.
- Kotlin generates JVM setters for `private set` properties: name mutators `editX`/`searchFor`, not `setX`.
- Versioning: `VERSION` build counter + `version` in `build.gradle.kts` (see global rules).
- Before tagging a release: `./gradlew createDistributable` and launch
  `build/compose/binaries/main/app/grimoire/bin/grimoire` once. Packaged builds run on a trimmed
  jlink runtime (`modules(...)` in `build.gradle.kts`) — a missing module crashes only there, and
  Windows masks it as "Failed to launch JVM". After adding a dependency or new JDK API, check
  `./gradlew suggestRuntimeModules` (it can't see SPI-loaded modules like `jdk.crypto.ec` — keep
  that one listed regardless).
