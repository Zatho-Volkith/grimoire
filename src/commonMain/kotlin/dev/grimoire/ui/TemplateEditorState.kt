package dev.grimoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.grimoire.entity.EntityType
import dev.grimoire.db.Template
import dev.grimoire.template.AutofillRule
import dev.grimoire.template.FieldType
import dev.grimoire.template.LinkDir
import dev.grimoire.template.LinkSpec
import dev.grimoire.template.TemplateField
import dev.grimoire.template.TemplateFields
import dev.grimoire.template.TemplateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Editing session for one template: its name and its ordered field list, saved together
 * under the usual debounce+flush contract. Field keys are minted once from the label at
 * add time and never change, so renaming a label can't orphan entity values.
 */
class TemplateEditorState(
    val id: String,
    private val worldId: String,
    private val repo: TemplateRepository,
    private val scope: CoroutineScope,
) {
    var loaded by mutableStateOf(false); private set
    var entityType: EntityType = EntityType.CHARACTER; private set
    var name by mutableStateOf(""); private set
    var fields by mutableStateOf<List<TemplateField>>(emptyList()); private set
    var bodyScaffold by mutableStateOf(""); private set
    /** The world's live templates (plus shared built-ins), for the link target-template picker. */
    var worldTemplates by mutableStateOf<List<Template>>(emptyList()); private set
    var dirty by mutableStateOf(false); private set

    private var saveJob: Job? = null

    suspend fun load() {
        val t = repo.template(id).first() ?: return
        entityType = EntityType.fromKey(t.entity_type)
        name = t.name
        fields = TemplateFields.decode(t.fields)
        bodyScaffold = t.body_scaffold.orEmpty()
        worldTemplates = repo.forWorld(worldId).first()
        loaded = true
    }

    fun editName(v: String) = edit { name = v }

    fun addField(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        edit { fields = fields + TemplateField(TemplateFields.keyFor(trimmed, fields.map { it.key }.toSet()), trimmed) }
    }

    fun renameField(key: String, label: String) =
        edit { fields = fields.map { if (it.key == key) it.copy(label = label) else it } }

    fun editBodyScaffold(v: String) = edit { bodyScaffold = v }

    /** Distinct non-null groups, in field order. */
    val groups: List<String> get() = fields.mapNotNull { it.group }.distinct()

    /** To LINK a field gets a default link; away from LINK the link goes but autofill stays. */
    fun editFieldType(key: String, type: FieldType) = editField(key) {
        it.copy(type = type, link = if (type == FieldType.LINK) it.link ?: LinkSpec("knows", LinkDir.OUT) else null)
    }

    fun editFieldGroup(key: String, group: String) = editField(key) { it.copy(group = group.ifBlank { null }) }

    fun editFieldOptions(key: String, text: String) =
        editField(key) { it.copy(options = text.split(',').map(String::trim).filter(String::isNotEmpty)) }

    fun editFieldLink(key: String, link: LinkSpec) = editField(key) { it.copy(link = link) }

    fun editFieldAutofill(key: String, rules: List<AutofillRule>) = editField(key) { it.copy(autofill = rules) }

    /** Fields of the template referenced by a link's `targetTemplate` (built-in key or id), if known. */
    fun targetFields(ref: String?): List<TemplateField> {
        val t = worldTemplates.firstOrNull { it.builtin_key == ref || it.id == ref } ?: return emptyList()
        return TemplateFields.decode(t.fields)
    }

    private fun editField(key: String, change: (TemplateField) -> TemplateField) =
        edit { fields = fields.map { if (it.key == key) change(it) else it } }

    fun removeField(key: String) = edit { fields = fields.filterNot { it.key == key } }

    suspend fun flush() {
        saveJob?.cancelAndJoin()
        save()
    }

    private fun edit(change: () -> Unit) {
        change()
        dirty = true
        saveJob?.cancel()
        saveJob = scope.launch { delay(EntityEditorState.SAVE_DELAY_MS); save() }
    }

    private suspend fun save() {
        if (!dirty) return
        dirty = false
        try {
            withContext(NonCancellable) {
                repo.update(id, name.trim().ifEmpty { "Untitled template" }, fields.map { it.copy(group = it.group?.trim()?.ifEmpty { null }) }, bodyScaffold.ifBlank { null })
            }
        } catch (e: Throwable) {
            dirty = true // still unsaved: the next edit or flush retries it
            throw e
        }
    }
}
