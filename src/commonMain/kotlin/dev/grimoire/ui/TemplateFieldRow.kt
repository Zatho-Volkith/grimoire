package dev.grimoire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.grimoire.entity.EntityType
import dev.grimoire.relationship.RelationshipKind
import dev.grimoire.template.AutofillRule
import dev.grimoire.template.FieldType
import dev.grimoire.template.LinkDir
import dev.grimoire.template.LinkSpec
import dev.grimoire.template.TemplateField

private val fieldTypeLabels = mapOf(FieldType.TEXT to "Text", FieldType.LONG to "Long", FieldType.LINK to "Link")

/** One field of a template: label/type/group, then the controls its type calls for, then autofill rules. */
@Composable
internal fun TemplateFieldRow(state: TemplateEditorState, f: TemplateField) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                f.label, { state.renameField(f.key, it) },
                label = { Text("Field label") }, singleLine = true,
                modifier = Modifier.width(260.dp),
            )
            Picker(
                "Type", fieldTypeLabels.getValue(f.type), FieldType.entries.map { fieldTypeLabels.getValue(it) to it },
                Modifier.width(120.dp).testTag("field-type-${f.key}"),
            ) { state.editFieldType(f.key, it) }
            GroupPicker(state, f)
            TextButton(onClick = { state.removeField(f.key) }) { Text("Remove") }
        }
        when (f.type) {
            FieldType.TEXT -> OutlinedTextField(
                f.options.joinToString(", "), { state.editFieldOptions(f.key, it) },
                label = { Text("Options (comma-separated)") }, singleLine = true,
                modifier = Modifier.width(400.dp).padding(start = 16.dp),
            )
            FieldType.LINK -> f.link?.let { LinkControls(state, f, it) }
            FieldType.LONG -> Unit
        }
        AutofillRules(state, f)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupPicker(state: TemplateEditorState, f: TemplateField) {
    var open by remember { mutableStateOf(false) }
    val query = f.group.orEmpty()
    val shown = state.groups.filter { it != query && it.contains(query, ignoreCase = true) }
    ExposedDropdownMenuBox(expanded = open && shown.isNotEmpty(), onExpandedChange = { open = it }, modifier = Modifier.width(180.dp)) {
        OutlinedTextField(
            query, { state.editFieldGroup(f.key, it); open = true },
            label = { Text("Group") }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open && shown.isNotEmpty()) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).testTag("field-group-${f.key}"),
        )
        ExposedDropdownMenu(expanded = open && shown.isNotEmpty(), onDismissRequest = { open = false }) {
            shown.forEach { g -> DropdownMenuItem(text = { Text(g) }, onClick = { state.editFieldGroup(f.key, g); open = false }) }
        }
    }
}

@Composable
private fun LinkControls(state: TemplateEditorState, f: TemplateField, link: LinkSpec) {
    val preset = RelationshipKind.entries.firstOrNull { it.key == link.kind && it != RelationshipKind.CUSTOM }
    Row(
        Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Picker(
            "Kind", preset?.key ?: "custom", RelationshipKind.entries.map { it.key to it },
            Modifier.width(160.dp).testTag("field-kind-${f.key}"),
        ) { k -> state.editFieldLink(f.key, link.copy(kind = if (k == RelationshipKind.CUSTOM) "custom" else k.key)) }
        if (preset == null) {
            OutlinedTextField(
                link.kind, { state.editFieldLink(f.key, link.copy(kind = it)) },
                label = { Text("Custom kind") }, singleLine = true, modifier = Modifier.width(160.dp),
            )
        }
        Picker(
            "Direction", if (link.dir == LinkDir.OUT) "Out" else "In", listOf("Out" to LinkDir.OUT, "In" to LinkDir.IN),
            Modifier.width(110.dp),
        ) { state.editFieldLink(f.key, link.copy(dir = it)) }
        Picker(
            "Target type", link.targetType?.let { EntityType.fromKey(it).label } ?: "Any",
            listOf<Pair<String, EntityType?>>("Any" to null) + EntityType.entries.map { it.label to it },
            Modifier.width(150.dp),
        ) { state.editFieldLink(f.key, link.copy(targetType = it?.key, targetTemplate = null)) }
        val candidates = state.worldTemplates.filter { link.targetType == null || it.entity_type == link.targetType }
        val current = candidates.firstOrNull { it.builtin_key == link.targetTemplate || it.id == link.targetTemplate }
        Picker(
            "Target template", if (link.targetTemplate == null) "None" else current?.name ?: link.targetTemplate,
            listOf<Pair<String, String?>>("None" to null) + candidates.map { it.name to (it.builtin_key ?: it.id) },
            Modifier.width(200.dp),
        ) { state.editFieldLink(f.key, link.copy(targetTemplate = it)) }
    }
}

@Composable
private fun AutofillRules(state: TemplateEditorState, f: TemplateField) {
    val linkFields = state.fields.filter { it.type == FieldType.LINK && it.key != f.key }
    Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        f.autofill.forEachIndexed { i, rule ->
            fun replace(r: AutofillRule) = state.editFieldAutofill(f.key, f.autofill.mapIndexed { j, old -> if (j == i) r else old })
            val via = linkFields.firstOrNull { it.key == rule.via }
            val sourceFields = state.targetFields(via?.link?.targetTemplate)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(
                    "Autofill via", via?.label ?: rule.via, linkFields.map { it.label to it.key },
                    Modifier.width(200.dp),
                ) { replace(rule.copy(via = it)) }
                if (sourceFields.isEmpty()) {
                    OutlinedTextField(
                        rule.from, { replace(rule.copy(from = it)) },
                        label = { Text("From field") }, singleLine = true, modifier = Modifier.width(200.dp),
                    )
                } else {
                    Picker(
                        "From field", sourceFields.firstOrNull { it.key == rule.from }?.label ?: rule.from,
                        sourceFields.map { it.label to it.key }, Modifier.width(200.dp),
                    ) { replace(rule.copy(from = it)) }
                }
                TextButton(onClick = { state.editFieldAutofill(f.key, f.autofill.filterIndexed { j, _ -> j != i }) }) { Text("Remove rule") }
            }
        }
        if (linkFields.isNotEmpty()) {
            TextButton(onClick = {
                state.editFieldAutofill(f.key, f.autofill + AutofillRule(linkFields.first().key, ""))
            }) { Text("Add autofill rule", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

/** Read-only dropdown over (label, value) options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Picker(label: String, shown: String, options: List<Pair<String, T>>, modifier: Modifier, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            shown, {}, readOnly = true, label = { Text(label) }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, v) -> DropdownMenuItem(text = { Text(text) }, onClick = { onPick(v); open = false }) }
        }
    }
}
