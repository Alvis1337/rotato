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

    private val imageDir: File
        get() = File(context.filesDir, "rotato_images").also { it.mkdirs() }

    fun getImages(): List<File> {
        val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif")
        return imageDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in imageExts }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /**
     * The pool's images, re-read whenever a file lands in or leaves the directory (downloads,
     * imports, deletes, the worker's temp-file renames). It used to poll every two seconds for
     * as long as the Library was open.
     */
    fun imagesFlow(): Flow<List<File>> = callbackFlow {
        val dir = imageDir
        trySend(getImages())
        val mask = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_TO or
            FileObserver.MOVED_FROM or FileObserver.CLOSE_WRITE
        // The File constructor needs API 29; the path one works back to minSdk 26.
        @Suppress("DEPRECATION")
        val observer = object : FileObserver(dir.absolutePath, mask) {
            override fun onEvent(event: Int, path: String?) {
                // Half-written downloads end in .part; the rename to the real name is what counts.
                if (path == null || path.endsWith(".part")) return
                trySend(getImages())
            }
        }
        observer.startWatching()
        awaitClose { observer.stopWatching() }
    }.conflate().distinctUntilChanged().flowOn(Dispatchers.IO)

    suspend fun addImage(uri: Uri): Boolean = withContext(Dispatchers.IO) {
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

    fun removeImage(file: File): Boolean = file.delete()

    fun clearAll() {
        imageDir.listFiles()?.forEach { it.delete() }
    }
}
