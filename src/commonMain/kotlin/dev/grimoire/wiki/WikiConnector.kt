package dev.grimoire.wiki

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator
import dev.grimoire.json.JsonStringMap
import dev.grimoire.net.Fetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports wiki pages as entities and keeps their canon fresh.
 *
 * Model: an import is a COPY into the user's world — one `source` row per page (kind
 * `fandom_wiki`, carrying url/revision/raw payload) and one entity whose `canon_snapshot`
 * holds the wiki's version. User columns start as a copy of canon and then belong to the
 * user; [refresh] only ever touches the snapshot and the source row.
 */
class WikiConnector(
    private val db: GrimoireDatabase,
    private val ids: IdGenerator,
    private val clock: () -> Long,
    fetcher: Fetcher,
) {
    private val api = MediaWiki(fetcher)
    private val q get() = db.grimoireQueries

    suspend fun search(host: String, query: String): List<String> = api.search(host, query)

    /** Fetches [title] from [host] and creates source + entity. Returns the new entity's id. */
    suspend fun import(worldId: String, host: String, title: String, type: EntityType): String =
        withContext(Dispatchers.IO) {
            val page = api.fetchPage(host, title) ?: error("No page named “$title” on $host")
            val canon = canonOf(page)
            val now = clock()
            val sourceId = ids.next()
            val entityId = ids.next()
            db.transaction {
                q.insertWikiSource(
                    id = sourceId, world_id = worldId, kind = "fandom_wiki",
                    label = "$host · ${page.title}", remote_url = page.pageUrl, remote_id = page.title,
                    revision = page.revisionId.toString(), fetched_at = now, cached_payload = page.rawResponse,
                    created_at = now, updated_at = now,
                )
                q.insertEntity(
                    id = entityId, world_id = worldId, source_id = sourceId, template_id = null,
                    type = type.key, name = canon.name, aliases = "[]",
                    summary = canon.summary, body = canon.body, image_path = null, color = null,
                    attributes = JsonStringMap.encode(canon.attributes),
                    canon_snapshot = CanonSnapshot.encode(canon),
                    created_at = now, updated_at = now,
                )
            }
            entityId
        }

    /** [PAGE_MISSING]: the wiki no longer has the page; canon is kept as last fetched. */
    enum class RefreshResult { UPDATED, UNCHANGED, PAGE_MISSING, NOT_WIKI_SOURCED }

    /** Re-fetches the entity's page. Only `canon_snapshot` and the source row change — never user text. */
    suspend fun refresh(entityId: String): RefreshResult = withContext(Dispatchers.IO) {
        val entity = q.entityById(entityId).executeAsOneOrNull()
        if (entity == null || entity.deleted_at != null) return@withContext RefreshResult.NOT_WIKI_SOURCED
        val source = q.sourceById(entity.source_id).executeAsOneOrNull()
        if (source?.kind != "fandom_wiki" || source.remote_id == null || source.remote_url == null) {
            return@withContext RefreshResult.NOT_WIKI_SOURCED
        }
        val host = source.remote_url.removePrefix("https://").substringBefore('/')
        val page = api.fetchPage(host, source.remote_id)
        val now = clock()
        // Unchanged or gone: just record the check, so the page waits a full day like any other
        // instead of failing (and retrying) forever.
        if (page == null || page.revisionId.toString() == source.revision) {
            q.updateSourceFetch(
                revision = source.revision, fetched_at = now, cached_payload = source.cached_payload,
                updated_at = now, id = source.id,
            )
            return@withContext if (page == null) RefreshResult.PAGE_MISSING else RefreshResult.UNCHANGED
        }
        val canon = canonOf(page)
        db.transaction {
            q.updateSourceFetch(
                revision = page.revisionId.toString(), fetched_at = now, cached_payload = page.rawResponse,
                updated_at = now, id = source.id,
            )
            q.updateEntityCanon(canon_snapshot = CanonSnapshot.encode(canon), updated_at = now, id = entityId)
        }
        RefreshResult.UPDATED
    }

    private fun canonOf(page: WikiPage): CanonSnapshot {
        val markdown = Wikitext.toMarkdown(page.wikitext)
        return CanonSnapshot(
            name = page.title,
            summary = Wikitext.firstParagraph(markdown).ifBlank { null },
            body = markdown.ifBlank { null },
            attributes = Wikitext.infobox(page.wikitext),
        )
    }
}
