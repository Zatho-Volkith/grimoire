package dev.grimoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.grimoire.entity.EntityType
import dev.grimoire.relationship.RelationshipKind
import dev.grimoire.template.AutofillRule
import dev.grimoire.template.FieldType
import dev.grimoire.template.LinkDir
import dev.grimoire.template.LinkSpec
import dev.grimoire.template.TemplateField
import dev.grimoire.ui.design.ButtonKind
import dev.grimoire.ui.design.GButton
import dev.grimoire.ui.design.GField
import dev.grimoire.ui.design.GSelect
import dev.grimoire.ui.design.Radius
import dev.grimoire.ui.design.SectionLabel
import dev.grimoire.ui.design.Space
import dev.grimoire.ui.design.grimoire

private val fieldTypeLabels = mapOf(FieldType.TEXT to "Text", FieldType.LONG to "Long", FieldType.LINK to "Link")

/**
 * One field of a template. Collapsed: label (click to expand), type, group, remove. Expanded: the label
 * editor, the controls its type calls for, and its autofill rules.
 */
@Composable
internal fun TemplateFieldRow(state: TemplateEditorState, f: TemplateField, expanded: Boolean, onToggle: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(
            if (expanded) MaterialTheme.grimoire.selected else Color.Transparent, RoundedCornerShape(Radius.field),
        ).padding(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(
                Modifier.weight(1f).testTag("field-row-${f.key}").clickable(onClick = onToggle).padding(vertical = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text(if (expanded) "▾" else "▸", color = MaterialTheme.grimoire.muted)
                Text(f.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Picker(
                fieldTypeLabels.getValue(f.type), FieldType.entries.map { fieldTypeLabels.getValue(it) to it },
                Modifier.width(110.dp).testTag("field-type-${f.key}"),
            ) { state.editFieldType(f.key, it) }
            GroupPicker(state, f)
            GButton("✕", { state.removeField(f.key) }, kind = ButtonKind.QUIET)
        }
        if (expanded) FieldDetail(state, f)
    }
}

@Composable
private fun FieldDetail(state: TemplateEditorState, f: TemplateField) {
    Column(
        Modifier.padding(start = Space.xl).testTag("field-detail-${f.key}"),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        GField(f.label, { state.renameField(f.key, it) }, Modifier.width(320.dp), label = "Field label")
        when (f.type) {
            FieldType.TEXT -> {
                // Raw text is held locally: the state re-splits on every edit, which would eat a trailing comma.
                var raw by remember(f.key) { mutableStateOf(f.options.joinToString(", ")) }
                GField(
                    raw, { raw = it; state.editFieldOptions(f.key, it) }, Modifier.width(400.dp),
                    label = "Options (comma-separated)", fieldModifier = Modifier.testTag("field-options-${f.key}"),
                )
            }
            FieldType.LINK -> f.link?.let { LinkControls(state, f, it) }
            FieldType.LONG -> Unit
        }
        AutofillRules(state, f)
    }
}

@Composable
private fun GroupPicker(state: TemplateEditorState, f: TemplateField) {
    var open by remember { mutableStateOf(false) }
    val query = f.group.orEmpty()
    val shown = state.groups.filter { it != query && it.contains(query, ignoreCase = true) }
    Box(Modifier.width(160.dp)) {
        GField(
            query, { state.editFieldGroup(f.key, it); open = true }, placeholder = "Group",
            fieldModifier = Modifier.testTag("field-group-${f.key}"),
        )
        DropdownMenu(expanded = open && shown.isNotEmpty(), onDismissRequest = { open = false }) {
            shown.forEach { g -> DropdownMenuItem(text = { Text(g) }, onClick = { state.editFieldGroup(f.key, g); open = false }) }
        }
    }
}

@Composable
private fun LinkControls(state: TemplateEditorState, f: TemplateField, link: LinkSpec) {
    val preset = RelationshipKind.entries.firstOrNull { it.key == link.kind && it != RelationshipKind.CUSTOM }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Picker(
            preset?.key ?: "custom", RelationshipKind.entries.map { it.key to it },
            Modifier.width(150.dp).testTag("field-kind-${f.key}"), label = "Kind",
        ) { k -> state.editFieldLink(f.key, link.copy(kind = if (k == RelationshipKind.CUSTOM) "custom" else k.key)) }
        if (preset == null) {
            GField(link.kind, { state.editFieldLink(f.key, link.copy(kind = it)) }, Modifier.width(150.dp), label = "Custom kind")
        }
        Picker(
            if (link.dir == LinkDir.OUT) "Out" else "In", listOf("Out" to LinkDir.OUT, "In" to LinkDir.IN),
            Modifier.width(100.dp), label = "Direction",
        ) { state.editFieldLink(f.key, link.copy(dir = it)) }
        Picker(
            link.targetType?.let { EntityType.fromKey(it).label } ?: "Any",
            listOf<Pair<String, EntityType?>>("Any" to null) + EntityType.entries.map { it.label to it },
            Modifier.width(140.dp), label = "Target type",
        ) { state.editFieldLink(f.key, link.copy(targetType = it?.key, targetTemplate = null)) }
        val candidates = state.worldTemplates.filter { link.targetType == null || it.entity_type == link.targetType }
        val current = candidates.firstOrNull { it.builtin_key == link.targetTemplate || it.id == link.targetTemplate }
        Picker(
            if (link.targetTemplate == null) "None" else current?.name ?: link.targetTemplate,
            listOf<Pair<String, String?>>("None" to null) + candidates.map { it.name to (it.builtin_key ?: it.id) },
            Modifier.width(190.dp), label = "Target template",
        ) { state.editFieldLink(f.key, link.copy(targetTemplate = it)) }
    }
}

@Composable
private fun AutofillRules(state: TemplateEditorState, f: TemplateField) {
    val linkFields = state.fields.filter { it.type == FieldType.LINK && it.key != f.key }
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        if (f.autofill.isNotEmpty() || linkFields.isNotEmpty()) SectionLabel("Autofill")
        f.autofill.forEachIndexed { i, rule ->
            fun replace(r: AutofillRule) = state.editFieldAutofill(f.key, f.autofill.mapIndexed { j, old -> if (j == i) r else old })
            val via = linkFields.firstOrNull { it.key == rule.via }
            val sourceFields = state.targetFields(via?.link?.targetTemplate)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Picker(via?.label ?: rule.via, linkFields.map { it.label to it.key }, Modifier.width(190.dp), label = "Autofill via") {
                    replace(rule.copy(via = it))
                }
                if (sourceFields.isEmpty()) {
                    GField(rule.from, { replace(rule.copy(from = it)) }, Modifier.width(190.dp), label = "From field")
                } else {
                    Picker(
                        sourceFields.firstOrNull { it.key == rule.from }?.label ?: rule.from,
                        sourceFields.map { it.label to it.key }, Modifier.width(190.dp), label = "From field",
                    ) { replace(rule.copy(from = it)) }
                }
                GButton("Remove rule", { state.editFieldAutofill(f.key, f.autofill.filterIndexed { j, _ -> j != i }) }, kind = ButtonKind.QUIET)
            }
        }
        if (linkFields.isNotEmpty()) {
            GButton(
                "Add autofill rule", { state.editFieldAutofill(f.key, f.autofill + AutofillRule(linkFields.first().key, "")) },
                kind = ButtonKind.QUIET,
            )
        }
    }
}

/** Read-only dropdown over (label, value) options. */
@Composable
private fun <T> Picker(shown: String, options: List<Pair<String, T>>, modifier: Modifier, label: String? = null, onPick: (T) -> Unit) {
    GSelect(shown, modifier, label = label) { close ->
        options.forEach { (text, v) -> DropdownMenuItem(text = { Text(text) }, onClick = { onPick(v); close() }) }
    }
}
