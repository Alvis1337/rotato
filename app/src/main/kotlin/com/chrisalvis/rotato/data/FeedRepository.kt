package com.chrisalvis.rotato.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.chrisalvis.rotato.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "FeedRepository"

class FeedRepository(private val imageDir: File) {

    suspend fun downloadWallpaper(sourceId: String, fullUrl: String, fallbackUrl: String = "", authHeader: String? = null): Boolean = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) Log.d(TAG, "downloadWallpaper: sourceId=$sourceId, fullUrl=$fullUrl")
        if (fullUrl.isBlank()) {
            Log.e(TAG, "downloadWallpaper: fullUrl is blank!")
            return@withContext false
        }
        val sanitized = sanitizeFilename(sourceId)
        // Check if any previously-downloaded file for this sourceId is still valid (extension-agnostic).
        val existing = imageDir.listFiles()?.firstOrNull { it.nameWithoutExtension == sanitized }
        if (existing != null && existing.isValidImage()) return@withContext true
        existing?.delete() // corrupt or stale — force re-download

        return@withContext try {
            var pair = downloadBytesWithMime(fullUrl, authHeader)
            if (pair == null && fallbackUrl.isNotBlank()) {
                if (BuildConfig.DEBUG) Log.d(TAG, "downloadWallpaper: primary URL failed, retrying with fallback: $fallbackUrl")
                pair = downloadBytesWithMime(fallbackUrl, authHeader)
            }
            val (bytes, contentType) = pair ?: return@withContext false.also { Log.e(TAG, "downloadBytes returned null for $fullUrl") }
            // Prefer Content-Type header, fall back to URL suffix, then magic bytes.
            val ext = detectExtFromContentType(contentType)
                ?: fullUrl.substringAfterLast('.').substringBefore('?').take(5).lowercase().takeIf { it.matches("[a-z]+".toRegex()) }
                ?: detectExtFromBytes(bytes)
                ?: "jpg"
            imageDir.mkdirs()
            File(imageDir, "$sanitized.$ext").writeBytes(bytes)
            if (BuildConfig.DEBUG) Log.d(TAG, "downloadWallpaper successful: $sanitized.$ext")
            true
        } catch (e: Exception) {
            Log.e(TAG, "downloadWallpaper failed for $fullUrl", e)
            false
        }
    }

    suspend fun saveToGallery(context: Context, sourceId: String, fullUrl: String, fallbackUrl: String = "", authHeader: String? = null): Boolean = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) Log.d(TAG, "saveToGallery: sourceId=$sourceId, url=$fullUrl")
        if (fullUrl.isBlank()) {
            Log.e(TAG, "saveToGallery fullUrl is blank!")
            return@withContext false
        }

        return@withContext try {
            var bytes = downloadBytes(fullUrl, authHeader)
            var effectiveUrl = fullUrl
            if (bytes == null && fallbackUrl.isNotBlank()) {
                if (BuildConfig.DEBUG) Log.d(TAG, "saveToGallery: primary URL failed, retrying with fallback: $fallbackUrl")
                bytes = downloadBytes(fallbackUrl, authHeader)
                if (bytes != null) effectiveUrl = fallbackUrl
            }
            bytes ?: return@withContext false.also { Log.e(TAG, "downloadBytes returned null for $fullUrl") }
            if (BuildConfig.DEBUG) Log.d(TAG, "Downloaded ${bytes.size} bytes from $effectiveUrl")

            val mimeType = when (effectiveUrl.substringAfterLast('.').substringBefore('?').lowercase().take(5)) {
                "png"  -> "image/png"
                "webp" -> "image/webp"
                "gif"  -> "image/gif"
                else   -> "image/jpeg"
            }
            val ext = mimeType.substringAfterLast('/')
            val fileName = "${sanitizeFilename(sourceId)}.$ext"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Rotato")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext false.also { Log.e(TAG, "Failed to insert uri") }
                val stream = resolver.openOutputStream(uri)
                    ?: return@withContext false.also {
                        resolver.delete(uri, null, null)
                        Log.e(TAG, "openOutputStream returned null for $uri")
                    }
                stream.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                if (BuildConfig.DEBUG) Log.d(TAG, "Saved to gallery: $uri")
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Rotato"
                )
                dir.mkdirs()
                File(dir, fileName).writeBytes(bytes)
                if (BuildConfig.DEBUG) Log.d(TAG, "Saved to file: ${dir.absolutePath}/$fileName")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveToGallery failed for $fullUrl", e)
            false
        }
    }

    /** Copies an already-downloaded local [file] into the public Pictures/Rotato gallery folder. */
    suspend fun saveFileToGallery(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) Log.d(TAG, "saveFileToGallery: ${file.name}")
        if (!file.exists()) return@withContext false
        return@withContext try {
            val bytes = file.readBytes()
            val mimeType = when (file.extension.lowercase()) {
                "png"  -> "image/png"
                "webp" -> "image/webp"
                "gif"  -> "image/gif"
                else   -> "image/jpeg"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Rotato")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext false.also { Log.e(TAG, "Failed to insert uri") }
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: return@withContext false.also { resolver.delete(uri, null, null) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Rotato"
                )
                dir.mkdirs()
                File(dir, file.name).writeBytes(bytes)
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "saveFileToGallery success: ${file.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveFileToGallery failed for ${file.name}", e)
            false
        }
    }

    /** Returns true if this file starts with known image magic bytes (JPEG, PNG, GIF, WebP). */
    private fun File.isValidImage(): Boolean {
        if (!exists() || length() < 4) return false
        return try {
            inputStream().use { stream ->
                val header = ByteArray(4)
                stream.read(header)
                // JPEG: FF D8 FF
                (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) ||
                // PNG: 89 50 4E 47
                (header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte()) ||
                // GIF: GIF8
                (header[0] == 0x47.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte()) ||
                // WebP: RIFF
                (header[0] == 0x52.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte())
            }
        } catch (e: Exception) { false }
    }

    private fun downloadBytesWithMime(url: String, authHeader: String? = null): Pair<ByteArray, String?>? = try {
        if (BuildConfig.DEBUG) Log.d(TAG, "Downloading from: $url")
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
        if (url.contains("cdn.donmai.us") || url.contains("danbooru.donmai.us")) {
            reqBuilder.header("Referer", "https://danbooru.donmai.us/")
        }
        if (url.contains("gelbooru.com") || url.contains("img2.gelbooru.com")) {
            reqBuilder.header("Referer", "https://gelbooru.com/")
        }
        if (authHeader != null) {
            reqBuilder.header("Authorization", authHeader)
        }
        httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (BuildConfig.DEBUG) Log.d(TAG, "HTTP ${resp.code} for $url")
            if (!resp.isSuccessful) {
                Log.w(TAG, "HTTP ${resp.code} for $url")
                null
            } else {
                val contentType = resp.header("Content-Type")
                val bytes = resp.body?.bytes()
                if (BuildConfig.DEBUG) Log.d(TAG, "Got ${bytes?.size} bytes, Content-Type=$contentType")
                bytes?.let { it to contentType }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "downloadBytesWithMime exception for $url", e)
        null
    }

    private fun detectExtFromContentType(contentType: String?): String? = when {
        contentType == null -> null
        "jpeg" in contentType || "jpg" in contentType -> "jpg"
        "png" in contentType -> "png"
        "webp" in contentType -> "webp"
        "gif" in contentType -> "gif"
        else -> null
    }

    private fun detectExtFromBytes(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        return when {
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "png"
            bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() -> "gif"
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() -> "webp"
            else -> null
        }
    }

    private fun downloadBytes(url: String, authHeader: String? = null): ByteArray? =
        downloadBytesWithMime(url, authHeader)?.first

    companion object {
        // Shared client — reusing thread pool and connection pool across all FeedRepository instances.
        val httpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
