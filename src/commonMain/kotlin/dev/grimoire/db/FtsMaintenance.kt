package dev.grimoire.db

import app.cash.sqldelight.db.SqlDriver

/**
 * FTS5 maintenance commands for `entity_fts`. These are raw SQL because SQLDelight cannot
 * compile the `INSERT INTO fts(fts) VALUES('command')` form as a named query.
 */
object FtsMaintenance {
    /** Re-derives the whole index from `entity`. Run after any migration that rebuilds the entity table, an import, or a restore. */
    fun rebuild(driver: SqlDriver) {
        driver.execute(null, "INSERT INTO entity_fts(entity_fts) VALUES ('rebuild')", 0)
    }

    /** Throws (SQLITE_CORRUPT_VTAB) if the index disagrees with the `entity` content table. */
    fun integrityCheck(driver: SqlDriver) {
        driver.execute(null, "INSERT INTO entity_fts(entity_fts, rank) VALUES ('integrity-check', 1)", 0)
    }
}
