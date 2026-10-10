package dev.grimoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.grimoire.db.LinksFor
import dev.grimoire.template.TemplateField
import dev.grimoire.template.TemplateFields
import dev.grimoire.ui.design.ButtonKind
import dev.grimoire.ui.design.GButton
import dev.grimoire.ui.design.GCard
import dev.grimoire.ui.design.GField
import dev.grimoire.ui.design.GSelect
import dev.grimoire.ui.design.GTextArea
import dev.grimoire.ui.design.ListRow
import dev.grimoire.ui.design.PageHeader
import dev.grimoire.ui.design.Radius
import dev.grimoire.ui.design.SectionLabel
import dev.grimoire.ui.design.Space
import dev.grimoire.ui.design.Widths
import dev.grimoire.ui.design.grimoire

/** The entity editor: a width-capped stack of cards under a page header with the editable name. */
@Composable
fun EntityEditor(state: EntityEditorState, onDelete: () -> Unit, onOpenEntity: (String) -> Unit, onOpenChapter: (String, String) -> Unit) {
    if (!state.loaded) return
    val links by state.links.collectAsState(initial = emptyList())
    val options by state.templateOptions.collectAsState(initial = emptyList())
    val template = options.firstOrNull { it.id == state.templateId }
    val fields = remember(template?.fields) { template?.let { TemplateFields.decode(it.fields) }.orEmpty() }

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = Widths.editor).fillMaxWidth()
                .padding(Space.xl).testTag("editor-content"),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            PageHeader(
                titleField = {
                    GField(
                        state.name, state::editName, fieldModifier = Modifier.testTag("page-title"),
                        placeholder = "Name", textStyle = MaterialTheme.typography.headlineMedium,
                    )
                },
                subtitle = listOfNotNull(state.type.label, state.sourceLabel?.let { "Canon: $it" }).joinToString(" · "),
            ) {
                Text(
                    if (state.dirty) "Unsaved" else "Saved",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.grimoire.muted,
                )
                GButton("Delete", onDelete, kind = ButtonKind.QUIET)
            }
            if (state.showCanonDiff) CanonDiffDialog(state)

            OverviewCard(state, options.map { it.id to it.name }, template?.name)
            FieldCards(state, fields, links, onOpenEntity)
            GCard(Modifier.testTag("card-notes"), title = "Notes") {
                GTextArea(state.body, state::editBody, Modifier.fillMaxWidth(), placeholder = "Notes", minLines = 12)
            }
            RelationshipsPanel(state, onOpenEntity)
            AppearancesCard(state, onOpenChapter)
            VariantsCard(state)
        }
    }
}

@Composable
private fun OverviewCard(state: EntityEditorState, templates: List<Pair<String, String>>, templateName: String?) {
    GCard(Modifier.testTag("card-overview"), title = "Overview") {
        state.sourceLabel?.let { CanonStrip(state, it) }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            if (state.assignedSourceId != null) SourceAssignSelect(state, Modifier.weight(1f))
            GSelect(templateName ?: "None", Modifier.weight(1f), label = "Template") { close ->
                DropdownMenuItem(text = { Text("None") }, onClick = { state.selectTemplate(null); close() })
                templates.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { state.selectTemplate(id); close() })
                }
            }
        }
        GField(state.aliasesText, state::editAliasesText, Modifier.fillMaxWidth(), label = "Aliases (comma-separated)")
        GTextArea(state.summary, state::editSummary, Modifier.fillMaxWidth(), label = "Summary", minLines = 2)
    }
}

/** Thin accent strip for wiki-sourced entities: where canon came from, drift, refresh. */
@Composable
private fun CanonStrip(state: EntityEditorState, label: String) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.grimoire.selected, RoundedCornerShape(Radius.field))
            .padding(horizontal = Space.md, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text("Canon: $label", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        state.canonStatus?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.grimoire.muted) }
        if (state.differsFromCanon) GButton("differs from canon — compare", state::openCanonDiff, kind = ButtonKind.QUIET)
        GButton("Refresh canon", state::refreshCanon, kind = ButtonKind.QUIET)
    }
}

/** One card per template group, ungrouped fields first in an untitled card. */
@Composable
private fun FieldCards(state: EntityEditorState, fields: List<TemplateField>, links: List<LinksFor>, onOpenEntity: (String) -> Unit) {
    if (fields.isEmpty()) return
    val ungrouped = fields.filter { it.group == null }
    if (ungrouped.isNotEmpty()) {
        GCard(Modifier.testTag("card-group-fields")) {
            ungrouped.forEach { FieldEditor(state, it, links, onOpenEntity) }
        }
    }
    fields.mapNotNull { it.group }.distinct().forEach { g ->
        GCard(Modifier.testTag("card-group-$g")) {
            Text(g, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("field-group"))
            fields.filter { it.group == g }.forEach { FieldEditor(state, it, links, onOpenEntity) }
        }
    }
}

