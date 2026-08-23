package dev.grimoire.work

object Words {
    /**
     * Word count the way writers expect: whitespace-separated tokens that contain at least one
     * letter or digit, so Markdown furniture (`---`, `>`, `**`) doesn't inflate the number.
     * Single allocation-free pass — it runs on every keystroke of the chapter editor.
     */
    fun count(text: String): Long {
        var count = 0L
        var inWord = false
        var wordCounts = false
        for (c in text) {
            if (c.isWhitespace()) {
                if (wordCounts) count++
                inWord = false
                wordCounts = false
            } else {
                inWord = true
                if (!wordCounts && c.isLetterOrDigit()) wordCounts = true
            }
        }
        if (wordCounts) count++
        return count
    }
}
