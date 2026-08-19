package dev.grimoire.entity

/**
 * Turns free text into a safe FTS5 MATCH expression: every whitespace-separated token becomes a
 * quoted prefix term, implicitly AND-ed. Quoting means user input can never be parsed as FTS
 * syntax (`NEAR`, `OR`, column filters, unbalanced quotes).
 *
 * `kae nor` → `"kae"* "nor"*`. Returns null when there is nothing to search for.
 */
fun ftsQuery(raw: String): String? =
    raw.split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { "\"" + it.replace("\"", "\"\"") + "\"*" }
        .ifEmpty { null }
