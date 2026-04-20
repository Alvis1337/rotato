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

    suspend fun downloadWallpaper(sourceId: String, fullUrl: String): Boolean = withContext(Dispatchers.IO) {
        val ext = fullUrl.substringAfterLast('.').substringBefore('?').take(5).ifBlank { "jpg" }
        val destFile = File(imageDir, "${sanitize(sourceId)}.$ext")
        if (destFile.exists()) return@withContext true
        return@withContext try {
            val bytes = downloadBytes(fullUrl) ?: return@withContext false
            imageDir.mkdirs()
            destFile.writeBytes(bytes)
            true
        } catch (e: Exception) {
            Log.e(TAG, "downloadWallpaper failed for $fullUrl", e)
            false
        }
    }

    suspend fun saveToGallery(context: Context, sourceId: String, fullUrl: String): Boolean = withContext(Dispatchers.IO) {
        val mimeType = when (fullUrl.substringAfterLast('.').substringBefore('?').lowercase().take(5)) {
            "png"  -> "image/png"
            "webp" -> "image/webp"
            "gif"  -> "image/gif"
            else   -> "image/jpeg"
        }
        val ext = mimeType.substringAfterLast('/')
        val fileName = "${sanitize(sourceId)}.$ext"

        return@withContext try {
            val bytes = downloadBytes(fullUrl) ?: return@withContext false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Rotato")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext false
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Rotato"
                )
                dir.mkdirs()
                File(dir, fileName).writeBytes(bytes)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveToGallery failed for $fullUrl", e)
            false
        }
    }

    private fun downloadBytes(url: String): ByteArray? = try {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) { Log.w(TAG, "downloadBytes HTTP ${resp.code} for $url"); null }
            else resp.body?.bytes()
        }
    } catch (e: Exception) { Log.e(TAG, "downloadBytes failed for $url", e); null }

    private fun sanitize(s: String) = s.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
}
