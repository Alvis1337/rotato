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
    /** True when the last attempt (if any) was successful. */
    val isHealthy: Boolean get() = lastSuccessMs >= lastErrorMs
    /** True when we have seen at least one fetch attempt. */
    val hasData: Boolean get() = lastSuccessMs > 0L || lastErrorMs > 0L
}

/**
 * In-memory singleton that tracks the fetch health of each [SourceType].
 * Resets on app restart (intentional — we only care about session health).
 */
object SourceHealthTracker {

    private val _health = MutableStateFlow<Map<SourceType, SourceHealth>>(emptyMap())
    val health: StateFlow<Map<SourceType, SourceHealth>> = _health.asStateFlow()

    fun recordSuccess(type: SourceType) {
        _health.update { map ->
            val prev = map[type] ?: SourceHealth()
            map + (type to prev.copy(lastSuccessMs = System.currentTimeMillis()))
        }
    }

    fun recordError(type: SourceType, message: String) {
        _health.update { map ->
            val prev = map[type] ?: SourceHealth()
            map + (type to prev.copy(lastErrorMs = System.currentTimeMillis(), lastError = message))
        }
    }

    fun get(type: SourceType): SourceHealth = _health.value[type] ?: SourceHealth()
}
