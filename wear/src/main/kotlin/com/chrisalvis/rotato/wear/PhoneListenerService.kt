package com.chrisalvis.rotato.wear

import android.widget.Toast
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Receives the phone's wallpaper updates and confirmations. */
class PhoneListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        val items = events
            .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WatchPaths.NOW }
            .map { it.dataItem.freeze() }
        // The service may be stopped as soon as this returns, so finish the download first.
        // onDataChanged runs on a background thread.
        items.lastOrNull()?.let { item -> runBlocking { NowStore.save(applicationContext, item) } }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchPaths.TOAST) return
        val text = String(event.data, Charsets.UTF_8)
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
        }
    }
}
