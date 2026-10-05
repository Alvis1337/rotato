package com.chrisalvis.rotato.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Interceptor
import okhttp3.MediaType

import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

/**
 * Download progress (0..1) of images Coil is fetching, keyed by the requested URL, so a full
 * screen viewer can show "42%" instead of a frozen preview. Only responses with a known length
 * are tracked; entries are removed when the body is fully read or closed.
 */
object ImageLoadProgress {
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    internal fun set(url: String, fraction: Float) = _progress.update { it + (url to fraction) }
    internal fun clear(url: String) = _progress.update { it - url }

    /** OkHttp application interceptor that wraps image bodies to report read progress. */
    val interceptor = Interceptor { chain ->
        val url = chain.request().url.toString()
        val response = chain.proceed(chain.request())
        val body = response.body
        if (body == null || body.contentLength() <= 0L) response
        else response.newBuilder().body(ProgressBody(url, body)).build()
    }

    private class ProgressBody(private val url: String, private val body: ResponseBody) : ResponseBody() {
        private val source: BufferedSource by lazy {
            object : ForwardingSource(body.source()) {
                private var read = 0L
                private var lastReported = -1f
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val n = super.read(sink, byteCount)
                    val total = body.contentLength()
                    if (n == -1L) {
                        clear(url)
                    } else {
                        read += n
                        val fraction = (read.toFloat() / total).coerceIn(0f, 1f)
                        // Throttle to ~2% steps so a large download doesn't spam recompositions.
                        if (fraction - lastReported >= 0.02f) {
                            lastReported = fraction
                            set(url, fraction)
                        }
                    }
                    return n
                }

                override fun close() {
                    clear(url)
                    super.close()
                }
            }.buffer()
        }

        override fun contentType(): MediaType? = body.contentType()
        override fun contentLength(): Long = body.contentLength()
        override fun source(): BufferedSource = source
    }
}

