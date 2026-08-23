package dev.grimoire.wiki

/**
 * Line-level diff for the canon compare view: everything unchanged disappears, leaving only
 * the passages where canon and the user's text disagree. Common prefix/suffix are trimmed
 * before the LCS so typical one-paragraph edits cost almost nothing; pathologically large
 * disagreements degrade to a single blob hunk instead of an O(n·m) table.
 */
object TextDiff {

    /** One disagreement: [canon] lines vs [yours] lines (either side may be empty). */
    data class Hunk(val canon: List<String>, val yours: List<String>)

    fun hunks(canonText: String, yourText: String): List<Hunk> {
        val a = canonText.lines()
        val b = yourText.lines()

        var start = 0
        while (start < a.size && start < b.size && a[start] == b[start]) start++
        var endA = a.size
        var endB = b.size
        while (endA > start && endB > start && a[endA - 1] == b[endB - 1]) { endA--; endB-- }

        val ca = a.subList(start, endA)
        val cb = b.subList(start, endB)
        if (ca.isEmpty() && cb.isEmpty()) return emptyList()
        if (ca.size.toLong() * cb.size > 1_000_000L) return listOf(Hunk(ca, cb))

        val dp = Array(ca.size + 1) { IntArray(cb.size + 1) }
        for (i in ca.indices.reversed()) {
            for (j in cb.indices.reversed()) {
                dp[i][j] = if (ca[i] == cb[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }

        val hunks = mutableListOf<Hunk>()
        var canonRun = mutableListOf<String>()
        var yourRun = mutableListOf<String>()
        fun flush() {
            // A hunk of only blank lines is formatting noise, not a disagreement worth showing.
            if ((canonRun + yourRun).any { it.isNotBlank() }) hunks += Hunk(canonRun, yourRun)
            canonRun = mutableListOf()
            yourRun = mutableListOf()
        }

        var i = 0
        var j = 0
        while (i < ca.size || j < cb.size) {
            when {
                i < ca.size && j < cb.size && ca[i] == cb[j] -> { flush(); i++; j++ }
                j >= cb.size || (i < ca.size && dp[i + 1][j] >= dp[i][j + 1]) -> { canonRun += ca[i]; i++ }
                else -> { yourRun += cb[j]; j++ }
            }
        }
        flush()
        return hunks
    }
}
