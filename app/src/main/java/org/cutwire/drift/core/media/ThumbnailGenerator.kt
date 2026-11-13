package org.cutwire.drift.core.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.collection.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cutwire.drift.core.storage.MediaStorageManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThumbnailGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager: MediaStorageManager
) {

    // 16MB in-memory LRU cache for quick timeline scrubbing and thumbnail rendering
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    /**
     * Extract a single frame bitmap at specified timestamp in microseconds.
     * Checks memory cache first.
     */
    suspend fun getFrameAtTime(
        uri: Uri,
        timeUs: Long = 0L,
        targetWidth: Int = 240,
        targetHeight: Int = 135
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${uri}_${timeUs}_${targetWidth}x${targetHeight}"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        val retriever = MediaMetadataRetriever()
        try {
            if (uri.scheme == "file") {
                retriever.setDataSource(uri.path)
            } else {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    pfd.use { retriever.setDataSource(it.fileDescriptor) }
                } else {
                    retriever.setDataSource(context, uri)
                }
            }
            val bitmap = extractScaledBitmap(retriever, timeUs, targetWidth, targetHeight)
            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
            }
            bitmap
        } catch (_: Exception) {
            // Fallback for image formats
            val imageBitmap = decodeImageBitmap(uri, targetWidth, targetHeight)
            if (imageBitmap != null) {
                memoryCache.put(cacheKey, imageBitmap)
            }
            imageBitmap
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun decodeImageBitmap(uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap? {
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
            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return null

            var inSampleSize = 1
            if (srcHeight > targetHeight || srcWidth > targetWidth) {
                val halfHeight = srcHeight / 2
                val halfWidth = srcWidth / 2
                while ((halfHeight / inSampleSize) >= targetHeight && (halfWidth / inSampleSize) >= targetWidth) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val decoded = if (uri.scheme == "file") {
                android.graphics.BitmapFactory.decodeFile(uri.path, decodeOptions)
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream, null, decodeOptions)
                }
            }
            if (decoded != null && targetWidth > 0 && targetHeight > 0) {
                Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
            } else {
                decoded
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extract a primary thumbnail for a newly imported asset, saving it to disk via MediaStorageManager.
     */
    suspend fun generateAssetThumbnail(
        uri: Uri,
        assetId: String,
        timeUs: Long = 0L
    ): String? = withContext(Dispatchers.IO) {
        val bitmap = getFrameAtTime(uri, timeUs, targetWidth = 320, targetHeight = 180) ?: return@withContext null
        try {
            storageManager.saveThumbnail(bitmap, assetId)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extract multiple evenly spaced thumbnail bitmaps across a time interval [startUs, endUs].
     * Ideal for timeline filmstrips.
     */
    suspend fun generateFilmstrip(
        uri: Uri,
        startUs: Long,
        endUs: Long,
        count: Int,
        thumbWidth: Int = 120,
        thumbHeight: Int = 70
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        if (count <= 0 || endUs <= startUs) return@withContext emptyList()

        val stepUs = (endUs - startUs) / count.coerceAtLeast(1)
        val bitmaps = mutableListOf<Bitmap>()
        val retriever = MediaMetadataRetriever()

        try {
            if (uri.scheme == "file") {
                retriever.setDataSource(uri.path)
            } else {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    pfd.use { retriever.setDataSource(it.fileDescriptor) }
                } else {
                    retriever.setDataSource(context, uri)
                }
            }
            for (i in 0 until count) {
                val timeUs = (startUs + i * stepUs).coerceAtMost(endUs)
                val cacheKey = "${uri}_${timeUs}_${thumbWidth}x${thumbHeight}"
                val cached = memoryCache.get(cacheKey)
                if (cached != null) {
                    bitmaps.add(cached)
                } else {
                    val frame = extractScaledBitmap(retriever, timeUs, thumbWidth, thumbHeight)
                    if (frame != null) {
                        memoryCache.put(cacheKey, frame)
                        bitmaps.add(frame)
                    }
                }
            }
        } catch (_: Exception) {
            // Return whatever frames were successfully gathered
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        if (bitmaps.isEmpty()) {
            val img = decodeImageBitmap(uri, thumbWidth, thumbHeight)
            if (img != null) {
                for (i in 0 until count) {
                    bitmaps.add(img)
                }
            }
        }

        bitmaps
    }

    private fun extractScaledBitmap(
        retriever: MediaMetadataRetriever,
        timeUs: Long,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && targetWidth > 0 && targetHeight > 0) {
            retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                targetWidth,
                targetHeight
            )
        } else {
            val original = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (original != null && targetWidth > 0 && targetHeight > 0) {
                Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
            } else {
                original
            }
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
    }
}
