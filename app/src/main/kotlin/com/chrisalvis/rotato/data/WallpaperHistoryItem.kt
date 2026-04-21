package com.chrisalvis.rotato.data

import org.json.JSONArray
import org.json.JSONObject

data class WallpaperHistoryItem(
    val thumbUrl: String,
    val fullUrl: String,
    val source: String,
    val timestamp: Long,
    val tags: List<String> = emptyList(),
    val pageUrl: String = ""
)

fun List<WallpaperHistoryItem>.toJson(): String = JSONArray().also { arr ->
    forEach { item ->
        arr.put(JSONObject().apply {
            put("thumbUrl", item.thumbUrl)
            put("fullUrl", item.fullUrl)
            put("source", item.source)
            put("timestamp", item.timestamp)
            put("tags", JSONArray().also { ta -> item.tags.forEach { ta.put(it) } })
            put("pageUrl", item.pageUrl)
        })
    }
}.toString()

fun historyFromJson(json: String): List<WallpaperHistoryItem> = try {
    val arr = JSONArray(json)
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val tagsArr = o.optJSONArray("tags")
        val tags = if (tagsArr != null) (0 until tagsArr.length()).map { tagsArr.getString(it) } else emptyList()
        WallpaperHistoryItem(
            thumbUrl = o.optString("thumbUrl"),
            fullUrl = o.optString("fullUrl"),
            source = o.optString("source"),
            timestamp = o.optLong("timestamp"),
            tags = tags,
            pageUrl = o.optString("pageUrl", "")
        )
    }
} catch (_: Exception) { emptyList() }
