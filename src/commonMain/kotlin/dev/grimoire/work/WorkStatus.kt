package dev.grimoire.work

/** Preset vocabulary for `work.status`. Stored as [key]; the column stays free text. */
enum class WorkStatus(val key: String, val label: String) {
    DRAFT("draft", "Draft"),
    IN_PROGRESS("in_progress", "In progress"),
    COMPLETE("complete", "Complete"),
    POSTED("posted", "Posted");

    companion object {
        fun fromKey(key: String): WorkStatus = entries.firstOrNull { it.key == key } ?: DRAFT
    }
}
