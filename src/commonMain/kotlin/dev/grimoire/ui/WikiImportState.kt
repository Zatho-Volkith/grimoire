package dev.grimoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.grimoire.entity.EntityType
import dev.grimoire.wiki.WikiConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * State for the import-from-wiki dialog. Network calls never block the UI: [busy] disables
 * buttons while a call runs, failures land in [error] and cost nothing else (offline-first).
 */
class WikiImportState(
    private val worldId: String,
    private val wiki: WikiConnector,
    private val scope: CoroutineScope,
    private val onImported: (String) -> Unit,
) {
    var host by mutableStateOf(""); private set
    var query by mutableStateOf(""); private set
    var results by mutableStateOf<List<String>>(emptyList()); private set
    var selected by mutableStateOf<String?>(null); private set
    var type by mutableStateOf(EntityType.CONCEPT); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    /**
     * Accepts anything people actually paste: a bare host, a host with scheme, or a full
     * page URL — `https://cyberpunk.fandom.com/wiki/V_(character)` fills the host AND
     * selects the page title in one paste.
     */
    fun editHost(v: String) {
        val trimmed = v.trim().removePrefix("http://").removePrefix("https://")
        host = trimmed.substringBefore('/')
        val title = trimmed.substringAfter("/wiki/", missingDelimiterValue = "")
            .substringBefore('#').substringBefore('?')
        if (title.isNotEmpty()) {
            val readable = urlDecode(title).replace('_', ' ')
            query = readable
            selected = readable
            results = listOf(readable)
        }
    }

    fun editQuery(v: String) { query = v; selected = null }
    fun select(title: String) { selected = title }
    fun selectType(t: EntityType) { type = t }

    fun searchNow() {
        val h = host.trim().ifEmpty { return }
        scope.launch {
            busy = true; error = null
            runCatching { wiki.search(h, query.trim()) }
                .onSuccess { results = it; if (it.isEmpty()) error = "No matches on $h" }
                .onFailure { error = "Couldn't reach $h — offline, or not a MediaWiki host? (${it.message ?: it::class.simpleName})" }
            busy = false
        }
    }

    private fun urlDecode(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            if (s[i] == '%' && i + 2 < s.length) {
                s.substring(i + 1, i + 3).toIntOrNull(16)?.let { append(it.toChar()); i += 3 } ?: run { append(s[i]); i++ }
            } else { append(s[i]); i++ }
        }
    }

    fun importNow() {
        val h = host.trim()
        val title = selected ?: return
        scope.launch {
            busy = true; error = null
            runCatching { wiki.import(worldId, h, title, type) }
                .onSuccess { onImported(it) }
                .onFailure { error = it.message ?: "Import failed" }
            busy = false
        }
    }
}
