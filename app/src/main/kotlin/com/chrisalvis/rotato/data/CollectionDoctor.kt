package com.chrisalvis.rotato.data

import android.content.Context
import com.chrisalvis.rotato.data.plugins.PluginExecutor
import com.chrisalvis.rotato.data.plugins.PluginRepository
import com.chrisalvis.rotato.data.plugins.PostLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** What a collection check found. [unchecked] couldn't be reached (offline, timeouts), so they aren't called dead. */
data class DoctorReport(
    val checked: Int,
    val dead: List<LocalWallpaperEntry>,
    val unchecked: Int,
    /** Entries whose post had moved to a new link; already saved with the new one. */
    val repaired: Int = 0,
)

/**
 * Collection doctor: finds saved wallpapers whose image is gone (deleted posts, moved files,
 * photos removed from the phone) and repairs what it can. Booru posts often still exist when
 * their image link has changed, so a dead link is first looked up again by post id on its site.
 */
object CollectionDoctor {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private enum class Health { OK, DEAD, UNKNOWN }

    private fun refererFor(url: String): String? = when {
        url.contains("donmai.us") -> "https://danbooru.donmai.us/"
        url.contains("gelbooru.com") -> "https://gelbooru.com/"
        url.contains("rule34.xxx") -> "https://rule34.xxx/"
        else -> null
    }

    private fun probe(url: String): Health {
        fun request(head: Boolean) = Request.Builder().url(url).header("User-Agent", UA)
            .apply { refererFor(url)?.let { header("Referer", it) } }
            .apply { if (head) head() else header("Range", "bytes=0-0") }
            .build()
        return try {
            var code = 0
            var type: String? = null
            http.newCall(request(head = true)).execute().use { code = it.code; type = it.header("Content-Type") }
            // Some image hosts don't answer HEAD (or answer it wrongly); ask for the first byte instead.
            if (code !in 200..299) {
                http.newCall(request(head = false)).execute().use { code = it.code; type = it.header("Content-Type") }
            }
            when {
                code == 404 || code == 410 -> Health.DEAD
                code in 200..299 || code == 206 ->
                    // A "this image was removed" page served in its place.
                    if (type?.startsWith("text/html") == true) Health.DEAD else Health.OK
                else -> Health.UNKNOWN
            }
        } catch (e: Exception) {
            Health.UNKNOWN
        }
    }

    suspend fun check(context: Context, onProgress: (done: Int, total: Int) -> Unit): DoctorReport = withContext(Dispatchers.IO) {
        val entries = LocalListsPreferences(context).allWallpapers.first()
        val total = entries.size
        val done = AtomicInteger(0)
        val unknown = AtomicInteger(0)
        val gate = Semaphore(6)
        // One request per distinct image, however many collections it's saved in.
        val byUrl = entries.groupBy { it.fullUrl }
        val deadUrls = coroutineScope {
            byUrl.keys.map { url ->
                async {
                    gate.withPermit {
                        val health = when {
                            url.startsWith("list_images/") -> if (File(context.filesDir, url).exists()) Health.OK else Health.DEAD
                            url.startsWith("http") -> probe(url)
                            url.isBlank() -> Health.DEAD
                            else -> Health.OK
                        }
                        if (health == Health.UNKNOWN) unknown.addAndGet(byUrl[url]?.size ?: 0)
                        onProgress(done.addAndGet(byUrl[url]?.size ?: 0), total)
                        url.takeIf { health == Health.DEAD }
                    }
                }
            }.awaitAll().filterNotNull().toSet()
        }
        // An image link that looks dead isn't proof the post is: hosts block probes, and boorus
        // move files. Each booru post is asked for on its site before it's called broken.
        val suspects = entries.filter { it.fullUrl in deadUrls }
        val verdicts = confirm(context, suspects)
        // No verdict (not a booru post): the probe's word stands.
        val stillDead = suspects.filter { verdicts[it.id].let { v -> v == null || v == Verdict.Gone } }
        val unconfirmed = suspects.count { verdicts[it.id] == Verdict.Unknown }
        val moved = suspects.mapNotNull { e ->
            val p = (verdicts[e.id] as? Verdict.Moved)?.post ?: return@mapNotNull null
            e.copy(
                fullUrl = p.fullUrl,
                sampleUrl = p.sampleUrl,
                thumbUrl = p.thumbUrl,
                resolution = p.resolution.ifBlank { e.resolution },
                pageUrl = p.pageUrl.ifBlank { e.pageUrl },
            )
        }
        if (moved.isNotEmpty()) LocalListsPreferences(context).updateEntries(moved)
        DoctorReport(total, stillDead, unknown.get() + unconfirmed, repaired = moved.size)
    }

