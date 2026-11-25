package com.chrisalvis.rotato.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.chrisalvis.rotato.worker.AutomationReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/** The kinds of weather a rule can match, from Open-Meteo's WMO weather codes. */
enum class WeatherKind(val label: String) {
    CLEAR("Clear"),
    CLOUDY("Cloudy"),
    FOG("Fog"),
    RAIN("Rain"),
    SNOW("Snow"),
    STORM("Storm");

    companion object {
        fun fromWmo(code: Int): WeatherKind = when (code) {
            0, 1 -> CLEAR
            2, 3 -> CLOUDY
            45, 48 -> FOG
            in 51..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> STORM
            else -> CLOUDY
        }
    }
}

/** A place weather is read for, picked by name search or from the phone's location. */
data class WeatherPlace(val name: String, val lat: Double, val lon: Double)

/**
 * Weather rules: rain, snow, clear skies and so on each rotate from their own collection, with
 * an optional one for any other weather. Weather comes from Open-Meteo (free, no key, no
 * account), at most every 30 minutes, and is checked at the start of each rotation, right after
 * place rules, so when both have a rule the weather one wins. A seasonal schedule in its dates
 * wins over both.
 */
object WeatherRules {
    private const val PREFS = "weather_rules"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_RULES = "rules"
    private const val KEY_OTHERWISE = "otherwise"
    private const val KEY_LAST = "last_applied"
    private const val KEY_PLACE_NAME = "place_name"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val KEY_USE_DEVICE = "use_device_location"
    private const val KEY_KIND = "cached_kind"
    private const val KEY_TEMP = "cached_temp"
    private const val KEY_FETCHED = "cached_at"
    private const val CACHE_MS = 30 * 60_000L

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private fun sp(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = sp(context).getBoolean(KEY_ENABLED, false)

    fun rules(context: Context): Map<WeatherKind, String> = runCatching {
        val o = JSONObject(sp(context).getString(KEY_RULES, "{}") ?: "{}")
        WeatherKind.entries.mapNotNull { k -> o.optString(k.name).takeIf { it.isNotBlank() }?.let { k to it } }.toMap()
    }.getOrDefault(emptyMap())

    /** Collection for weather no rule covers; blank = leave rotation as it is. */
    fun otherwise(context: Context): String = sp(context).getString(KEY_OTHERWISE, "").orEmpty()

    fun place(context: Context): WeatherPlace? {
        val p = sp(context)
        if (!p.contains(KEY_LAT)) return null
        return WeatherPlace(
            p.getString(KEY_PLACE_NAME, "").orEmpty(),
            p.getFloat(KEY_LAT, 0f).toDouble(),
            p.getFloat(KEY_LON, 0f).toDouble(),
        )
    }

    fun useDeviceLocation(context: Context) = sp(context).getBoolean(KEY_USE_DEVICE, false)

    /** The last weather read, and when, or null if none yet. */
    fun cached(context: Context): Triple<WeatherKind, Double, Long>? {
        val p = sp(context)
        val kind = p.getString(KEY_KIND, null)?.let { runCatching { WeatherKind.valueOf(it) }.getOrNull() } ?: return null
        return Triple(kind, p.getFloat(KEY_TEMP, 0f).toDouble(), p.getLong(KEY_FETCHED, 0L))
    }

    fun setEnabled(context: Context, on: Boolean) {
        // The caller fetches fresh weather and applies it.
        sp(context).edit().putBoolean(KEY_ENABLED, on).remove(KEY_LAST).apply()
    }

    fun setRule(context: Context, kind: WeatherKind, collectionId: String?) {
        val o = JSONObject(rules(context).mapKeys { it.key.name })
        if (collectionId.isNullOrBlank()) o.remove(kind.name) else o.put(kind.name, collectionId)
        sp(context).edit().putString(KEY_RULES, o.toString()).remove(KEY_LAST).apply()
        applyAsync(context)
    }

    fun setOtherwise(context: Context, collectionId: String) {
        sp(context).edit().putString(KEY_OTHERWISE, collectionId).remove(KEY_LAST).apply()
        applyAsync(context)
    }

    fun setPlace(context: Context, place: WeatherPlace, fromDevice: Boolean = false) {
        sp(context).edit()
            .putString(KEY_PLACE_NAME, place.name)
            // Two decimals is about a kilometre: plenty for weather, and no more precise than needed.
            .putFloat(KEY_LAT, round2(place.lat).toFloat())
            .putFloat(KEY_LON, round2(place.lon).toFloat())
            .putBoolean(KEY_USE_DEVICE, fromDevice)
            .remove(KEY_FETCHED)
            .remove(KEY_LAST)
            .apply()
    }

    private fun round2(v: Double) = Math.round(v * 100) / 100.0

    fun hasLocation(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The phone's last known position, without turning on GPS. Null without permission or a fix. */
    @Suppress("MissingPermission")
    fun deviceLocation(context: Context): WeatherPlace? {
        if (!hasLocation(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val best = runCatching {
            lm.getProviders(true)
                .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull() ?: return null
        return WeatherPlace("Your location", best.latitude, best.longitude)
    }

    /** Places matching [query], from Open-Meteo's free geocoding search. */
    suspend fun searchPlaces(query: String): List<WeatherPlace> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList<WeatherPlace>()
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", query.trim())
            .addQueryParameter("count", "6")
            .addQueryParameter("language", Locale.getDefault().language)
            .build()
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<WeatherPlace>()
                val results = JSONObject(resp.body?.string().orEmpty()).optJSONArray("results") ?: return@use emptyList<WeatherPlace>()
                (0 until results.length()).map { i ->
                    val r = results.getJSONObject(i)
                    val label = listOf(r.optString("name"), r.optString("admin1"), r.optString("country"))
                        .filter { it.isNotBlank() }.distinct().joinToString(", ")
                    WeatherPlace(label, r.getDouble("latitude"), r.getDouble("longitude"))
                }
            }
        }.getOrDefault(emptyList())
    }

    /** The weather now, cached for 30 minutes. [force] skips the cache. Null when offline or no place set. */
    suspend fun currentWeather(context: Context, force: Boolean = false): WeatherKind? = withContext(Dispatchers.IO) {
        val p = sp(context)
        val cached = cached(context)
        if (!force && cached != null && System.currentTimeMillis() - cached.third < CACHE_MS) return@withContext cached.first
        // Follow the phone around when the person chose their location, else use the saved place.
        val where = (if (useDeviceLocation(context)) deviceLocation(context) else null) ?: place(context)
            ?: return@withContext cached?.first
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", round2(where.lat).toString())
            .addQueryParameter("longitude", round2(where.lon).toString())
            .addQueryParameter("current", "weather_code,temperature_2m")
            .build()
        val fresh = runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val current = JSONObject(resp.body?.string().orEmpty()).getJSONObject("current")
                WeatherKind.fromWmo(current.getInt("weather_code")) to current.optDouble("temperature_2m", 0.0)
            }
        }.getOrNull()
        if (fresh == null) {
            // Offline: keep using the last reading for a few hours rather than flip-flopping.
            return@withContext cached?.takeIf { System.currentTimeMillis() - it.third < 6 * 60 * 60_000L }?.first
        }
        p.edit()
            .putString(KEY_KIND, fresh.first.name)
            .putFloat(KEY_TEMP, fresh.second.toFloat())
            .putLong(KEY_FETCHED, System.currentTimeMillis())
            .apply()
        fresh.first
    }

    /**
     * Switches collection when the weather calls for a different one than last time. [rotate]
     * also sets a new wallpaper straight away (not wanted when called from inside a rotation).
     */
    suspend fun apply(context: Context, rotate: Boolean = true, force: Boolean = false) {
        if (!enabled(context)) return
        // A seasonal schedule in its dates (Christmas, say) keeps its collection whatever the sky does.
        if (SchedulePreferences(context).entries.first().any { it.seasonActive() }) return
        val kind = currentWeather(context, force) ?: return
        val target = rules(context)[kind] ?: otherwise(context).takeIf { it.isNotBlank() } ?: return
        if (sp(context).getString(KEY_LAST, null) == target) return
        AutomationReceiver.useOnlyCollection(context, target, rotate)
        sp(context).edit().putString(KEY_LAST, target).apply()
    }

    fun applyAsync(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { apply(app, force = force) } }
    }
}
