package com.chrisalvis.rotato.wear

import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonColors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/**
 * The phone's wallpaper as a tile, with Save and Next buttons. Tapping the picture opens the app.
 */
class NowTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture {
            when (requestParams.currentState.lastClickableId) {
                ID_NEXT -> PhoneLink.send(this@NowTileService, WatchPaths.NEXT)
                ID_SAVE -> PhoneLink.send(this@NowTileService, WatchPaths.SAVE)
                ID_SYNC -> {
                    PhoneLink.send(this@NowTileService, WatchPaths.SYNC)
                    NowStore.refresh(this@NowTileService)
                }
            }
            val now = NowStore.load(this@NowTileService)
            val device = requestParams.deviceConfiguration
            TileBuilders.Tile.Builder()
                .setResourcesVersion(resourcesVersion(now))
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(now, device)))
                .build()
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = SuspendToFutureAdapter.launchFuture {
        val now = NowStore.load(this@NowTileService)
        val builder = ResourceBuilders.Resources.Builder()
            .setVersion(requestParams.version)
            .addIdToImageMapping(ID_ICON_NEXT, drawable(R.drawable.ic_widget_next))
            .addIdToImageMapping(ID_ICON_SAVE, drawable(R.drawable.ic_widget_save))
        val bytes = (now.tileImage ?: now.image)?.takeIf { it.isFile }?.readBytes()
        if (bytes != null) {
            builder.addIdToImageMapping(
                ID_WALLPAPER,
                ResourceBuilders.ImageResource.Builder()
                    .setInlineResource(
                        ResourceBuilders.InlineImageResource.Builder()
                            .setData(bytes)
                            // UNDEFINED means "encoded image" (our JPEG); the tile decodes it.
                            .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                            .build()
                    )
                    .build()
            )
        }
        builder.build()
    }

    override fun onTileAddEvent(requestParams: EventBuilders.TileAddEvent) {
        // First time on the carousel: ask the phone for the wallpaper right away.
        SuspendToFutureAdapter.launchFuture { PhoneLink.send(this@NowTileService, WatchPaths.SYNC) }
    }

    private fun drawable(id: Int) = ResourceBuilders.ImageResource.Builder()
        .setAndroidResourceByResId(
            ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(id).build()
        )
        .build()

    private fun resourcesVersion(now: NowShowing) = "${now.updatedAt}-${now.hasImage}"

    private fun layout(now: NowShowing, device: DeviceParameters): LayoutElementBuilders.LayoutElement {
        if (!now.hasImage) return emptyLayout(now, device)

        val picture = LayoutElementBuilders.Image.Builder()
            .setResourceId(ID_WALLPAPER)
            .setWidth(expand())
            .setHeight(expand())
            .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_CROP)
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(openApp()).build())
            .build()

        val buttons = LayoutElementBuilders.Row.Builder()
            .addContent(iconButton(ID_SAVE, ID_ICON_SAVE, "Save"))
            .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(12f)).build())
            .addContent(iconButton(ID_NEXT, ID_ICON_NEXT, "Next"))
            .build()

        val controls = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(buttons)
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(18f)).build())
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
            .addContent(picture)
            .addContent(controls)
            .build()
    }

    private fun iconButton(clickId: String, iconId: String, label: String) =
        Button.Builder(this, clickable(clickId))
            .setIconContent(iconId)
            .setContentDescription(label)
            .setButtonColors(ButtonColors(SCRIM, WHITE))
            .build()

    private fun emptyLayout(now: NowShowing, device: DeviceParameters): LayoutElementBuilders.LayoutElement {
        val message = if (now.updatedAt == 0L) "Open Rotato on your phone to see its wallpaper here"
        else "This wallpaper can't be shown on the watch"
        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        Text.Builder(this, message)
                            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                            .setColor(argb(WHITE))
                            .setMaxLines(3)
                            .build()
                    )
                    .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(10f)).build())
                    .addContent(CompactChip.Builder(this, "Sync", clickable(ID_SYNC), device).build())
                    .build()
            )
            .build()
    }

    private fun clickable(id: String) = ModifiersBuilders.Clickable.Builder()
        .setId(id)
        .setOnClick(ActionBuilders.LoadAction.Builder().build())
        .build()

    private fun openApp() = ModifiersBuilders.Clickable.Builder()
        .setId(ID_OPEN)
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(packageName)
                        .setClassName(MainActivity::class.java.name)
                        .build()
                )
                .build()
        )
        .build()

    private companion object {
        const val ID_WALLPAPER = "wallpaper"
        const val ID_ICON_NEXT = "icon_next"
        const val ID_ICON_SAVE = "icon_save"
        const val ID_NEXT = "next"
        const val ID_SAVE = "save"
        const val ID_SYNC = "sync"
        const val ID_OPEN = "open"
        val SCRIM = 0x99000000.toInt()
        val WHITE = 0xFFFFFFFF.toInt()
    }
}
