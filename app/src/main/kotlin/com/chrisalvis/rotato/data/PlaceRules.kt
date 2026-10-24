package com.chrisalvis.rotato.data

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.chrisalvis.rotato.worker.AutomationReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** On this Wi-Fi network, rotate from this collection. */
data class PlaceRule(val ssid: String, val collectionId: String)

/**
 * Place rules: which collection to rotate from depends on the Wi-Fi you're on (home, work, a
 * friend's), with an optional one for everywhere else. Android only reveals the Wi-Fi name to
 * apps with location access, and to apps in the background only with "Allow all the time".
 */
object PlaceRules {
    private const val PREFS = "place_rules"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_RULES = "rules"
    private const val KEY_ELSEWHERE = "elsewhere"
    private const val KEY_LAST = "last_applied"

    private fun sp(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = sp(context).getBoolean(KEY_ENABLED, false)

    fun rules(context: Context): List<PlaceRule> = runCatching {
        val arr = JSONArray(sp(context).getString(KEY_RULES, "[]"))
        (0 until arr.length()).map { i -> arr.getJSONObject(i).let { PlaceRule(it.getString("s"), it.getString("c")) } }
    }.getOrDefault(emptyList())

    /** Collection for anywhere no rule covers (no Wi-Fi, or another network); blank = leave it. */
    fun elsewhere(context: Context): String = sp(context).getString(KEY_ELSEWHERE, "").orEmpty()

    fun setEnabled(context: Context, on: Boolean) {
        sp(context).edit().putBoolean(KEY_ENABLED, on).remove(KEY_LAST).apply()
        register(context)
        if (on) applyAsync(context)
    }

    fun setRules(context: Context, rules: List<PlaceRule>) {
        val arr = JSONArray().also { a -> rules.forEach { a.put(JSONObject().put("s", it.ssid).put("c", it.collectionId)) } }
        sp(context).edit().putString(KEY_RULES, arr.toString()).remove(KEY_LAST).apply()
        applyAsync(context)
    }

    fun setElsewhere(context: Context, collectionId: String) {
        sp(context).edit().putString(KEY_ELSEWHERE, collectionId).remove(KEY_LAST).apply()
        applyAsync(context)
    }

    fun hasLocation(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun onWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    /** The connected Wi-Fi's name, or null when not on Wi-Fi or Android won't say. */
    @Suppress("DEPRECATION")
    fun currentSsid(context: Context): String? {
        if (!hasLocation(context) || !onWifi(context)) return null
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null
        val raw = runCatching { wifi.connectionInfo?.ssid }.getOrNull() ?: return null
        return raw.removeSurrounding("\"").takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }

    /** The collection the current place calls for, or null to leave rotation as it is. */
    private fun targetFor(context: Context): String? {
        val rules = rules(context)
        val ssid = currentSsid(context)
        rules.firstOrNull { it.ssid == ssid }?.let { return it.collectionId }
        // Only call it "elsewhere" when we know: off Wi-Fi, or on a network we could name.
        val known = !onWifi(context) || ssid != null
        return elsewhere(context).takeIf { it.isNotBlank() && known }
    }

    /**
     * Switches collection when the place changed since the last switch. [rotate] also sets a new
     * wallpaper from it straight away (not wanted when called from inside a rotation).
     */
    suspend fun apply(context: Context, rotate: Boolean = true) {
        if (!enabled(context)) return
        val target = targetFor(context) ?: return
        if (sp(context).getString(KEY_LAST, null) == target) return
        AutomationReceiver.useOnlyCollection(context, target, rotate)
        sp(context).edit().putString(KEY_LAST, target).apply()
    }

    private fun applyAsync(context: Context) {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { apply(app) } }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, PlaceRulesReceiver::class.java),
            // Mutable: the system adds the network to the intent.
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
        )

    /** Asks Android to wake [PlaceRulesReceiver] whenever Wi-Fi connects, even with Rotato closed. */
    fun register(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val pi = pendingIntent(context)
        runCatching { cm.unregisterNetworkCallback(pi) }
        if (!enabled(context)) return
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        runCatching { cm.registerNetworkCallback(request, pi) }
    }
}

class PlaceRulesReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Give the connection a moment to settle so the network name is readable.
                kotlinx.coroutines.delay(3_000)
                PlaceRules.apply(context.applicationContext)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
