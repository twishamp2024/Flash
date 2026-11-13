package org.cutwire.drift.engine.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cutwire.drift.core.storage.MediaStorageManager
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ExportStatus {
    data object Idle : ExportStatus
    data class Progress(val percentage: Int) : ExportStatus
    data class Success(val outputFile: File, val mediaStoreUri: Uri?) : ExportStatus
    data class Error(val message: String, val throwable: Throwable? = null) : ExportStatus
    data object Cancelled : ExportStatus
}

data class ExportConfiguration(
    val width: Int = 1920,
    val height: Int = 1080,
    val frameRate: Int = 30,
    val bitRate: Long = 8_000_000L,
    val videoMimeType: String = MimeTypes.VIDEO_H264,
    val audioMimeType: String = MimeTypes.AUDIO_AAC
)

@OptIn(UnstableApi::class)
@Singleton
class ExportEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager: MediaStorageManager
) {
    private var activeTransformer: Transformer? = null
    private var progressJob: Job? = null

    /**
     * Export a trimmed video clip to an MP4 file with the specified resolution and bounds.
     * Emits progress updates from 0 to 100, then Success or Error.
     */
    fun exportClip(
        sourceUri: Uri,
        inPointUs: Long,
        outPointUs: Long,
        config: ExportConfiguration = ExportConfiguration(),
        additionalEffects: List<Effect> = emptyList()
    ): Flow<ExportStatus> = callbackFlow {
        val outputFile = storageManager.createExportOutputFile("mp4")
        val progressHolder = ProgressHolder()

        val durationUs = (outPointUs - inPointUs).coerceAtLeast(1_000_000L)
        val durationMs = durationUs / 1000L

        // Detect if the source asset is a static image
        val mimeType = context.contentResolver.getType(sourceUri)
            ?: MimeTypeMap.getFileExtensionFromUrl(sourceUri.toString())?.let { ext ->
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            }
        val isImage = mimeType?.startsWith("image/") == true ||
                sourceUri.path?.endsWith(".png", ignoreCase = true) == true ||
                sourceUri.path?.endsWith(".jpg", ignoreCase = true) == true ||
                sourceUri.path?.endsWith(".jpeg", ignoreCase = true) == true

        val clippingConfig = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionUs(inPointUs)
            .apply {
                if (outPointUs > inPointUs) {
                    setEndPositionUs(outPointUs)
                }
            }
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .apply {
                if (isImage) {
                    setImageDurationMs(durationMs)
                } else {
                    setClippingConfiguration(clippingConfig)
                }
            }
            .build()

        val videoEffects = mutableListOf<Effect>()
        if (config.width > 0 && config.height > 0) {
            videoEffects.add(Presentation.createForWidthAndHeight(config.width, config.height, Presentation.LAYOUT_SCALE_TO_FIT))
        }
        videoEffects.addAll(additionalEffects)

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), videoEffects))
            .apply {
                if (isImage) {
                    setFrameRate(config.frameRate)
                    setDurationUs(durationUs)
                    setRemoveAudio(true)
                }
            }
            .build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                progressJob?.cancel()
                launch(Dispatchers.IO) {
                    val mediaStoreUri = saveToPublicGallery(outputFile)
                    trySend(ExportStatus.Success(outputFile, mediaStoreUri))
                    close()
                }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                progressJob?.cancel()
                trySend(ExportStatus.Error(exportException.localizedMessage ?: "Export failed", exportException))
                close()
            }
        }

        val transformerBuilder = Transformer.Builder(context)
            .setVideoMimeType(config.videoMimeType)
            .addListener(listener)

        if (!isImage) {
            transformerBuilder.setAudioMimeType(config.audioMimeType)
        }

        val transformer = transformerBuilder.build()

        activeTransformer = transformer

        try {
            trySend(ExportStatus.Progress(0))
            transformer.start(editedMediaItem, outputFile.absolutePath)

            // Start polling progress on Main application thread (required by Media3 Transformer)
            progressJob = launch(Dispatchers.Main) {
                while (isActive) {
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        trySend(ExportStatus.Progress(progressHolder.progress))
                    }
                    delay(100L)
                }
            }
        } catch (e: Exception) {
            trySend(ExportStatus.Error(e.localizedMessage ?: "Failed to start export", e))
            close()
        }

        awaitClose {
            progressJob?.cancel()
            cancelExport()
        }
    }

    fun cancelExport() {
        activeTransformer?.cancel()
        activeTransformer = null
        progressJob?.cancel()
        progressJob = null
    }

    /**
     * Copy the exported MP4 to the public Movies/Flash directory via MediaStore
     * so it immediately appears in Google Photos / Gallery.
     */
    private suspend fun saveToPublicGallery(file: File): Uri? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Flash")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, contentValues) ?: return@withContext null

        try {
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(file).use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } catch (_: Exception) {
            null
        }
    }
}
