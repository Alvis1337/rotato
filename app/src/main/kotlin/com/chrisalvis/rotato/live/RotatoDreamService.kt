package com.chrisalvis.rotato.live

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.service.dreams.DreamService
import android.util.AttributeSet
import android.view.View
import com.chrisalvis.rotato.data.ImageRepository
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.findFocusPoint
import com.chrisalvis.rotato.data.focusedCrop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Rotato as a screen saver (Settings › Display › Screen saver): a slow slideshow of the
 * wallpapers in your rotation with crossfades, a gentle Ken Burns pan and a clock. Images marked
 * NSFW are always left out, since a screen saver is on show to whoever walks past.
 */
class RotatoDreamService : DreamService() {
    private var view: SlideshowView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        view = SlideshowView(this).also { setContentView(it) }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        view?.start()
    }

    override fun onDreamingStopped() {
        view?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        view?.release()
        view = null
        super.onDetachedFromWindow()
    }

    private class Slide(val bitmap: Bitmap, val focus: PointF, val shownAt: Long, val panDir: Int)

    class SlideshowView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
        private val loaderThread = HandlerThread("rotato-dream").apply { start() }
        private val loader = Handler(loaderThread.looper)
        private val main = Handler(Looper.getMainLooper())
        private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(12f, 0f, 2f, 0x99000000.toInt())
        }
        private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xDDFFFFFF.toInt()
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setShadowLayer(8f, 0f, 1f, 0x99000000.toInt())
        }
        private val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
        private val dateFormat = DateFormat.getDateInstance(DateFormat.FULL)

        private var files: List<File> = emptyList()
        private var index = 0
        private var current: Slide? = null
        private var previous: Slide? = null
        private var running = false
        private var slideCount = 0

        private val slideMs = 20_000L
        private val fadeMs = 1_600L

        private val nextSlide = Runnable { loadNext() }

        fun start() {
            running = true
            loader.post {
                val nsfw = runCatching { runBlocking { RotatoPreferences(context).nsfwFileNames.first() } }.getOrDefault(emptySet())
                val pool = ImageRepository(context).getImages()
                    .filter { !LiveWallpaper.isVideoFile(it) && it.name !in nsfw }
                    .shuffled()
                main.post {
                    files = pool
                    index = 0
                    loadNext()
                }
            }
            postInvalidateOnAnimation()
        }

        fun stop() {
            running = false
            main.removeCallbacks(nextSlide)
        }

        fun release() {
            stop()
            loaderThread.quitSafely()
            current?.bitmap?.recycle()
            previous?.bitmap?.recycle()
            current = null
            previous = null
        }

        private fun loadNext() {
            if (!running || files.isEmpty()) return
            val file = files[index % files.size]
            index++
            val metrics = resources.displayMetrics
            val w = if (width > 0) width else metrics.widthPixels
            val h = if (height > 0) height else metrics.heightPixels
            loader.post {
                val bmp = decodeFitting(file, w, h)
                val focus = bmp?.let { runCatching { findFocusPoint(it) }.getOrNull() } ?: PointF(0.5f, 0.5f)
                main.post {
                    if (!running || bmp == null) {
                        bmp?.recycle()
                        if (running) main.postDelayed(nextSlide, 1_000L)
                        return@post
                    }
                    previous?.bitmap?.recycle()
                    previous = current
                    current = Slide(bmp, focus, SystemClock.uptimeMillis(), slideCount++ % 4)
                    invalidate()
                    main.postDelayed(nextSlide, slideMs)
                }
            }
        }

        /** Decodes at roughly screen size (with headroom for the pan) to keep memory down. */
        private fun decodeFitting(file: File, w: Int, h: Int): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= w * 1.3f && bounds.outHeight / (sample * 2) >= h * 1.3f) sample *= 2
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)
            val now = SystemClock.uptimeMillis()
            val cur = current
            val fadeT = cur?.let { ((now - it.shownAt).toFloat() / fadeMs).coerceIn(0f, 1f) } ?: 1f
            previous?.let { prev ->
                if (fadeT < 1f) {
                    imagePaint.alpha = 255
                    drawSlide(canvas, prev, now)
                } else {
                    prev.bitmap.recycle()
                    previous = null
                }
            }
            cur?.let {
                imagePaint.alpha = (255 * fadeT).toInt()
                drawSlide(canvas, it, now)
            }
            drawClock(canvas)
            if (running) postInvalidateOnAnimation()
        }

        /** Ken Burns: each slide slowly zooms and pans in one of four directions. */
        private fun drawSlide(canvas: Canvas, slide: Slide, now: Long) {
            val bmp = slide.bitmap
            if (bmp.isRecycled || width == 0 || height == 0) return
            val crop = RectF(focusedCrop(bmp.width, bmp.height, width.toFloat() / height, slide.focus))
            val t = ((now - slide.shownAt).toFloat() / (slideMs + fadeMs)).coerceIn(0f, 1f)
            val zoomIn = slide.panDir % 2 == 0
            val zoom = if (zoomIn) 1.04f + 0.10f * t else 1.14f - 0.10f * t
            val dx = if (slide.panDir < 2) -1f + 2f * t else 1f - 2f * t
            val srcW = crop.width() / zoom
            val srcH = crop.height() / zoom
            val cx = crop.centerX() + dx * (crop.width() - srcW) / 2f
            val cy = crop.centerY()
            val src = Rect(
                (cx - srcW / 2f).toInt().coerceAtLeast(0),
                (cy - srcH / 2f).toInt().coerceAtLeast(0),
                (cx + srcW / 2f).toInt().coerceAtMost(bmp.width),
                (cy + srcH / 2f).toInt().coerceAtMost(bmp.height),
            )
            canvas.drawBitmap(bmp, src, Rect(0, 0, width, height), imagePaint)
        }

        private fun drawClock(canvas: Canvas) {
            val short = minOf(width, height).toFloat()
            clockPaint.textSize = short * 0.14f
            datePaint.textSize = short * 0.038f
            val now = Date()
            val x = short * 0.07f
            val y = height - short * 0.09f
            canvas.drawText(dateFormat.format(now), x, y, datePaint)
            canvas.drawText(timeFormat.format(now), x - clockPaint.textSize * 0.04f, y - datePaint.textSize * 1.4f, clockPaint)
            if (files.isEmpty() && current == null) {
                canvas.drawText("Add wallpapers in Rotato to fill your screen saver", x, short * 0.15f, datePaint)
            }
        }
    }
}
