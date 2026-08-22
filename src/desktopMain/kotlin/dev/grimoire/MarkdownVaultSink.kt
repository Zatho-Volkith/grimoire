package dev.grimoire

import dev.grimoire.export.ExportFile
import dev.grimoire.export.ExportSink
import dev.grimoire.export.VaultNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Desktop [ExportSink]: asks for a folder, then writes the vault into a subfolder named after
 * the world. Re-exporting to the same place overwrites matching files in place, so the vault
 * can be refreshed without touching anything Obsidian added next to it.
 */
class MarkdownVaultSink : ExportSink {

    override suspend fun write(worldName: String, files: List<ExportFile>): String? {
        val directory = withContext(Dispatchers.Swing) {
            val chooser = JFileChooser().apply {
                dialogTitle = "Choose where to put the exported vault"
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            }
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
        } ?: return null

        return withContext(Dispatchers.IO) {
            val root = directory.toPath().resolve(VaultNames.sanitize(worldName))
            files.forEach { f ->
                val target = root.resolve(f.path.joinToString("/"))
                target.parent.createDirectories()
                target.writeText(f.content)
            }
            root.absolutePathString()
        }
    }
}
