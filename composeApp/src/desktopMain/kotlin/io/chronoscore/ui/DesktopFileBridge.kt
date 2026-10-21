package io.chronoscore.ui

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Native save / open dialogs; files are read and written as UTF-8. */
class DesktopFileBridge(private val parent: Frame?) : FileBridge {
    override val available = true

    override fun saveText(suggestedName: String, mimeType: String, text: String, onResult: (Boolean) -> Unit) {
        val dialog = FileDialog(parent, "Save", FileDialog.SAVE).apply {
            file = suggestedName
            isVisible = true
        }
        val name = dialog.file ?: return onResult(false)
        onResult(runCatching { File(dialog.directory, name).writeText(text, Charsets.UTF_8) }.isSuccess)
    }

    override fun openText(mimeTypes: List<String>, onResult: (String?) -> Unit) {
        val dialog = FileDialog(parent, "Open backup", FileDialog.LOAD).apply {
            file = "*.json"
            setFilenameFilter { _, n -> n.endsWith(".json", ignoreCase = true) }
            isVisible = true
        }
        val name = dialog.file ?: return onResult(null)
        onResult(runCatching { File(dialog.directory, name).readText(Charsets.UTF_8) }.getOrNull())
    }
}
