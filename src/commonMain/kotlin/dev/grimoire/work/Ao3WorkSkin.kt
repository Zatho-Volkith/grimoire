package dev.grimoire.work

/** Text-conversation colours (ARGB), shared by the AO3 Work Skin and the Compose preview. */
object TextsPalette {
    const val SENT_BG = 0xFF0B5FCC
    const val SENT_TEXT = 0xFFFFFFFF
    const val RECEIVED_BG = 0xFFE9E9EB
    const val RECEIVED_TEXT = 0xFF1C1C1E
    const val CHAT_BG = 0xFFFFFFFF
    const val BORDER = 0xFFD1D1D6
    const val META_TEXT = 0xFF6C6C70
}

/**
 * The Work Skin that styles [Ao3Markdown]'s `gt-` conversation markup. Pasted once into AO3
 * (Dashboard → Skins → Create Work Skin), then selected on each work that has texts.
 *
 * Stays inside AO3's skin whitelist: no comments, no `var()`, no `font` shorthand, one
 * declaration per property per rule. Every colour is explicit so dark site skins can't
 * leave white text on a white bubble.
 */
object Ao3WorkSkin {

    val css: String = with(TextsPalette) {
        listOf(
            ".gt-chat" to "display: flex; flex-direction: column; max-width: 400px; margin: 1.5em auto; padding: 0.75em; " +
                "background-color: ${hex(CHAT_BG)}; border: 1px solid ${hex(BORDER)}; border-radius: 1.25em;",
            ".gt-chat p" to "margin: 0.2em 0;",
            ".gt-header" to "align-self: stretch; text-align: center; font-weight: bold; color: ${hex(RECEIVED_TEXT)}; " +
                "padding-bottom: 0.5em; margin-bottom: 0.5em; border-bottom: 1px solid ${hex(BORDER)};",
            ".gt-msg" to "max-width: 75%; padding: 0.45em 0.8em; border-radius: 1.1em; line-height: 1.35;",
            ".gt-them" to "align-self: flex-start; background-color: ${hex(RECEIVED_BG)}; color: ${hex(RECEIVED_TEXT)};",
            ".gt-me" to "align-self: flex-end; background-color: ${hex(SENT_BG)}; color: ${hex(SENT_TEXT)};",
            ".gt-me a" to "color: ${hex(SENT_TEXT)};",
            ".gt-name" to "display: none;",
            ".gt-group .gt-them .gt-name" to "display: block; font-size: 0.75em; font-weight: bold; color: ${hex(META_TEXT)};",
            ".gt-receipt" to "align-self: flex-end; font-size: 0.75em; color: ${hex(META_TEXT)};",
            ".gt-notice" to "align-self: center; text-align: center; font-size: 0.8em; color: ${hex(META_TEXT)};",
        ).joinToString("\n") { (selector, body) -> "#workskin $selector { $body }" }
    }

    private fun hex(argb: Long): String = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()
}
