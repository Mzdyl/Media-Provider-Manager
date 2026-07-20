package me.gm.cleaner.plugin.ui.screens.createtemplate

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.core.provider.DocumentsContractCompat
import me.gm.cleaner.plugin.xposed.util.FileUtils
import java.io.File

private const val ExternalStoragePrimaryEmulatedRootId = "primary"

fun treeUriToFile(result: Uri, context: Context): File? {
    require(DocumentsContractCompat.isTreeUri(result))
    val docId = DocumentsContract.getTreeDocumentId(result)
    val splitIndex = docId.indexOf(':', 1)
    if (splitIndex <= 0) return null

    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    val roots = storageManager.storageVolumes.associateBy { volume ->
        if (volume.isPrimary) {
            ExternalStoragePrimaryEmulatedRootId
        } else {
            volume.uuid?.lowercase()
        }
    }
    val tag = docId.substring(0, splitIndex).lowercase()
    val root = roots[tag] ?: return null
    val path = docId.substring(splitIndex + 1)
    val rootDirectory = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> root.directory
        root.isPrimary -> Environment.getExternalStorageDirectory()
        root.uuid != null -> File("/storage", root.uuid!!)
        else -> null
    } ?: return null
    return File(rootDirectory, path).normalize()
        .takeIf { FileUtils.contains(rootDirectory, it) }
}
