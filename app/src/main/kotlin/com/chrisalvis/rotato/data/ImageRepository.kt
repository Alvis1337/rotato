package com.chrisalvis.rotato.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import android.os.FileObserver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ImageRepository(private val context: Context) {

    companion object {
        val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif")
        val VIDEO_EXTS = setOf("mp4", "webm", "mkv", "mov", "m4v")
    }

    private val imageDir: File
        get() = File(context.filesDir, "rotato_images").also { it.mkdirs() }

    /** The pool's still images: what static wallpapers, the screensaver and photo tags use. */
    fun getImages(): List<File> = list(IMAGE_EXTS)

    /** Videos in the pool. They play through Rotato's live wallpaper. */
    fun getVideos(): List<File> = list(VIDEO_EXTS)

    /** Everything the Library shows: images and videos. */
    fun getMedia(): List<File> = list(IMAGE_EXTS + VIDEO_EXTS)

    private fun list(exts: Set<String>): List<File> =
        imageDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in exts }
            ?.sortedBy { it.name }
            ?: emptyList()

    /**
     * The pool's images, re-read whenever a file lands in or leaves the directory (downloads,
     * imports, deletes, the worker's temp-file renames). It used to poll every two seconds for
     * as long as the Library was open.
     */
    fun mediaFlow(): Flow<List<File>> = callbackFlow {
        val dir = imageDir
        trySend(getMedia())
        // No CREATE: it fires while a copied file is still empty, and the CLOSE_WRITE that follows
        // produces the same file list, so the grid was left showing a broken thumbnail.
        val mask = FileObserver.DELETE or FileObserver.MOVED_TO or
            FileObserver.MOVED_FROM or FileObserver.CLOSE_WRITE
        // The File constructor needs API 29; the path one works back to minSdk 26.
        @Suppress("DEPRECATION")
        val observer = object : FileObserver(dir.absolutePath, mask) {
            override fun onEvent(event: Int, path: String?) {
                // Half-written downloads end in .part; the rename to the real name is what counts.
                if (path == null || path.endsWith(".part")) return
                trySend(getMedia())
            }
        }
        observer.startWatching()
        awaitClose { observer.stopWatching() }
    }.conflate().distinctUntilChanged().flowOn(Dispatchers.IO)

    suspend fun addImage(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        if (mime.startsWith("video/")) return@withContext addVideo(uri, mime)
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext false

            // First pass: read dimensions without allocating pixels
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, opts)
            inputStream.close()

            // Compute inSampleSize to cap decode at ~2048px on the longer side (OOM guard)
            val maxDim = 2048
            var sampleSize = 1
            val largerDim = maxOf(opts.outWidth, opts.outHeight)
            while (largerDim / (sampleSize * 2) >= maxDim) sampleSize *= 2

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decodeStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext false
            val bitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOpts)
            decodeStream.close()

            if (bitmap == null) return@withContext false

            val destFile = File(imageDir, "${UUID.randomUUID()}.jpg")
            destFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Copies a video in as it is (decoding it as a picture is what used to leave a broken file). */
    private fun addVideo(uri: Uri, mime: String): Boolean {
        val ext = when {
            "webm" in mime -> "webm"
            "matroska" in mime -> "mkv"
            "quicktime" in mime -> "mov"
            else -> "mp4"
        }
        val name = UUID.randomUUID().toString()
        val tmp = File(imageDir, "$name.part")
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return false
            input.use { src -> tmp.outputStream().use { src.copyTo(it) } }
            tmp.length() > 0 && tmp.renameTo(File(imageDir, "$name.$ext"))
        } catch (e: Exception) {
            false
        } finally {
            tmp.delete()
        }
    }

    fun removeImage(file: File): Boolean = file.delete()

    fun clearAll() {
        imageDir.listFiles()?.forEach { it.delete() }
    }
}
