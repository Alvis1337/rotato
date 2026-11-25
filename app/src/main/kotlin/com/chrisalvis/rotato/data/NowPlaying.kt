package com.chrisalvis.rotato.data

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.chrisalvis.rotato.worker.NowPlayingService
import com.chrisalvis.rotato.worker.WallpaperWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** When this artist plays, the wallpaper comes from this collection instead of the album art. */
data class ArtistRule(val artist: String, val collectionId: String)

/** What a music app says is playing. [art] is a private copy that [NowPlaying] may recycle. */
data class PlayingTrack(val artist: String, val album: String, val title: String, val art: Bitmap?)

/**
 * Now playing mode (opt-in): while music plays, the wallpaper shows the album art, or a
 * wallpaper from the collection tied to that artist. When the music stops, the wallpaper from
 * before comes back and rotation carries on. Android only shares what's playing with apps that
 * have notification access; [NowPlayingService] holds it and never reads the notifications.
 */
object NowPlaying {
    private const val PREFS = "now_playing"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ALBUM_ART = "album_art"
    private const val KEY_RULES = "artist_rules"
    private const val KEY_SHOWING = "showing"
    private const val KEY_SHOWN = "shown_key"
    private const val KEY_LAST_ARTIST = "last_artist"

    /** One change at a time: a quick track skip must not race the previous set. */
    private val lock = Mutex()

    private fun sp(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = sp(context).getBoolean(KEY_ENABLED, false)

    /** Album art for music no artist rule covers. Off = only artists with a collection change it. */
    fun albumArt(context: Context) = sp(context).getBoolean(KEY_ALBUM_ART, true)

    fun rules(context: Context): List<ArtistRule> = runCatching {
        val arr = JSONArray(sp(context).getString(KEY_RULES, "[]"))
        (0 until arr.length()).map { i -> arr.getJSONObject(i).let { ArtistRule(it.getString("a"), it.getString("c")) } }
    }.getOrDefault(emptyList())

    /** True while the wallpaper on screen is a now playing one, so rotation holds off. */
    fun isShowing(context: Context) = enabled(context) && sp(context).getBoolean(KEY_SHOWING, false)

    /** The artist heard most recently, offered in Tools for a new rule. */
    fun lastArtist(context: Context): String = sp(context).getString(KEY_LAST_ARTIST, "").orEmpty()

    fun hasAccess(context: Context) =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    private fun component(context: Context) = ComponentName(context, NowPlayingService::class.java)

    /** Opens the system screen where Rotato gets notification access (straight to it on Android 11+). */
    fun accessIntent(context: Context): Intent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Fallback when a phone has no per-app access screen. */
    fun accessListIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun setEnabled(context: Context, on: Boolean) {
        sp(context).edit().putBoolean(KEY_ENABLED, on).apply()
        val app = context.applicationContext
        if (on) {
            // The listener unbinds itself while the mode is off; ask Android to connect it again.
            runCatching { NotificationListenerService.requestRebind(component(app)) }
        } else {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { stopped(app, force = true) } }
        }
    }

    fun setAlbumArt(context: Context, on: Boolean) {
        sp(context).edit().putBoolean(KEY_ALBUM_ART, on).remove(KEY_SHOWN).apply()
    }

    fun setRules(context: Context, rules: List<ArtistRule>) {
        val arr = JSONArray().also { a -> rules.forEach { a.put(JSONObject().put("a", it.artist).put("c", it.collectionId)) } }
        // Forget what's shown so the next track picks up the change.
        sp(context).edit().putString(KEY_RULES, arr.toString()).remove(KEY_SHOWN).apply()
    }

    /** The rule for [artist]: an exact name first, then one named among several ("A, B", "A feat. B"). */
    private fun ruleFor(context: Context, artist: String): ArtistRule? {
        if (artist.isBlank()) return null
        val rules = rules(context)
        rules.firstOrNull { it.artist.equals(artist, ignoreCase = true) }?.let { return it }
        val parts = artist.split(Regex("""\s*(,|&|/|;|\bfeat\.?|\bft\.?|\bfeaturing|\bx\b|\band\b)\s*""", RegexOption.IGNORE_CASE))
            .map { it.trim() }.filter { it.isNotEmpty() }
        return rules.firstOrNull { rule -> parts.any { it.equals(rule.artist, ignoreCase = true) } }
    }

    /** Music is playing [track]. Changes the wallpaper when the album (or artist collection) changed. */
    suspend fun playing(context: Context, track: PlayingTrack) {
        try {
            lock.withLock { applyTrack(context, track) }
        } finally {
            track.art?.takeIf { !it.isRecycled }?.recycle()
        }
    }

