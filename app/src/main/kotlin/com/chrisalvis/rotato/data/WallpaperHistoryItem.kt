package com.chrisalvis.rotato.data

import org.json.JSONArray
import org.json.JSONObject

data class WallpaperHistoryItem(
    val thumbUrl: String,
    val fullUrl: String,
    val source: String,
    val timestamp: Long
)

fun List<WallpaperHistoryItem>.toJson(): String = JSONArray().also { arr ->
    forEach { item ->
        arr.put(JSONObject().apply {
            put("thumbUrl", item.thumbUrl)
            put("fullUrl", item.fullUrl)
            put("source", item.source)
            put("timestamp", item.timestamp)
        })
    }
}.toString()

fun historyFromJson(json: String): List<WallpaperHistoryItem> = try {
    val arr = JSONArray(json)
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        WallpaperHistoryItem(
            thumbUrl = o.optString("thumbUrl"),
            fullUrl = o.optString("fullUrl"),
            source = o.optString("source"),
            timestamp = o.optLong("timestamp")
        )
    }
} catch (_: Exception) { emptyList() }
