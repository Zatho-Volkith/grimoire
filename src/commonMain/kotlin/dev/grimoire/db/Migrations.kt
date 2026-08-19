package dev.grimoire.db

import app.cash.sqldelight.db.AfterVersion

/**
 * Hooks that run after the database is migrated *to* a given version — the place for work SQL
 * migrations can't express. Every migration that rebuilds `entity` (CREATE new / INSERT SELECT /
 * DROP / RENAME) renumbers rowids and MUST register `FtsMaintenance::rebuild` here for its version.
 *
 * Migration files themselves live in `src/commonMain/sqldelight/dev/grimoire/db/N.sqm`
 * (N = version being migrated FROM); frozen schema snapshots in `sqldelight/databases/N.db`.
 */
object Migrations {
    val afterVersion: Array<AfterVersion> = arrayOf(
        // AfterVersion(2) { FtsMaintenance.rebuild(it) },
    )
}