    private suspend fun applyTrack(context: Context, track: PlayingTrack) {
        if (!enabled(context)) return
        val store = sp(context)
        if (track.artist.isNotBlank()) store.edit().putString(KEY_LAST_ARTIST, track.artist).apply()
        val rule = ruleFor(context, track.artist)
        val key = when {
            rule != null -> "c:${rule.collectionId}"
            track.art != null && albumArt(context) -> "a:${track.artist}|${track.album.ifBlank { track.title }}"
            else -> return // Nothing to show for this one: leave whatever is on screen.
        }
        if (store.getBoolean(KEY_SHOWING, false) && store.getString(KEY_SHOWN, null) == key) return
        val ok = if (rule != null) showFromCollection(context, rule.collectionId) else showAlbumArt(context, track.art!!)
        if (ok) store.edit().putBoolean(KEY_SHOWING, true).putString(KEY_SHOWN, key).apply()
    }

    /** The music stopped: put back the wallpaper from before and let rotation carry on. */
    suspend fun stopped(context: Context, force: Boolean = false) = lock.withLock {
        val store = sp(context)
        if (!store.getBoolean(KEY_SHOWING, false)) return@withLock
        if (!force && !enabled(context)) return@withLock
        store.edit().putBoolean(KEY_SHOWING, false).remove(KEY_SHOWN).apply()
        // Album art is never pushed onto the applied list, so its last entry is the one from before.
        val previous = RotatoPreferences(context).appliedWallpaperPaths.first().lastOrNull()?.let(::File)
        val restored = previous != null && previous.exists() &&
            runCatching { applyWallpaperFile(context, previous, recordAsCurrent = false) == null }.getOrDefault(false)
        if (!restored) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "now_playing_restore",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WallpaperWorker>()
                    .setInputData(workDataOf(WallpaperWorker.KEY_MANUAL to true))
                    .build(),
            )
        }
    }

    private suspend fun showFromCollection(context: Context, collectionId: String): Boolean {
        val listPrefs = LocalListsPreferences(context)
        val list = listPrefs.lists.first().firstOrNull { it.id == collectionId } ?: return false
        val hidden = RotatoPreferences(context).nsfwHidden.first()
        if (hidden && list.isLocked) return false
        val entries = listPrefs.allWallpapers.first()
            .filter { it.listId == collectionId && !it.isVideo && !(hidden && it.isNsfw) }
        // A few tries, in case one image has gone missing.
        for (entry in entries.shuffled().take(3)) {
            val url = entry.fullUrl.ifBlank { entry.sampleUrl }
            if (url.isBlank()) continue
            val history = WallpaperHistoryItem(
                thumbUrl = entry.thumbUrl, sampleUrl = entry.sampleUrl, fullUrl = entry.fullUrl,
                source = entry.source, timestamp = System.currentTimeMillis(), tags = entry.tags, pageUrl = entry.pageUrl,
            )
            if (applyWallpaperFromUrl(context, url, entry.isNsfw, history) == null) return true
        }
        return false
    }

    private suspend fun showAlbumArt(context: Context, art: Bitmap): Boolean = runCatching {
        val settings = RotatoPreferences(context).settings.first()
        val flags = when (settings.wallpaperTarget) {
            WallpaperTarget.HOME_ONLY -> WallpaperManager.FLAG_SYSTEM
            WallpaperTarget.LOCK_ONLY -> WallpaperManager.FLAG_LOCK
            WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }
        val frame = albumArtWallpaper(art, wallpaperTargetSize(context))
        try {
            // Already laid out for every screen, so no fit or effects on top.
            setWallpaperBitmap(context, WallpaperManager.getInstance(context), frame, flags)
        } finally {
            frame.recycle()
        }
        true
    }.getOrDefault(false)

    /**
     * The cover, sharp and centred, over a blurred, darkened copy of itself filling the screen.
     * Sized to the narrowest screen so a foldable's cover display shows all of it too.
     */
    internal fun albumArtWallpaper(art: Bitmap, canvas: WallpaperCanvas): Bitmap {
        val w = canvas.width
        val h = canvas.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        // Background: shrink a lot, then stretch back up in two steps for a soft, even blur.
        val tiny = Bitmap.createScaledBitmap(art, 16, 16, true)
        val mid = Bitmap.createScaledBitmap(tiny, 96, 96, true)
        tiny.recycle()
        val side = maxOf(w, h).toFloat()
        c.drawBitmap(mid, null, RectF((w - side) / 2f, (h - side) / 2f, (w + side) / 2f, (h + side) / 2f), paint)
        mid.recycle()
        c.drawColor(Color.argb(110, 0, 0, 0))

        // Foreground: the cover with rounded corners, a little above centre to clear the dock.
        val size = minOf(canvas.narrowestWidth, h) * 0.72f
        val cx = w / 2f
        val cy = h * 0.44f
        val rect = RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
        val radius = size * 0.045f
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
        }
        c.drawRoundRect(RectF(rect.left, rect.top + size * 0.02f, rect.right, rect.bottom + size * 0.03f), radius, radius, shadow)
        c.save()
        c.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
        // Covers aren't always square: crop to the middle square.
        val s = minOf(art.width, art.height)
        val src = android.graphics.Rect((art.width - s) / 2, (art.height - s) / 2, (art.width + s) / 2, (art.height + s) / 2)
        c.drawBitmap(art, src, rect, paint)
        c.restore()
        return out
    }
}
