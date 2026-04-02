package me.gm.cleaner.plugin.ui.screens.createtemplate

import android.content.Context
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.core.provider.DocumentsContractCompat
import java.io.File

private const val ExternalStoragePrimaryEmulatedRootId = "primary"

fun treeUriToFile(result: Uri, context: Context): File? {
    require(DocumentsContractCompat.isTreeUri(result))
    val docId = DocumentsContract.getTreeDocumentId(result)
    val splitIndex = docId.indexOf(':', 1)

    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    val roots = storageManager.storageVolumes.associateBy { volume ->
        if (volume.isPrimary) {
            ExternalStoragePrimaryEmulatedRootId
        } else {
            volume.uuid
        }
    }
    val tag = docId.substring(0, splitIndex)
    val root = roots[tag] ?: return null
    val path = docId.substring(splitIndex + 1)
    return File(root.javaClass.getMethod("getPathFile").invoke(root) as File, path)
}
