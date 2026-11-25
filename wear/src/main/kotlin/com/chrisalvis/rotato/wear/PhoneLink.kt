package com.chrisalvis.rotato.wear

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/** Sends Next / Previous / Save / Sync to the phone running Rotato. */
object PhoneLink {

    /** Returns false when no phone with Rotato is reachable. */
    suspend fun send(context: Context, path: String): Boolean = runCatching {
        val nodes = Wearable.getCapabilityClient(context)
            .getCapability(WatchPaths.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
        val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull() ?: return false
        Wearable.getMessageClient(context).sendMessage(node.id, path, ByteArray(0)).await()
        true
    }.getOrDefault(false)
}
