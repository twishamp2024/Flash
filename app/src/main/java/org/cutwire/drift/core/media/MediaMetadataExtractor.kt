package org.cutwire.drift.core.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cutwire.drift.core.model.MediaAsset
import org.cutwire.drift.core.model.MediaType
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ExtractedMediaMetadata(
    val durationUs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val frameRate: Float,
    val bitRate: Long,
    val mimeType: String,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val audioChannels: Int = 2,
    val audioSampleRate: Int = 44100
) {
    val displayWidth: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) height else width

    val displayHeight: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) width else height
}

@Singleton
class MediaMetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun extractMetadata(uri: Uri): Result<ExtractedMediaMetadata> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            if (uri.scheme == "file") {
                retriever.setDataSource(uri.path)
            } else {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    pfd.use {
                        retriever.setDataSource(it.fileDescriptor)
                    }
                } else {
                    retriever.setDataSource(context, uri)
                }
            }
            Result.success(parseRetrieverMetadata(retriever))
        } catch (e: Exception) {
            // Check if it's an image file
            val imageMetadata = extractImageMetadata(uri)
            if (imageMetadata != null) {
                Result.success(imageMetadata)
            } else {
                android.util.Log.e("DriftMedia", "Metadata extraction failed for $uri", e)
                Result.failure(e)
            }
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    suspend fun extractMetadata(file: File): Result<ExtractedMediaMetadata> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            Result.success(parseRetrieverMetadata(retriever))
        } catch (e: Exception) {
            val imageMetadata = extractImageMetadata(file)
            if (imageMetadata != null) {
                Result.success(imageMetadata)
            } else {
                Result.failure(e)
            }
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun extractImageMetadata(uri: Uri): ExtractedMediaMetadata? {
        return try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            if (uri.scheme == "file") {
                android.graphics.BitmapFactory.decodeFile(uri.path, options)
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream, null, options)
                }
            }
            if (options.outWidth > 0 && options.outHeight > 0) {
                val mime = options.outMimeType ?: (context.contentResolver.getType(uri) ?: "image/png")
                ExtractedMediaMetadata(
                    durationUs = 3_000_000L, // 3 seconds default for imported images
                    width = options.outWidth,
                    height = options.outHeight,
                    rotationDegrees = 0,
                    frameRate = 30.0f,
                    bitRate = 0L,
                    mimeType = mime,
                    hasVideo = false,
                    hasAudio = false
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractImageMetadata(file: File): ExtractedMediaMetadata? {
        return try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                val mime = options.outMimeType ?: "image/png"
                ExtractedMediaMetadata(
                    durationUs = 3_000_000L,
                    width = options.outWidth,
                    height = options.outHeight,
                    rotationDegrees = 0,
                    frameRate = 30.0f,
                    bitRate = 0L,
                    mimeType = mime,
                    hasVideo = false,
                    hasAudio = false
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseRetrieverMetadata(retriever: MediaMetadataRetriever): ExtractedMediaMetadata {
        val durationMsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val durationUs = (durationMsStr?.toLongOrNull() ?: 0L) * 1_000L

        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

        val bitRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
        val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/mp4"

        val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null || width > 0
        val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null

        val captureFpsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
        val fps = captureFpsStr?.toFloatOrNull() ?: 30.0f

        return ExtractedMediaMetadata(
            durationUs = durationUs,
            width = width,
            height = height,
            rotationDegrees = rotation,
            frameRate = if (fps > 0f) fps else 30.0f,
            bitRate = bitRate,
            mimeType = mimeType,
            hasVideo = hasVideo,
            hasAudio = hasAudio
        )
    }

    fun buildMediaAsset(
        uri: Uri,
        displayName: String,
        metadata: ExtractedMediaMetadata,
        thumbnailPath: String? = null
    ): MediaAsset {
        val type = when {
            metadata.hasVideo -> MediaType.VIDEO
            metadata.hasAudio -> MediaType.AUDIO
            metadata.mimeType.startsWith("image/") -> MediaType.IMAGE
            else -> MediaType.VIDEO
        }

        return MediaAsset(
            uriString = uri.toString(),
            displayName = displayName,
            mediaType = type,
            durationUs = metadata.durationUs,
            width = metadata.displayWidth,
            height = metadata.displayHeight,
            frameRate = metadata.frameRate,
            bitRate = metadata.bitRate,
            mimeType = metadata.mimeType,
            thumbnailPath = thumbnailPath
        )
    }
}
