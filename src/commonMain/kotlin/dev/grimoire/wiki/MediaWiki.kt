package dev.grimoire.wiki

import dev.grimoire.net.Fetcher
import dev.grimoire.net.urlEncode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One fetched wiki page: identity, provenance, and the raw material the importer needs. */
data class WikiPage(
    val host: String,
    val title: String,
    val wikitext: String,
    val revisionId: Long,
    val pageUrl: String,
    val rawResponse: String,
)

/**
 * Thin MediaWiki API client. Both supported hosts — `<name>.fandom.com` and `<name>.wiki.gg` —
 * are stock MediaWiki, so one `/api.php` surface covers them.
 */
class MediaWiki(private val fetcher: Fetcher) {

    /** Title suggestions for [query], best first. */
    suspend fun search(host: String, query: String): List<String> {
        if (query.isBlank()) return emptyList()
        val url = "https://$host/api.php?action=opensearch&search=${urlEncode(query)}&limit=10&format=json"
        val body = fetcher.getText(url)
        return Json.parseToJsonElement(body).jsonArray[1].jsonArray.map { it.jsonPrimitive.content }
    }

    /** The page's current wikitext + revision, or null if the wiki has no such page. */
    suspend fun fetchPage(host: String, title: String): WikiPage? {
        val url = "https://$host/api.php?action=query&titles=${urlEncode(title)}" +
            "&prop=revisions&rvslots=main&rvprop=content%7Cids%7Ctimestamp&format=json&formatversion=2"
        val body = fetcher.getText(url)
        val page = Json.parseToJsonElement(body).jsonObject
            .getValue("query").jsonObject
            .getValue("pages").jsonArray
            .firstOrNull()?.jsonObject ?: return null
        if (page["missing"]?.jsonPrimitive?.content == "true") return null
        val revision = page["revisions"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val canonicalTitle = page.getValue("title").jsonPrimitive.content
        return WikiPage(
            host = host,
            title = canonicalTitle,
            wikitext = revision.getValue("slots").jsonObject.getValue("main").jsonObject.getValue("content").jsonPrimitive.content,
            revisionId = revision.getValue("revid").jsonPrimitive.content.toLong(),
            pageUrl = "https://$host/wiki/${canonicalTitle.replace(' ', '_')}",
            rawResponse = body,
        )
    }
}
