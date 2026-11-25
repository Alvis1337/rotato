package com.chrisalvis.rotato.wear

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/**
 * Puts the phone's wallpaper on any watch face with a photo (background or small image)
 * complication slot. Tapping it opens the Rotato watch app.
 */
class PhotoComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val now = NowStore.load(this)
        val file = now.image?.takeIf { it.isFile } ?: return NoDataComplicationData()
        val icon = file.readBytes().let { Icon.createWithData(it, 0, it.size) }
        return build(request.complicationType, icon, now.caption)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, Icon.createWithResource(this, R.drawable.tile_preview), "Rotato")

    private fun build(type: ComplicationType, icon: Icon, caption: String): ComplicationData? {
        val description = PlainComplicationText.Builder(caption.ifBlank { "Phone wallpaper" }).build()
        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return when (type) {
            ComplicationType.PHOTO_IMAGE ->
                PhotoImageComplicationData.Builder(icon, description).setTapAction(tap).build()
            ComplicationType.SMALL_IMAGE ->
                SmallImageComplicationData.Builder(
                    SmallImage.Builder(icon, SmallImageType.PHOTO).build(),
                    description,
                ).setTapAction(tap).build()
            else -> null
        }
    }
}
