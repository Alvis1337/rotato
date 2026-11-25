package com.chrisalvis.rotato.watch

import android.content.Intent
import com.chrisalvis.rotato.worker.AutomationReceiver
import com.chrisalvis.rotato.worker.FavoriteWallpaperReceiver
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Handles Next, Previous, Save and Sync sent from the watch app. */
class WatchListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        val app = applicationContext
        when (event.path) {
            // Rotation and Previous refresh the widget when they finish, which pushes the new
            // wallpaper to the watch (see RotatoWidgetProvider.refreshAll).
            WatchPaths.NEXT -> scope.launch {
                AutomationReceiver.handle(app, Intent(AutomationReceiver.ACTION_NEXT))
            }
            WatchPaths.PREVIOUS -> scope.launch {
                AutomationReceiver.handle(app, Intent(AutomationReceiver.ACTION_PREVIOUS))
            }
            WatchPaths.SAVE -> scope.launch {
                val (title, _) = FavoriteWallpaperReceiver.saveCurrent(app)
                WatchSync.reply(app, event.sourceNodeId, title)
            }
            WatchPaths.SYNC -> scope.launch { runCatching { WatchSync.pushNow(app) } }
        }
    }

    /** The watch app was just installed or came back in range: give it the wallpaper now. */
    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.name == WatchPaths.CAPABILITY_WATCH && info.nodes.isNotEmpty()) WatchSync.push(applicationContext)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
