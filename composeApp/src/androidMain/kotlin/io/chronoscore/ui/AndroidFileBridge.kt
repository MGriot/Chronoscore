package io.chronoscore.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Storage Access Framework pickers: the user chooses where a file goes or which one to read,
 * so the app needs no storage permission and never touches the network.
 * Create it during Activity.onCreate, before the activity is started.
 */
class AndroidFileBridge(private val activity: ComponentActivity) : FileBridge {
    override val available = true

    private var pendingSave: Pair<String, (Boolean) -> Unit>? = null
    private var pendingOpen: ((String?) -> Unit)? = null

    private val createLaunchers: Map<String, ActivityResultLauncher<String>> =
        listOf("application/json", "text/csv").associateWith { mime ->
            activity.registerForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri -> finishSave(uri) }
        }

    private val openLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = pendingOpen ?: return@registerForActivityResult
        pendingOpen = null
        callback(uri?.let { read(it) })
    }

    override fun saveText(suggestedName: String, mimeType: String, text: String, onResult: (Boolean) -> Unit) {
        val launcher = createLaunchers[mimeType] ?: createLaunchers.getValue("application/json")
        pendingSave = text to onResult
        launcher.launch(suggestedName)
    }

    override fun openText(mimeTypes: List<String>, onResult: (String?) -> Unit) {
        pendingOpen = onResult
        openLauncher.launch(mimeTypes.toTypedArray())
    }

    private fun finishSave(uri: Uri?) {
        val (text, callback) = pendingSave ?: return
        pendingSave = null
        if (uri == null) return callback(false)
        val ok = runCatching {
            activity.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }.isSuccess
        callback(ok)
    }

    private fun read(uri: Uri): String? = runCatching {
        activity.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()
}
