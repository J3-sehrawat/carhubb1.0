package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

data class CompressedPhoto(
    val dataUri: String,
    val sizeKb: Int
)

/**
 * Client-side photo compressor optimized for mobile data & Firestore real-time sync.
 * Resizes captured/gallery images up to max 1080px and compresses JPEG adaptively so
 * up to 4 product photos stay crisp yet lightweight (~45-130 KB each, safely within
 * Firestore's 1 MiB document budget).
 */
object ImageCompressor {
    private const val MAX_INITIAL_DIM = 1080
    private const val MAX_DATA_URI_CHARS = 195_000 // ~146 KB binary -> 4 photos safely < 800 KB

    suspend fun compressUriToDataUri(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<CompressedPhoto> = withContext(Dispatchers.IO) {
        try {
            onProgress(0.15f)
            val resolver = context.contentResolver

            // Step 1: Read bounds only to compute efficient inSampleSize
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: return@withContext Result.failure(IllegalStateException("Unable to read selected image"))

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) {
                return@withContext Result.failure(IllegalArgumentException("Unsupported or corrupted image file"))
            }

            var inSampleSize = 1
            val maxOrig = max(origWidth, origHeight)
            while (maxOrig / inSampleSize > MAX_INITIAL_DIM * 2) {
                inSampleSize *= 2
            }

            onProgress(0.35f)
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext Result.failure(IllegalStateException("Failed to decode image"))

            // Step 2: Scale cleanly down to max 1080px on longest side
            var currentBitmap = scaleDownIfNeeded(decoded, MAX_INITIAL_DIM)
            if (currentBitmap !== decoded) {
                decoded.recycle()
            }

            onProgress(0.60f)

            // Step 3: Adaptive JPEG compression (start at quality 72, step down if needed for budget)
            var quality = 72
            var compressedBytes: ByteArray
            var dataUri: String

            while (true) {
                val out = ByteArrayOutputStream()
                currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                compressedBytes = out.toByteArray()
                val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
                dataUri = "data:image/jpeg;base64,$base64"

                if (dataUri.length <= MAX_DATA_URI_CHARS) {
                    break
                }

                if (quality > 42) {
                    quality -= 12
                } else {
                    // Downscale dimensions further if still over budget
                    val nextMaxDim = max(480, (max(currentBitmap.width, currentBitmap.height) * 0.78f).toInt())
                    val resized = scaleDownIfNeeded(currentBitmap, nextMaxDim)
                    if (resized === currentBitmap) {
                        break
                    }
                    currentBitmap.recycle()
                    currentBitmap = resized
                    quality = 62
                }
            }

            currentBitmap.recycle()
            onProgress(1.0f)

            val sizeKb = max(1, compressedBytes.size / 1024)
            Result.success(CompressedPhoto(dataUri = dataUri, sizeKb = sizeKb))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun scaleDownIfNeeded(source: Bitmap, maxDim: Int): Bitmap {
        val width = source.width
        val height = source.height
        val longest = max(width, height)
        if (longest <= maxDim) return source

        val ratio = maxDim.toFloat() / longest.toFloat()
        val targetW = max(1, (width * ratio).toInt())
        val targetH = max(1, (height * ratio).toInt())
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }

    // Memory-bounded LRU cache for decoded product photos so Compose never blocks the UI thread
    // re-decoding Base64 images on recompositions, tab switches, or LazyColumn/LazyRow scrolling.
    private val imageBitmapCache: LruCache<String, ImageBitmap> = run {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        val cacheSizeKb = (maxKb / 8).coerceIn(4 * 1024, 24 * 1024)
        object : LruCache<String, ImageBitmap>(cacheSizeKb) {
            override fun sizeOf(key: String, value: ImageBitmap): Int {
                // RGB_565 uses 2 bytes/pixel; fall back to 4 bytes/pixel estimate safely
                val bytes = value.width.toLong() * value.height.toLong() * 2L
                return max(1, (bytes / 1024L).toInt())
            }
        }
    }

    private fun cacheKey(dataUri: String): String {
        val head = dataUri.take(48)
        val tail = dataUri.takeLast(32)
        return "${dataUri.length}:${dataUri.hashCode()}:$head:$tail"
    }

    fun getCachedImageBitmap(dataUri: String): ImageBitmap? {
        return imageBitmapCache.get(cacheKey(dataUri))
    }

    suspend fun decodeDataUriToImageBitmapCached(dataUri: String): ImageBitmap? {
        val key = cacheKey(dataUri)
        imageBitmapCache.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            imageBitmapCache.get(key)?.let { return@withContext it }
            val decoded = decodeDataUriToBitmap(dataUri, preferRgb565 = true)?.asImageBitmap()
            if (decoded != null) {
                imageBitmapCache.put(key, decoded)
            }
            decoded
        }
    }

    fun decodeDataUriToBitmap(dataUri: String, preferRgb565: Boolean = false): Bitmap? {
        return try {
            val commaIndex = dataUri.indexOf(',')
            if (commaIndex == -1) return null
            val pureBase64 = dataUri.substring(commaIndex + 1)
            val bytes = Base64.decode(pureBase64, Base64.DEFAULT)
            val options = BitmapFactory.Options().apply {
                if (preferRgb565) {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (_: Exception) {
            null
        }
    }

    fun createTempCameraFile(context: Context): File {
        val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        return File(dir, "capture_${System.currentTimeMillis()}.jpg")
    }
}
