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

    /** Downloads into the rotation pool. Returns the resulting on-disk filename (e.g. "12345.jpg"), or null on failure. */
    suspend fun downloadWallpaper(sourceId: String, fullUrl: String, fallbackUrl: String = "", authHeader: String? = null, source: String = ""): String? = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) Log.d(TAG, "downloadWallpaper: sourceId=$sourceId, fullUrl=$fullUrl")
        if (fullUrl.isBlank()) {
            Log.e(TAG, "downloadWallpaper: fullUrl is blank!")
            return@withContext null
        }
        val sanitized = poolKey(source, sourceId)
        // Check if any previously-downloaded file for this sourceId is still valid (extension-agnostic).
        val existing = imageDir.listFiles()?.firstOrNull { it.nameWithoutExtension == sanitized && !it.name.endsWith(".part") }
        if (existing != null && existing.isValidMedia()) return@withContext existing.name
        existing?.delete() // corrupt or stale — force re-download

        imageDir.mkdirs()
        // Streamed to a temp name and renamed so the worker never picks up a half-written file
        // (getImages() ignores the .part extension), and so big videos never sit in memory.
        val tmp = File(imageDir, "$sanitized.part")
        return@withContext try {
            var used = fullUrl
            var contentType = downloadToFile(fullUrl, authHeader, tmp)
            // A video's fallback is usually its still preview; a still beats nothing, but only
            // when the video itself can't be had.
            if (contentType == null && fallbackUrl.isNotBlank()) {
                if (BuildConfig.DEBUG) Log.d(TAG, "downloadWallpaper: primary URL failed, retrying with fallback: $fallbackUrl")
                contentType = downloadToFile(fallbackUrl, authHeader, tmp)
                used = fallbackUrl
            }
            if (contentType == null) {
                tmp.delete()
                Log.e(TAG, "download failed for $fullUrl")
                return@withContext null
            }
            val head = readHead(tmp)
            // A 200 HTML page (hotlink block, Cloudflare challenge) would otherwise be saved
            // as a .jpg and fail every rotation that picks it as IMAGE_CORRUPT.
            val looksLikeText = contentType.startsWith("text/") ||
                head.firstOrNull { it != ' '.code.toByte() && it != '\n'.code.toByte() && it != '\r'.code.toByte() } == '<'.code.toByte()
            if (looksLikeText) {
                tmp.delete()
                Log.e(TAG, "downloadWallpaper got a non-image response for $used ($contentType)")
                return@withContext null
            }
            // The file's own bytes first: a video saved under an image name showed up in the
            // Library as a tiny unreadable "image" with no preview.
            val ext = detectExtFromBytes(head)
                ?: detectExtFromContentType(contentType)
                ?: used.substringAfterLast('.').substringBefore('?').take(5).lowercase().takeIf { it.matches("[a-z0-9]+".toRegex()) }
                ?: "jpg"
            val fileName = "$sanitized.$ext"
            if (!tmp.renameTo(File(imageDir, fileName))) {
                tmp.delete()
                return@withContext null
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "downloadWallpaper successful: $fileName")
            fileName
        } catch (e: Exception) {
            tmp.delete()
            Log.e(TAG, "downloadWallpaper failed for $fullUrl", e)
            null
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

            val urlExt = effectiveUrl.substringAfterLast('.').substringBefore('?').lowercase().take(5)
            val isVideo = MediaType.isVideoUrl(effectiveUrl)
            val mimeType = when {
                isVideo -> if (urlExt == "webm") "video/webm" else "video/mp4"
                urlExt == "png"  -> "image/png"
                urlExt == "webp" -> "image/webp"
                urlExt == "gif"  -> "image/gif"
                else -> "image/jpeg"
            }
            val ext = mimeType.substringAfterLast('/').let { if (it == "mp4") "mp4" else it }
            val fileName = "${sanitizeFilename(sourceId)}.$ext"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val relativeDir = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativeDir/Rotato")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(collection, values)
                    ?: return@withContext false.also { Log.e(TAG, "Failed to insert uri") }
                val stream = resolver.openOutputStream(uri)
                    ?: return@withContext false.also {
                        resolver.delete(uri, null, null)
                        Log.e(TAG, "openOutputStream returned null for $uri")
                    }
                stream.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                if (BuildConfig.DEBUG) Log.d(TAG, "Saved to gallery: $uri")
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES),
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
            val ext = file.extension.lowercase()
            val isVideo = ext in ImageRepository.VIDEO_EXTS
            val mimeType = when (ext) {
                "png"  -> "image/png"
                "webp" -> "image/webp"
                "gif"  -> "image/gif"
                "webm" -> "video/webm"
                "mkv"  -> "video/x-matroska"
                "mov"  -> "video/quicktime"
                else   -> if (isVideo) "video/mp4" else "image/jpeg"
            }
            val publicDir = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$publicDir/Rotato")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val uri = resolver.insert(collection, values)
                    ?: return@withContext false.also { Log.e(TAG, "Failed to insert uri") }
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: return@withContext false.also { resolver.delete(uri, null, null) }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(publicDir), "Rotato")
                dir.mkdirs()
                file.copyTo(File(dir, file.name), overwrite = true)
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "saveFileToGallery success: ${file.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveFileToGallery failed for ${file.name}", e)
            false
        }
    }

    /** True when this file starts like a picture or video Rotato can show. */
    private fun File.isValidMedia(): Boolean {
        if (!exists() || length() < 12) return false
        return try { detectExtFromBytes(readHead(this)) != null } catch (e: Exception) { false }
    }

    private fun readHead(file: File): ByteArray = file.inputStream().use { input ->
        val buf = ByteArray(32)
        val n = input.read(buf).coerceAtLeast(0)
        buf.copyOf(n)
    }

    /** Streams [url] into [dest]. Returns the Content-Type ("" when none), or null on failure. */
    private fun downloadToFile(url: String, authHeader: String?, dest: File): String? = try {
        httpClient.newCall(buildRequest(url, authHeader)).execute().use { resp ->
            val body = resp.body
            if (!resp.isSuccessful || body == null) {
                Log.w(TAG, "HTTP ${resp.code} for $url")
                null
            } else {
                dest.outputStream().use { out -> body.byteStream().copyTo(out) }
                if (dest.length() == 0L) null else resp.header("Content-Type").orEmpty()
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "downloadToFile exception for $url", e)
        null
    }

    private fun buildRequest(url: String, authHeader: String?): Request {
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
        return reqBuilder.build()
    }

    private fun downloadBytesWithMime(url: String, authHeader: String? = null): Pair<ByteArray, String?>? = try {
        if (BuildConfig.DEBUG) Log.d(TAG, "Downloading from: $url")
        httpClient.newCall(buildRequest(url, authHeader)).execute().use { resp ->
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
        "video/webm" in contentType -> "webm"
        "video/quicktime" in contentType -> "mov"
        "video/x-matroska" in contentType -> "mkv"
        contentType.startsWith("video/") -> "mp4"
        else -> null
    }

    private fun detectExtFromBytes(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        return when {
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "png"
            bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() -> "gif"
            bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
            // Matroska/WebM: EBML header.
            bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() && bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte() -> "webm"
            // ISO media: "ftyp" at offset 4. HEIF/AVIF stills share it; those aren't videos.
            bytes.size >= 12 && String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp" -> {
                val brand = String(bytes, 8, 4, Charsets.US_ASCII)
                when {
                    brand in setOf("heic", "heix", "mif1", "msf1", "avif", "avis") -> null
                    brand == "qt  " -> "mov"
                    else -> "mp4"
                }
            }
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
