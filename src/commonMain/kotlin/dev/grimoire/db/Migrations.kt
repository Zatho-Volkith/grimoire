package dev.grimoire.db

import app.cash.sqldelight.db.AfterVersion

/**
 * Hooks that run after a given migration file is applied (AfterVersion(N) fires after N.sqm) — the place for work SQL
 * migrations can't express. Every migration that rebuilds `entity` (CREATE new / INSERT SELECT /
 * DROP / RENAME) renumbers rowids and MUST register `FtsMaintenance::rebuild` here for its version.
 *
 * Migration files themselves live in `src/commonMain/sqldelight/dev/grimoire/db/N.sqm`
 * (N = version being migrated FROM); frozen schema snapshots in `sqldelight/databases/N.db`.
 */
object Migrations {
    val afterVersion: Array<AfterVersion> = arrayOf(
        // 1.sqm added chapter_fts as external content — populate it from existing chapters.
        // NB: AfterVersion(N) fires after N.sqm is applied (the N -> N+1 step), not "at version N".
        AfterVersion(1) { FtsMaintenance.rebuildChapters(it) },
    )
}
