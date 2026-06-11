package com.chrisalvis.rotato.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppErrorLog {

    private const val MAX_LINES = 200
    private lateinit var logFile: File
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    fun init(context: Context) {
        logFile = File(context.filesDir, "rotato_debug.log")
    }

    fun log(tag: String, message: String, throwable: Throwable? = null) {
        if (!::logFile.isInitialized) return
        val timestamp = fmt.format(Date())
        val line = buildString {
            append("[$timestamp] $tag: $message")
            if (throwable != null) append(" | ${throwable.javaClass.simpleName}: ${throwable.message}")
        }
        try {
            val existing = if (logFile.exists()) logFile.readLines() else emptyList()
            val trimmed = (existing + line).takeLast(MAX_LINES)
            logFile.writeText(trimmed.joinToString("\n"))
        } catch (_: Exception) { /* never crash on logging */ }
    }

    fun getLog(): String = try {
        if (::logFile.isInitialized && logFile.exists()) logFile.readText() else "(no log yet)"
    } catch (_: Exception) { "(error reading log)" }

    fun clear() {
        try { if (::logFile.isInitialized) logFile.delete() } catch (_: Exception) {}
    }
}
