package com.chrisalvis.rotato.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ImageRepository(private val context: Context) {

    private val imageDir: File
        get() = File(context.filesDir, "rotato_images").also { it.mkdirs() }

    fun getImages(): List<File> {
        return imageDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".jpg") }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    suspend fun addImage(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext false

            // Decode into a bitmap so we normalize the format and strip metadata
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

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
