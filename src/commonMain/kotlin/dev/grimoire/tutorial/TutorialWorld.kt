package dev.grimoire.tutorial

import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.relationship.RelationshipKind
import dev.grimoire.relationship.RelationshipRepository
import dev.grimoire.template.TemplateRepository
import dev.grimoire.timeline.TimelineRepository
import dev.grimoire.variant.VariantRepository
import dev.grimoire.work.WorkRepository
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.flow.first

/**
 * The optional guided tour: a fully populated world whose WORK is the manual — you read the
 * guide inside the real editor, with the live preview rendering it, surrounded by example
 * entities, relationships, timelines, and a variant you can poke. Entirely disposable:
 * everything it creates soft-deletes like normal content.
 */
object TutorialWorld {

    suspend fun create(
        worlds: WorldRepository,
        entities: EntityRepository,
        relationships: RelationshipRepository,
        works: WorkRepository,
        timeline: TimelineRepository,
        templates: TemplateRepository,
        variants: VariantRepository,
    ): String {
        val w = worlds.create("Grimoire Tutorial")

        // --- cast of examples -------------------------------------------------
        val characterSheet = templates.byBuiltinKey("character")?.id

        val mira = entities.create(w, EntityType.CHARACTER, "Mira Vale")
        entities.updateContent(
            mira, "Mira Vale", aliases = listOf("The Cartographer"),
            summary = "A mapmaker who charts places that refuse to stay put.",
            body = "Mira's maps are argued over in every port tavern — because the *Driftlands* move, and her maps move with them.\n\n" +
                "Look right below the name field: this entity uses the built-in **Character sheet** template, " +
                "and its fields (Age, Occupation, …) are stored as attributes. Try changing the template dropdown, or edit a field.",
            templateId = characterSheet,
            attributes = mapOf(
                "age" to "29", "occupation" to "Mapmaker",
                "wants" to "To chart the Driftlands", "fears" to "Open water",
            ),
        )

        val josselin = entities.create(w, EntityType.CHARACTER, "Josselin Reed")
        entities.updateContent(
            josselin, "Josselin Reed", aliases = emptyList(),
            summary = "A lighthouse keeper who writes letters Mira never has an address for.",
            body = "Notice this entity's **Source** dropdown says *Headcanon* — sources are named layers you file entities under. " +
                "Use the Source filter on the entity list to see only one layer at a time.",
            templateId = null, attributes = emptyMap(),
        )
        val headcanon = entities.createSource(w, "Headcanon")
        entities.setSource(josselin, headcanon)

        val guild = entities.create(w, EntityType.FACTION, "The Wayfinders' Guild")
        entities.updateContent(
            guild, "The Wayfinders' Guild", aliases = emptyList(),
            summary = "Charters every expedition worth insuring.",
            body = null, templateId = null, attributes = emptyMap(),
        )
        val port = entities.create(w, EntityType.LOCATION, "Port Ambry")
        entities.updateContent(
            port, "Port Ambry", aliases = emptyList(),
            summary = "Last solid ground before the Driftlands.",
            body = null, templateId = null, attributes = emptyMap(),
        )

        relationships.create(w, mira, guild, RelationshipKind.MEMBER_OF.key, "member of", "has member")
        relationships.create(w, mira, josselin, RelationshipKind.PARTNER_OF.key, "partner of", "partner of")
        relationships.create(w, guild, port, RelationshipKind.LOCATED_IN.key, "located in", "contains")

        // --- the guide itself: a work whose chapters teach ---------------------
        val tour = works.create(w, "The Guided Tour")
        val ch1 = works.chapters(tour).first().single().id
        works.updateChapter(ch1, "Start here", CH1)
        val ch2 = works.addChapter(tour); works.updateChapter(ch2, "Writing", CH2)
        val ch3 = works.addChapter(tour); works.updateChapter(ch3, "Entities & connections", CH3)
        val ch4 = works.addChapter(tour); works.updateChapter(ch4, "Timelines", CH4)
        val ch5 = works.addChapter(tour); works.updateChapter(ch5, "Canon from wikis", CH5)
        val ch6 = works.addChapter(tour); works.updateChapter(ch6, "Variants, export, goodbye", CH6)

        works.addAppearance(ch3, mira)
        works.addAppearance(ch3, josselin)

        // --- two calendars, as promised in chapter 4 ---------------------------
        val founding = timeline.create(w, "Founding of Port Ambry")
        timeline.updateEvent(founding, "Founding of Port Ambry", "Built on the last rock that stays where you left it.", "Year 312", null)
        val charter = timeline.create(w, "The Guild charter is signed")
        timeline.updateEvent(charter, "The Guild charter is signed", null, "Year 340", null)
        timeline.addParticipant(charter, guild, null)
        val mapping = timeline.create(w, "Mira maps the Shallows")
        timeline.updateEvent(mapping, "Mira maps the Shallows", "Her first impossible map.", "Year 361", null)
        timeline.addParticipant(mapping, mira, "cartographer")

        val opening = timeline.create(w, "Opening scene — the storm", "Story order")
        timeline.updateEvent(opening, "Opening scene — the storm", "Where the tour's story would begin.", null, null)
        timeline.create(w, "Mira takes the job", "Story order")

        // --- one variant, so the panel has something to show -------------------
        variants.create(mira, tour, null)?.let { v ->
            variants.updateContent(
                v, nameOverride = "Mira the Unmapped", summaryOverride = null, bodyOverride = null,
                attributePatch = mapOf("age" to "31"),
            )
        }

        return w
    }

