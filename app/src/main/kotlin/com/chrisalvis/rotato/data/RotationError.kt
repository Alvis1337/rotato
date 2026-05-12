package com.chrisalvis.rotato.data

import org.json.JSONArray
import org.json.JSONObject

enum class RotationErrorType {
    POOL_EMPTY,       // rotation worker ran but no images in pool
    IMAGE_MISSING,    // selected file was deleted before worker could use it
    IMAGE_CORRUPT,    // file exists but couldn't be decoded as a bitmap
    SET_FAILED,       // WallpaperManager.setBitmap threw
    DOWNLOAD_FAILED,  // a collection image failed to download into the pool
}

data class RotationError(
    val type: RotationErrorType,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

fun List<RotationError>.toErrorJson(): String = JSONArray().also { arr ->
    forEach { e ->
        arr.put(JSONObject().apply {
            put("type", e.type.name)
            put("message", e.message)
            put("timestamp", e.timestamp)
        })
    }
}.toString()

fun rotationErrorsFromJson(json: String): List<RotationError> = try {
    val arr = JSONArray(json)
    (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val type = runCatching { RotationErrorType.valueOf(o.getString("type")) }.getOrNull()
            ?: return@mapNotNull null
        RotationError(
            type = type,
            message = o.optString("message", ""),
            timestamp = o.optLong("timestamp", System.currentTimeMillis())
        )
    }
} catch (_: Exception) { emptyList() }
