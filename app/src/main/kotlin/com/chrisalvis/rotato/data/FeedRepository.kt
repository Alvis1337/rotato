package com.chrisalvis.rotato.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "FeedRepository"

class FeedRepository(private val imageDir: File) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun downloadWallpaper(sourceId: String, fullUrl: String, fallbackUrl: String = "", authHeader: String? = null): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "downloadWallpaper: sourceId=$sourceId, fullUrl=$fullUrl")
        if (fullUrl.isBlank()) {
            Log.e(TAG, "downloadWallpaper: fullUrl is blank!")
            return@withContext false
        }
        val ext = fullUrl.substringAfterLast('.').substringBefore('?').take(5).ifBlank { "jpg" }
        val destFile = File(imageDir, "${sanitizeFilename(sourceId)}.$ext")
        if (destFile.exists()) return@withContext true
        return@withContext try {
            var bytes = downloadBytes(fullUrl, authHeader)
            if (bytes == null && fallbackUrl.isNotBlank()) {
                Log.d(TAG, "downloadWallpaper: primary URL failed, retrying with fallback: $fallbackUrl")
                bytes = downloadBytes(fallbackUrl, authHeader)
            }
            bytes ?: return@withContext false.also { Log.e(TAG, "downloadBytes returned null for $fullUrl") }
            imageDir.mkdirs()
            destFile.writeBytes(bytes)
            Log.d(TAG, "downloadWallpaper successful: $destFile")
            true
        } catch (e: Exception) {
            Log.e(TAG, "downloadWallpaper failed for $fullUrl", e)
            false
        }
    }

    suspend fun saveToGallery(context: Context, sourceId: String, fullUrl: String, fallbackUrl: String = "", authHeader: String? = null): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "saveToGallery: sourceId=$sourceId, url=$fullUrl")
        if (fullUrl.isBlank()) {
            Log.e(TAG, "saveToGallery fullUrl is blank!")
            return@withContext false
        }

        return@withContext try {
            var bytes = downloadBytes(fullUrl, authHeader)
            var effectiveUrl = fullUrl
            if (bytes == null && fallbackUrl.isNotBlank()) {
                Log.d(TAG, "saveToGallery: primary URL failed, retrying with fallback: $fallbackUrl")
                bytes = downloadBytes(fallbackUrl, authHeader)
                if (bytes != null) effectiveUrl = fallbackUrl
            }
            bytes ?: return@withContext false.also { Log.e(TAG, "downloadBytes returned null for $fullUrl") }
            Log.d(TAG, "Downloaded ${bytes.size} bytes from $effectiveUrl")

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
                Log.d(TAG, "Saved to gallery: $uri")
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Rotato"
                )
                dir.mkdirs()
                File(dir, fileName).writeBytes(bytes)
                Log.d(TAG, "Saved to file: ${dir.absolutePath}/$fileName")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveToGallery failed for $fullUrl", e)
            false
        }
    }

    private fun downloadBytes(url: String, authHeader: String? = null): ByteArray? = try {
        Log.d(TAG, "Downloading from: $url")
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
        if (url.contains("cdn.donmai.us") || url.contains("danbooru.donmai.us")) {
            reqBuilder.header("Referer", "https://danbooru.donmai.us/")
        }
        if (authHeader != null) {
            reqBuilder.header("Authorization", authHeader)
        }
        httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            Log.d(TAG, "HTTP ${resp.code} for $url")
            if (!resp.isSuccessful) { 
                Log.w(TAG, "HTTP ${resp.code} for $url"); 
                null 
            }
            else {
                val bytes = resp.body?.bytes()
                Log.d(TAG, "Got ${bytes?.size} bytes")
                bytes
            }
        }
    } catch (e: Exception) { 
        Log.e(TAG, "downloadBytes exception for $url", e)
        null 
    }

}