/** Chapters that cast this entity. Managed from the chapter side; rows jump straight there. */
@Composable
private fun AppearancesCard(state: EntityEditorState, onOpenChapter: (String, String) -> Unit) {
    val rows by state.appearances.collectAsState(initial = emptyList())
    if (rows.isEmpty()) return

    GCard(Modifier.testTag("card-appears"), title = "Appears in") {
        rows.forEach { a ->
            ListRow(
                a.work_title, { onOpenChapter(a.work_id, a.chapter_id) },
                Modifier.testTag("appearance-link"), secondary = a.chapter_title ?: "Untitled chapter",
            )
        }
    }
}

/**
 * Per-work / per-continuity overrides of this entity. Blank override fields inherit canon.
 * The schema allows one live variant per scope; the work picker hides already-used works.
 */
@Composable
private fun VariantsCard(state: EntityEditorState) {
    val rows by state.variants.collectAsState(initial = emptyList())
    val workOptions by state.workOptions.collectAsState(initial = emptyList())

    GCard(
        Modifier.testTag("card-variants"), title = "Variants",
        caption = if (rows.isEmpty()) "Canon everywhere — no overrides yet." else null,
    ) {
        rows.forEach { v ->
            val editing = state.variantEditor?.id == v.id
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(v.work_title?.let { "In “$it”" } ?: "AU: ${v.continuity_key}", style = MaterialTheme.typography.bodyMedium)
                v.name_override?.let { Text("as “$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.grimoire.muted) }
                Box(Modifier.weight(1f))
                GButton(if (editing) "Done" else "Edit", { if (editing) state.closeVariant() else state.openVariant(v.id) }, kind = ButtonKind.QUIET)
                GButton("Remove", { state.removeVariant(v.id) }, kind = ButtonKind.QUIET)
            }
            if (editing) state.variantEditor?.let { ed -> VariantFields(ed) }
        }
        AddVariantForm(state, alreadyUsedWorkIds = rows.mapNotNull { it.work_id }.toSet(), workOptions.map { it.id to it.title })
    }
}

@Composable
private fun VariantFields(ed: VariantEditorState) {
    if (!ed.loaded) return
    Column(Modifier.padding(start = Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        GField(ed.nameOverride, ed::editNameOverride, Modifier.fillMaxWidth(), label = "Name override (blank = canon)")
        GTextArea(ed.summaryOverride, ed::editSummaryOverride, Modifier.fillMaxWidth(), label = "Summary override", minLines = 2)
        GTextArea(ed.bodyOverride, ed::editBodyOverride, Modifier.fillMaxWidth(), label = "Notes override", minLines = 4)
        if (ed.patchKeys.isNotEmpty()) {
            SectionLabel("Attribute overrides (blank = inherit)")
            ed.patchKeys.forEach { key ->
                GField(
                    ed.patch[key].orEmpty(), { v -> ed.editPatch(key, v) }, Modifier.fillMaxWidth(),
                    label = key, placeholder = ed.parentAttributes[key],
                )
            }
        }
    }
}

@Composable
private fun AddVariantForm(state: EntityEditorState, alreadyUsedWorkIds: Set<String>, works: List<Pair<String, String>>) {
    var continuity by remember { mutableStateOf("") }
    val available = works.filter { it.first !in alreadyUsedWorkIds }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        GSelect("New variant for work…", Modifier.weight(1f)) { close ->
            if (available.isEmpty()) DropdownMenuItem(text = { Text("No works without a variant") }, onClick = close)
            available.forEach { (id, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { state.addVariant(id, null); close() })
            }
        }
        GField(continuity, { continuity = it }, Modifier.weight(1f), placeholder = "…or continuity key")
        GButton("Add AU", { state.addVariant(null, continuity.trim()); continuity = "" }, enabled = continuity.isNotBlank())
    }
}

/** Files a user-authored entity under one of the world's named sources. */
@Composable
private fun SourceAssignSelect(state: EntityEditorState, modifier: Modifier) {
    val options by state.sourceOptions.collectAsState(initial = emptyList())
    val current = options.firstOrNull { it.id == state.assignedSourceId }

    GSelect(current?.label ?: "…", modifier, label = "Source") { close ->
        options.forEach { s ->
            DropdownMenuItem(text = { Text(s.label) }, onClick = { state.assignSource(s.id); close() })
        }
    }
}

