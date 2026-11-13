package org.cutwire.drift.core.storage

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val thumbnailsDir: File
        get() = File(context.filesDir, "thumbnails").apply { if (!exists()) mkdirs() }

    private val exportsDir: File
        get() = File(context.filesDir, "exports").apply { if (!exists()) mkdirs() }

    private val tempDir: File
        get() = File(context.cacheDir, "drift_temp").apply { if (!exists()) mkdirs() }

    private val mediaDir: File
        get() = File(context.filesDir, "media").apply { if (!exists()) mkdirs() }

    /**
     * Copy imported media from external URI to internal app storage.
     * Guarantees reliable, offline, zero-permission-loss playback and editing.
     */
    suspend fun copyMediaToProjectStorage(uri: Uri, displayName: String): File = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val extension = displayName.substringAfterLast('.', "mp4")
        val safeName = "media_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension"
        val destFile = File(mediaDir, safeName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        destFile
    }

    /**
     * Persist SAF read permission so Android doesn't revoke URI access later.
     */
    fun persistUriPermission(uri: Uri): Boolean {
        return try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            true
        } catch (_: Exception) {
            // Some URI providers (like camera captures or non-SAF pickers) don't support persistable permissions
            false
        }
    }

    /**
     * Save thumbnail bitmap to disk and return absolute file path.
     */
    fun saveThumbnail(bitmap: Bitmap, assetId: String = UUID.randomUUID().toString()): String {
        val file = File(thumbnailsDir, "thumb_$assetId.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file.absolutePath
    }

    /**
     * Create a new output file path for video exports.
     */
    fun createExportOutputFile(extension: String = "mp4"): File {
        return File(exportsDir, "flash_export_${System.currentTimeMillis()}.$extension")
    }

    /**
     * Clear temporary scratch files.
     */
    fun clearTempFiles() {
        tempDir.deleteRecursively()
        tempDir.mkdirs()
    }
}
