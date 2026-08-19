# Grimoire

Local-first worldbuilding + writing desktop app. Kotlin Multiplatform (`jvm("desktop")` only),
Compose Multiplatform, SQLDelight (SQLite 3.38 dialect, FTS5). Offline-only by design for now.
Product notes and review rules: `PROXYAI.md`. Reference-app teardowns: `docs/reference/`.

## Build

No `java`/`gradle` on PATH — always: `JAVA_HOME=~/.jdks/jdk-21.0.12+8 ./gradlew build` (or `run`, `desktopTest`).
Dev database: `~/.local/share/grimoire/grimoire.db` (per-OS path in `DriverFactory.appDataDir`).

## Layout (`src/commonMain/kotlin/dev/grimoire/`)

- `db/` — SQLDelight output + `FtsMaintenance` (raw FTS commands SQLDelight can't compile).
- `id/` — UUIDv7 `IdGenerator`. All ids come from here.
- `json/` — JSON-in-TEXT column helpers (kotlinx.serialization, no compiler plugin yet).
- `<domain>/` (`world`, `entity`, …) — one `XRepository(db, ids, clock)` per aggregate; suspend writes on
  `Dispatchers.IO`, reads exposed as SQLDelight `Flow`s so the UI is live without manual refresh.
- `ui/` — screens are dumb `@Composable`s over plain state-holder classes (`Session` → `WorkspaceState` →
  `EntityEditorState`) that hold `mutableStateOf` fields and are testable without Compose.
`desktopMain` holds only `Main.kt` and `DriverFactory`.

## Rules

- Schema (`Grimoire.sq`) is the data model. Adding *queries* is routine; *table/column* changes are design
  decisions — raise them, don't slip them in, and never edit the schema just to make a build pass.
- Schema changes = new `N.sqm` migration + regenerated `databases/(N+1).db` snapshot (never overwrite a
  shipped snapshot) + test; the build verifies migrations replay to match `Grimoire.sq`. A migration that
  rebuilds `entity` must register `FtsMaintenance::rebuild` in `Migrations.afterVersion`.
- Writes to user content: debounce, but flush synchronously on navigation and on window close
  (`Session.flush()`); write paths are `NonCancellable`. Soft-delete only.
- No new runtime dependencies without flagging it. Currently: coroutines, serialization-json, sqldelight.
- Wayland blocks xdotool — verify UI with Compose UI tests (`WorkspaceScreenTest`), not by scripting clicks.
- Kotlin generates JVM setters for `private set` properties: name mutators `editX`/`searchFor`, not `setX`.
- Versioning: `VERSION` build counter + `version` in `build.gradle.kts` (see global rules).
