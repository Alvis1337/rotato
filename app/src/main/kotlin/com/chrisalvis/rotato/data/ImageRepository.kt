package com.chrisalvis.rotato.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
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

    /** Polls the image directory every [intervalMs] ms, emitting only when the list changes. */
    fun imagesFlow(intervalMs: Long = 2_000L): Flow<List<File>> = flow {
        while (true) {
            emit(getImages())
            delay(intervalMs)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

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
