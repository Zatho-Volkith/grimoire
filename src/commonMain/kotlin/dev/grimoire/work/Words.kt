package dev.grimoire.work

object Words {
    private val whitespace = Regex("\\s+")

    /**
     * Word count the way writers expect: whitespace-separated tokens that contain at least one
     * letter or digit, so Markdown furniture (`---`, `>`, `**`) doesn't inflate the number.
     */
    fun count(text: String): Long =
        text.splitToSequence(whitespace).count { token -> token.any(Char::isLetterOrDigit) }.toLong()
}
