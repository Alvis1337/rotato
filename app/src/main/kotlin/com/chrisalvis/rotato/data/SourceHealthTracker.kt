package com.chrisalvis.rotato.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SourceHealth(
    val lastSuccessMs: Long = 0L,
    val lastErrorMs: Long = 0L,
    val lastError: String = "",
) {
    val isHealthy: Boolean get() = lastSuccessMs >= lastErrorMs
    val hasData: Boolean get() = lastSuccessMs > 0L || lastErrorMs > 0L
}

/**
 * In-memory singleton that tracks the fetch health of each plugin source.
 * Keyed by pluginId (e.g. "DANBOORU", "WALLHAVEN").
 * Resets on app restart (intentional — we only care about session health).
 */
object SourceHealthTracker {

    private val _health = MutableStateFlow<Map<String, SourceHealth>>(emptyMap())
    val health: StateFlow<Map<String, SourceHealth>> = _health.asStateFlow()

    fun recordSuccess(pluginId: String) {
        _health.update { map ->
            val prev = map[pluginId] ?: SourceHealth()
            map + (pluginId to prev.copy(lastSuccessMs = System.currentTimeMillis()))
        }
    }

    fun recordError(pluginId: String, message: String) {
        _health.update { map ->
            val prev = map[pluginId] ?: SourceHealth()
            map + (pluginId to prev.copy(lastErrorMs = System.currentTimeMillis(), lastError = message))
        }
    }

    fun get(pluginId: String): SourceHealth = _health.value[pluginId] ?: SourceHealth()
}
