package dev.grimoire.db

import app.cash.sqldelight.db.SqlDriver

/**
 * FTS5 maintenance commands. Raw SQL because SQLDelight cannot compile the
 * `INSERT INTO fts(fts) VALUES('command')` form as a named query.
 */
object FtsMaintenance {
    /** Re-derives entity_fts from `entity`. Run after any migration that rebuilds the entity table, an import, or a restore. */
    fun rebuildEntities(driver: SqlDriver) {
        driver.execute(null, "INSERT INTO entity_fts(entity_fts) VALUES ('rebuild')", 0)
    }

    /** Re-derives chapter_fts from `chapter` — also how migration 1->2 populates the new index. */
    fun rebuildChapters(driver: SqlDriver) {
        driver.execute(null, "INSERT INTO chapter_fts(chapter_fts) VALUES ('rebuild')", 0)
    }

    /** Throws (SQLITE_CORRUPT_VTAB) if either index disagrees with its content table. */
    fun integrityCheck(driver: SqlDriver) {
        driver.execute(null, "INSERT INTO entity_fts(entity_fts, rank) VALUES ('integrity-check', 1)", 0)
        driver.execute(null, "INSERT INTO chapter_fts(chapter_fts, rank) VALUES ('integrity-check', 1)", 0)
    }
}
