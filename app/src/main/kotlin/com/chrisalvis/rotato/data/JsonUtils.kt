package com.chrisalvis.rotato.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Maps every object in the array, skipping entries that fail to parse. One malformed element
 * (e.g. a missing required field from an older version) must not make the whole list read as
 * empty, because the next write would then persist that empty list and wipe the user's data.
 */
internal inline fun <T> JSONArray.mapObjectsSafely(transform: (JSONObject) -> T): List<T> =
    (0 until length()).mapNotNull { i -> runCatching { transform(getJSONObject(i)) }.getOrNull() }