    private sealed interface Verdict {
        /** The post and its image are fine; the probe was wrong. */
        data object Fine : Verdict
        data class Moved(val post: com.chrisalvis.rotato.data.BrainrotWallpaper) : Verdict
        data object Gone : Verdict
        data object Unknown : Verdict
    }

    /**
     * Asks each suspect's site about its post. Entries that aren't booru posts get no verdict
     * (their probe result stands).
     */
    private suspend fun confirm(context: Context, suspects: List<LocalWallpaperEntry>): Map<String, Verdict> = coroutineScope {
        if (suspects.isEmpty()) return@coroutineScope emptyMap()
        val manifests = PluginRepository(context).manifests.first()
        val sources = LocalSourcesPreferences(context).sources.first()
        val gate = Semaphore(3)
        // One question per post, however many collections hold it.
        suspects.filter { it.fullUrl.startsWith("http") && it.sourceId.isNotBlank() }
            .groupBy { it.source.lowercase() to it.sourceId }
            .map { (key, group) ->
                async {
                    val manifest = manifests.firstOrNull { it.id.equals(key.first, ignoreCase = true) }
                        ?: return@async emptyList<Pair<String, Verdict>>()
                    val engine = PluginExecutor.engineFor(manifest) ?: return@async emptyList<Pair<String, Verdict>>()
                    val source = sources.firstOrNull { it.pluginId.equals(manifest.id, ignoreCase = true) } ?: LocalSource(pluginId = manifest.id)
                    val verdict = gate.withPermit {
                        when (val r = runCatching { engine.lookupPost(manifest, source, key.second) }.getOrDefault(PostLookup.Unknown)) {
                            is PostLookup.Found -> if (r.post.fullUrl == group.first().fullUrl) Verdict.Fine else Verdict.Moved(r.post)
                            PostLookup.Gone -> Verdict.Gone
                            PostLookup.Unknown -> Verdict.Unknown
                        }
                    }
                    group.map { it.id to verdict }
                }
            }.awaitAll().flatten().toMap()
    }

    /**
     * Looks each dead booru entry up again by its post id and saves the fresh links. Returns the
     * ids of the entries it repaired; the rest are really gone.
     */
    suspend fun repair(context: Context, dead: List<LocalWallpaperEntry>): Set<String> = withContext(Dispatchers.IO) {
        val manifests = PluginRepository(context).manifests.first()
        val sources = LocalSourcesPreferences(context).sources.first()
        val fixed = mutableListOf<LocalWallpaperEntry>()
        for (entry in dead) {
            if (!entry.fullUrl.startsWith("http") || entry.sourceId.isBlank()) continue
            val manifest = manifests.firstOrNull { it.id.equals(entry.source, ignoreCase = true) } ?: continue
            val engine = PluginExecutor.engineFor(manifest) ?: continue
            val source = sources.firstOrNull { it.pluginId.equals(manifest.id, ignoreCase = true) } ?: LocalSource(pluginId = manifest.id)
            // Looked up by id with no rating filter: the old search-based lookup only found posts
            // rated the way the entry was flagged, so most repairs found nothing.
            val post = (runCatching { engine.lookupPost(manifest, source, entry.sourceId) }.getOrNull() as? PostLookup.Found)?.post ?: continue
            fixed += entry.copy(
                fullUrl = post.fullUrl,
                sampleUrl = post.sampleUrl,
                thumbUrl = post.thumbUrl,
                resolution = post.resolution.ifBlank { entry.resolution },
                pageUrl = post.pageUrl.ifBlank { entry.pageUrl },
            )
        }
        LocalListsPreferences(context).updateEntries(fixed)
        fixed.mapTo(HashSet()) { it.id }
    }
}
