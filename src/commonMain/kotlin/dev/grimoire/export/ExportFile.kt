package dev.grimoire.export

/** One file of an export: [path] segments relative to the vault root (already sanitized), plus content. */
data class ExportFile(val path: List<String>, val content: String)

/** Receives a finished export. Implementations own the "where" (dialogs, disk) — see desktopMain. */
fun interface ExportSink {
    /**
     * Writes [files] under a folder named for [worldName] at a user-chosen location.
     * Returns the destination to show the user, or null if they cancelled.
     */
    suspend fun write(worldName: String, files: List<ExportFile>): String?
}

/** Filesystem- and wikilink-safe naming shared by the exporter and the writers. */
object VaultNames {
    private val illegal = """\/:*?"<>|[]#^""".toSet()

    fun sanitize(name: String): String =
        name.map { if (it in illegal || it.code < 32) ' ' else it }.joinToString("")
            .replace(Regex("\\s+"), " ").trim().trimEnd('.').take(80).trim()
            .ifEmpty { "Untitled" }

    /** Returns [base] or "base 2", "base 3", … — first variant not yet in [used] (case-insensitive). */
    fun uniquify(base: String, used: MutableSet<String>): String {
        if (used.add(base.lowercase())) return base
        var n = 2
        while (!used.add("$base $n".lowercase())) n++
        return "$base $n"
    }
}
