package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.matches
import org.json.JSONObject

/** Words of a photo's title or caption, as tags, so taste and blocking work on photos too. */
private fun captionTags(text: String): List<String> =
    text.substringBefore('(').lowercase()
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length > 2 && it !in setOf("the", "and", "from", "with", "view", "file", "jpg", "jpeg", "png") }
        .distinct()
        .take(12)

/**
 * Bing's photos of the day (`/HPImageArchive.aspx`): the last 16 days, at 4K. There's no search,
 * so a tag query returns nothing here.
 */
object BingEngine : PluginEngine() {
    override val protocol = Protocol.BING

    override suspend fun fetch(
        manifest: PluginManifest, source: LocalSource, query: String, exclude: List<String>,
        nsfw: Boolean, filters: BrainrotFilters,
    ): BrainrotWallpaper? = fetchPage(manifest, source, query, exclude, nsfw, filters, 16).randomOrNull()

    override suspend fun fetchPage(
        manifest: PluginManifest, source: LocalSource, query: String, exclude: List<String>,
        nsfw: Boolean, filters: BrainrotFilters, limit: Int,
    ): List<BrainrotWallpaper> = onIO {
        if (!canServe(manifest, nsfw, source) || query.isNotBlank()) return@onIO emptyList()
        val base = baseUrl(manifest, source)
        val market = source.extraConfig["market"].orEmpty().ifBlank { manifest.extras["market"] ?: "en-US" }
        // The archive serves at most 8 days per call; idx 8 reaches the previous 8.
        listOf(0, 8).flatMap { idx ->
            val json = getJson("$base/HPImageArchive.aspx?format=js&idx=$idx&n=8&mkt=${market.urlEncode()}")
            json?.optJSONArray("images")?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } }.orEmpty()
        }.mapNotNull { img ->
            val urlBase = img.optString("urlbase").ifBlank { return@mapNotNull null }
            val id = img.optString("startdate").ifBlank { urlBase.substringAfter("OHR.").substringBefore('_') }
            if (id in exclude || !filters.matches(3840, 2160)) return@mapNotNull null
            val title = img.optString("title").ifBlank { img.optString("copyright") }
            BrainrotWallpaper(
                id = id,
                source = manifest.id.lowercase(),
                thumbUrl = "$base${urlBase}_480x270.jpg",
                sampleUrl = "$base${urlBase}_1920x1080.jpg",
                fullUrl = "$base${urlBase}_UHD.jpg",
                resolution = "3840x2160",
                pageUrl = img.optString("copyrightlink").ifBlank { "$base/" },
                tags = captionTags(title + " " + img.optString("copyright")),
            )
        }.distinctBy { it.id }.take(limit)
    }
}

/**
 * Wikimedia Commons' featured pictures: high-resolution photos, freely licensed. With a query it
 * searches within the featured set; without one it starts at a random point in it.
 */
object WikimediaEngine : PluginEngine() {
    override val protocol = Protocol.WIKIMEDIA
    private const val CATEGORY = "Featured_pictures_on_Wikimedia_Commons"

    override suspend fun fetch(
        manifest: PluginManifest, source: LocalSource, query: String, exclude: List<String>,
        nsfw: Boolean, filters: BrainrotFilters,
    ): BrainrotWallpaper? = fetchPage(manifest, source, query, exclude, nsfw, filters, 20).randomOrNull()

