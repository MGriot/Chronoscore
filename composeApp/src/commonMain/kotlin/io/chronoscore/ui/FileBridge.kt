package io.chronoscore.ui

/**
 * Local file access for backups and CSV exports, through the platform's own picker
 * (Storage Access Framework on Android, a file dialog on desktop). Nothing leaves the device.
 */
interface FileBridge {
    val available: Boolean

    /** Asks where to save [text]; [onResult] gets false if the user cancels or the write fails. */
    fun saveText(suggestedName: String, mimeType: String, text: String, onResult: (Boolean) -> Unit)

    /** Asks for a file to read; [onResult] gets null if the user cancels or the read fails. */
    fun openText(mimeTypes: List<String>, onResult: (String?) -> Unit)
}

/** No file access (previews and tests); the export and import buttons stay hidden. */
object NoFileBridge : FileBridge {
    override val available = false
    override fun saveText(suggestedName: String, mimeType: String, text: String, onResult: (Boolean) -> Unit) = onResult(false)
    override fun openText(mimeTypes: List<String>, onResult: (String?) -> Unit) = onResult(null)
}