    private val CH1 = """
        Welcome to **Grimoire** — a local-first home for worldbuilding and the writing that grows out of it. Everything lives on this machine; nothing needs a connection.

        This whole world is a guided tour. Poke anything. When you're done, delete it — the **Trash** section keeps everything recoverable.

        ## The rail on the left

        - **Entity types** (Characters, Locations, …) — your worldbuilding notes
        - **Works** — your actual writing (you're reading one now)
        - **Timeline** — what happened, in the order you decide
        - **Templates** — the field sheets entities use
        - **Trash** — nothing is ever gone

        ## Keyboard shortcuts

        - **Ctrl+1…5** switch sections · **Ctrl+N** new item · **Ctrl+F** search entities
        - **Ctrl+S** save now · **Esc** close the open editor

        Saving is automatic — watch the *Saved / Unsaved* label while you type. Closing the window also saves. And try the **Theme** menu in the header: four looks, remembered between launches.

        Next chapter: the editor you're already inside. →
    """.trimIndent()

    private val CH2 = """
        You're reading Markdown in the chapter editor — the same format Obsidian speaks. The pane to the right is the **live preview**; it re-renders as you type (there's a *Hide preview* toggle when you want the room back).

        *Italics*, **bold**, ~~strikethrough~~, [links](https://archiveofourown.org), lists:

        - a scene beat
        - another beat

        > Blockquotes make good epigraphs.
        > — someone quotable

        A line with three dashes becomes a scene break:

        ---

        The word count above ignores Markdown punctuation — writers count words, not asterisks.

        ## Getting it out

        **Copy AO3 HTML** converts this exact Markdown into the HTML AO3's editor accepts — paste it straight into a work form. **Export** (in the header) writes the whole world as an Obsidian-ready vault of .md files, wikilinks and all.

        ## Text conversations

        Click **Text convo** to write a text-message exchange row by row — Enter starts the next message, and the sender flips to whoever's replying. Put your cursor inside one of these and click it again to edit:

        ::: texts Josselin
        @me Mira
        Mira: the coastline moved again
        Josselin: *again?* that's twice this week
        Mira: tell the lighthouse to hold still
        ~ Read 11:52 PM
        :::

        Group chats name each sender automatically:

        ::: texts Wayfinders' Guild
        @me Mira
        -- Harbourmaster added Josselin to the chat
        Harbourmaster: who moved the Shallows
        Josselin: not me, I only keep the light
        Mira: I'll draw a new map
        :::

        On AO3, bubbles need a **Work Skin**: click **Copy Work Skin CSS**, paste it into AO3 under *Dashboard → Skins → Create Work Skin* once, then pick that skin under *Select Work Skin* when you post. Readers who turn creator styles off still see a plain, readable chat log.

        Chapters reorder with *Move up / Move down*, and **Search all chapters** (in the Works list) digs through everything you've written.
    """.trimIndent()

    private val CH3 = """
        Look under this chapter's title: **Mira Vale** and **Josselin Reed** are in its *Cast*. Click a name to jump to the character; on their page, an *Appears in* row jumps back here. That's how "every scene she's in" stays answerable.

        ## On an entity's page

        - **Relationships** — typed, two-ended: Mira is *member of* the Wayfinders' Guild, and on the Guild's page that same edge reads *has member*.
        - **Templates & attributes** — Mira uses the built-in Character sheet; the fields you see are yours to change, and Templates section lets you design your own sheets.
        - **Sources** — Josselin files under *Headcanon*. Sources are provenance layers ("my notes" vs "fandom consensus" vs wiki imports); the entity list filters by them.

        **Ctrl+F** searches every entity's name, aliases, summary, and notes at once.
    """.trimIndent()

    private val CH4 = """
        Open the **Timeline** section: three events sit on the *default* calendar in hand-picked order — no real dates required, just *Move up / Move down* and a free-text "When" ("Year 312" here, "Third Age" wherever you like).

        Now use the **Calendar** dropdown: this world also has a **Story order** calendar — the same world, a second chronology. World-history order and telling order rarely agree; keep both.

        Events carry **participants**: "Mira maps the Shallows" lists Mira *(cartographer)* — click her name to jump to her page.
    """.trimIndent()

    private val CH5 = """
        The Entities section has an **Import from wiki** button. Point it at any `name.fandom.com` or `name.wiki.gg` wiki, search a page, and it becomes an entity — infobox turned into attributes, article turned into Markdown notes.

        ## The two layers

        Imports are **copies with provenance**. The wiki's version becomes **canon** — a layer you can never accidentally edit. Your copy starts identical and then belongs to you.

        - Edit an imported entity and a *differs from canon* button appears — it opens a field-by-field **compare** where you can take canon back, one field at a time. Your words are never overwritten without a click.
        - In the background, Grimoire re-checks stale pages about once a day (the header shows *Canon: N queued* while it works). Offline? Nothing happens, nothing breaks — it retries later.

        Fanfic writers pull their fandom's canon. Original-fiction writers raid any wiki for concepts — superpowers, monster anatomy — as referenced research notes beside their own creations.
    """.trimIndent()

    private val CH6 = """
        One last trick: open **Mira Vale** and scroll to **Variants**. In *this* work she's "Mira the Unmapped", age 31 — a per-fic override of canon. Name, summary, notes, and individual attributes can each diverge; blank means inherit. "In my fic, she never left the village" is a first-class feature.

        ## Where to go from here

        - **Export** this world and open the folder in Obsidian — frontmatter, wikilinks, graph view.
        - Make your own world from the Worlds screen, attach your fandom's wiki, and start writing.
        - Then send this tutorial world to the **Trash** (or keep it as a scratchpad — it's yours).

        Happy writing. 🖋
    """.trimIndent()
}