    override suspend fun fetchPage(
        manifest: PluginManifest, source: LocalSource, query: String, exclude: List<String>,
        nsfw: Boolean, filters: BrainrotFilters, limit: Int,
    ): List<BrainrotWallpaper> = onIO {
        if (!canServe(manifest, nsfw, source)) return@onIO emptyList()
        val base = baseUrl(manifest, source)
        val props = "&prop=imageinfo&iiprop=url%7Csize%7Cmime&iiurlwidth=1280"
        val n = limit.coerceIn(10, 50)
        val url = if (query.isBlank()) {
            // Random entry point: featured pictures sorted by the time they were added, from a random year.
            val year = (2008..java.time.Year.now().value).random()
            val month = (1..12).random().toString().padStart(2, '0')
            "$base/w/api.php?action=query&format=json&generator=categorymembers&gcmtitle=Category:$CATEGORY" +
                "&gcmtype=file&gcmsort=timestamp&gcmstart=$year-$month-01T00:00:00Z&gcmlimit=$n$props"
        } else {
            val q = "${query.replace('_', ' ')} incategory:$CATEGORY"
            "$base/w/api.php?action=query&format=json&generator=search&gsrnamespace=6&gsrlimit=$n" +
                "&gsrsearch=${q.urlEncode()}&gsroffset=${(0..3).random() * n}$props"
        }
        val pages = getJson(url, "User-Agent" to "Rotato/1.0 (https://github.com/Alvis1337/rotato)")
            ?.optJSONObject("query")?.optJSONObject("pages") ?: return@onIO emptyList()
        pages.keys().asSequence().mapNotNull { pages.optJSONObject(it) }.mapNotNull { page ->
            val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@mapNotNull null
            if (!info.optString("mime").startsWith("image/") || info.optString("mime") == "image/svg+xml") return@mapNotNull null
            val id = page.optInt("pageid").toString()
            val w = info.optInt("width"); val h = info.optInt("height")
            if (id in exclude || !filters.matches(w, h)) return@mapNotNull null
            val title = page.optString("title").removePrefix("File:").substringBeforeLast('.')
            BrainrotWallpaper(
                id = id,
                source = manifest.id.lowercase(),
                thumbUrl = info.optString("thumburl").ifBlank { info.optString("url") },
                sampleUrl = info.optString("thumburl").ifBlank { info.optString("url") },
                fullUrl = info.optString("url").ifBlank { return@mapNotNull null },
                resolution = "${w}x$h",
                pageUrl = info.optString("descriptionurl"),
                tags = captionTags(title),
            )
        }.toList().shuffled().take(limit)
    }
}

/**
 * Unsplash (`/photos/random`). Needs a free access key from unsplash.com/developers; photos link
 * back to their photographer's page as Unsplash asks.
 */
object UnsplashEngine : PluginEngine() {
    override val protocol = Protocol.UNSPLASH

    override suspend fun fetch(
        manifest: PluginManifest, source: LocalSource, query: String, exclude: List<String>,
        nsfw: Boolean, filters: BrainrotFilters,
    ): BrainrotWallpaper? = fetchPage(manifest, source, query, exclude, nsfw, filters, 10).randomOrNull()

    override suspend fun fetchPage(
        manifest: PluginManifest, source: LocalSource, query: String, exclude: List<String>,
        nsfw: Boolean, filters: BrainrotFilters, limit: Int,
    ): List<BrainrotWallpaper> = onIO {
        if (!canServe(manifest, nsfw, source)) return@onIO emptyList()
        val base = baseUrl(manifest, source)
        var url = "$base/photos/random?count=${limit.coerceIn(1, 30)}&content_filter=high"
        if (query.isNotBlank()) url += "&query=${query.replace('_', ' ').urlEncode()}"
        // Phones want tall photos unless the user asked for another shape.
        url += when (filters.aspectRatio) {
            com.chrisalvis.rotato.data.AspectRatio.ANY -> ""
            com.chrisalvis.rotato.data.AspectRatio.PORTRAIT, com.chrisalvis.rotato.data.AspectRatio.MY_PHONE -> "&orientation=portrait"
            else -> "&orientation=landscape"
        }
        val arr = getJsonArray(url, "Authorization" to "Client-ID ${source.apiKey}", "Accept-Version" to "v1")
            ?: return@onIO emptyList()
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapNotNull { p: JSONObject ->
            val id = p.optString("id").ifBlank { return@mapNotNull null }
            val w = p.optInt("width"); val h = p.optInt("height")
            if (id in exclude || !filters.matches(w, h)) return@mapNotNull null
            val urls = p.optJSONObject("urls") ?: return@mapNotNull null
            val caption = p.optString("alt_description").ifBlank { p.optString("description") }
            BrainrotWallpaper(
                id = id,
                source = manifest.id.lowercase(),
                thumbUrl = urls.optString("small"),
                sampleUrl = urls.optString("regular"),
                fullUrl = urls.optString("full").ifBlank { urls.optString("raw") },
                resolution = "${w}x$h",
                pageUrl = p.optJSONObject("links")?.optString("html").orEmpty(),
                tags = captionTags(caption),
            )
        }
    }
}
