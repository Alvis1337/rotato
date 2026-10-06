package com.chrisalvis.rotato.live

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.view.animation.DecelerateInterpolator
import com.chrisalvis.rotato.data.findFocusPoint
import com.chrisalvis.rotato.data.focusedCrop
import com.chrisalvis.rotato.worker.AutomationReceiver
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Draws whatever [LiveWallpaper] last published: crossfades between images, re-crops on every
 * surface change (so folding/unfolding picks the right framing, and an unfold "reveals" the
 * wider picture), and plays videos. Optional extras, all off the main path: a slow drift that
 * makes stills feel alive, tilt parallax, and double-tap on the home screen for the next wallpaper.
 */
class RotatoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = RotatoEngine()

    private class Frame(val bitmap: Bitmap, val focus: PointF, val crops: Map<Point, Rect>)

    inner class RotatoEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener, SensorEventListener {
        private val main = Handler(Looper.getMainLooper())
        private val loaderThread = HandlerThread("rotato-live-loader").apply { start() }
        private val loader = Handler(loaderThread.looper)
        private val prefs = getSharedPreferences(LiveWallpaper.PREFS, Context.MODE_PRIVATE)
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val interpolator = DecelerateInterpolator(1.6f)

        private var current: Frame? = null
        private var previous: Frame? = null
        private var fadeStart = 0L
        private var fadeMs = 900L
        private var width = 0
        private var height = 0
        private var visible = false
        private var player: MediaPlayer? = null
        private var loadToken = 0

        // Unfold reveal: when the surface gets wider, start zoomed in and settle out.
        private var revealStart = 0L
        private val revealMs = 750L

        // Living stills (slow drift) and tilt parallax.
        private var drift = false
        private var parallax = false
        private var doubleTap = true
        private var tiltX = 0f
        private var tiltY = 0f
        private var baseRoll: Float? = null
        private var basePitch: Float? = null
        private val sensors by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }

        private var lastTapAt = 0L
        private var lastTapX = 0f
        private var lastTapY = 0f

        private val frameCallback = Runnable { drawFrame() }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            readOptions()
            prefs.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            main.removeCallbacks(frameCallback)
            stopSensors()
            releasePlayer()
            loaderThread.quitSafely()
            current?.bitmap?.recycle()
            previous?.bitmap?.recycle()
            current = null
            previous = null
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            val wasNarrow = width > 0 && width.toFloat() / height < 0.75f
            val nowWide = w.toFloat() / h >= 0.75f
            if (wasNarrow && nowWide) revealStart = SystemClock.uptimeMillis()
            width = w
            height = h
            if (current == null && player == null) load(fade = false) else scheduleFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            main.removeCallbacks(frameCallback)
            releasePlayer()
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                player?.start()
                if (parallax) startSensors()
                scheduleFrame()
            } else {
                player?.pause()
                stopSensors()
                main.removeCallbacks(frameCallback)
            }
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
            when (key) {
                LiveWallpaper.KEY_STAMP -> load(fade = true)
                else -> {
                    readOptions()
                    if (visible && parallax) startSensors() else stopSensors()
                    scheduleFrame()
                }
            }
        }

        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (action == WallpaperManager.COMMAND_TAP && doubleTap) {
                val now = SystemClock.uptimeMillis()
                val close = hypot(x - lastTapX, y - lastTapY) < 120f
                if (now - lastTapAt < 380 && close) {
                    lastTapAt = 0L
                    sendBroadcast(Intent(AutomationReceiver.ACTION_NEXT).setClass(this@RotatoWallpaperService, AutomationReceiver::class.java))
                } else {
                    lastTapAt = now
                    lastTapX = x.toFloat()
                    lastTapY = y.toFloat()
                }
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        private fun readOptions() {
            fadeMs = prefs.getLong(LiveWallpaper.KEY_FADE_MS, 900L)
            drift = prefs.getBoolean(LiveWallpaper.KEY_DRIFT, false)
            parallax = prefs.getBoolean(LiveWallpaper.KEY_PARALLAX, false)
            doubleTap = prefs.getBoolean(LiveWallpaper.KEY_DOUBLE_TAP, true)
        }

        /** Loads the published image or video; images decode off the main thread. */
        private fun load(fade: Boolean) {
            val token = ++loadToken
            val path = prefs.getString(LiveWallpaper.KEY_PATH, null) ?: fallbackPath()
            if (path == null) {
                scheduleFrame()
                return
            }
            if (prefs.getBoolean(LiveWallpaper.KEY_VIDEO, false)) {
                playVideo(path)
                return
            }
            val crops = LiveWallpaper.parseCrops(prefs.getString(LiveWallpaper.KEY_CROPS, null))
            loader.post {
                val bmp = BitmapFactory.decodeFile(path) ?: return@post
                val focus = runCatching { findFocusPoint(bmp) }.getOrDefault(PointF(0.5f, 0.5f))
                main.post {
                    if (token != loadToken) {
                        bmp.recycle()
                        return@post
                    }
                    releasePlayer()
                    previous?.bitmap?.recycle()
                    previous = if (fade) current else current?.let { it.bitmap.recycle(); null }
                    current = Frame(bmp, focus, crops)
                    fadeStart = SystemClock.uptimeMillis()
                    scheduleFrame()
                }
            }
        }

        /** Before anything is published, show the newest image in the rotation pool. */
        private fun fallbackPath(): String? =
            File(filesDir, "rotato_images").listFiles()?.filter { !LiveWallpaper.isVideoFile(it) }
                ?.maxByOrNull { it.lastModified() }?.absolutePath

        private fun playVideo(path: String) {
            main.removeCallbacks(frameCallback)
            releasePlayer()
            current?.bitmap?.recycle()
            previous?.bitmap?.recycle()
            current = null
            previous = null
            try {
                player = MediaPlayer().apply {
                    setSurface(surfaceHolder.surface)
                    setDataSource(path)
                    isLooping = true
                    setVolume(0f, 0f)
                    setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    setOnPreparedListener { if (visible) it.start() }
                    prepareAsync()
                }
            } catch (e: Exception) {
                releasePlayer()
            }
        }

        private fun releasePlayer() {
            player?.let { p ->
                runCatching { p.stop() }
                p.release()
            }
            player = null
        }

        private fun scheduleFrame() {
            main.removeCallbacks(frameCallback)
            if (visible || current != null) main.post(frameCallback)
        }

        private fun drawFrame() {
            if (player != null || width == 0) return
            val holder = surfaceHolder
            val canvas: Canvas = try {
                holder.lockHardwareCanvas()
            } catch (e: Exception) {
                runCatching { holder.lockCanvas() }.getOrNull()
            } ?: return
            val now = SystemClock.uptimeMillis()
            var animating = false
            try {
                canvas.drawColor(Color.BLACK)
                val cur = current
                val fadeT = if (fadeMs <= 0) 1f else ((now - fadeStart).toFloat() / fadeMs).coerceIn(0f, 1f)
                val revealT = ((now - revealStart).toFloat() / revealMs).coerceIn(0f, 1f)
                val reveal = 1f + 0.18f * (1f - interpolator.getInterpolation(revealT))
                val prev = previous
                if (prev != null && fadeT < 1f) {
                    paint.alpha = 255
                    drawImage(canvas, prev, now, reveal)
                }
                if (cur != null) {
                    paint.alpha = (255 * interpolator.getInterpolation(fadeT)).toInt()
                    drawImage(canvas, cur, now, reveal)
                }
                if (fadeT >= 1f && prev != null) {
                    prev.bitmap.recycle()
                    previous = null
                }
                animating = fadeT < 1f || revealT < 1f || (visible && (drift || parallax))
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
            if (animating && visible) main.postDelayed(frameCallback, if (drift && !parallax && fadeMs <= 0) 50L else 16L)
        }

        private fun drawImage(canvas: Canvas, frame: Frame, now: Long, reveal: Float) {
            val bmp = frame.bitmap
            if (bmp.isRecycled) return
            val aspect = width.toFloat() / height
            val crop = frame.crops[Point(width, height)]
                ?: frame.crops.entries.minByOrNull { (p, _) -> abs(p.x.toFloat() / p.y - aspect) }
                    ?.takeIf { (p, _) -> abs(p.x.toFloat() / p.y - aspect) < 0.08f }?.value
                ?: focusedCrop(bmp.width, bmp.height, aspect, frame.focus)
            val cropRect = RectF(crop)
            if (cropRect.width().toFloat() / cropRect.height() > aspect + 0.01f) {
                val w = cropRect.height() * aspect
                val cx = cropRect.centerX()
                cropRect.left = cx - w / 2f; cropRect.right = cx + w / 2f
            }

            // Zoom in a touch for drift/parallax headroom (and for the unfold reveal).
            var zoom = reveal
            var panX = 0f
            var panY = 0f
            if (drift) {
                val t = now / 1000.0
                zoom *= 1.06f + 0.025f * sin(t * 2 * Math.PI / 47).toFloat()
                panX += 0.5f * sin(t * 2 * Math.PI / 61).toFloat()
                panY += 0.4f * sin(t * 2 * Math.PI / 53 + 1.3).toFloat()
            }
            if (parallax) {
                zoom *= 1.05f
                panX += tiltX
                panY += tiltY
            }
            val srcW = cropRect.width() / zoom
            val srcH = cropRect.height() / zoom
            val slackX = (cropRect.width() - srcW) / 2f
            val slackY = (cropRect.height() - srcH) / 2f
            val cx = cropRect.centerX() + panX.coerceIn(-1f, 1f) * slackX
            val cy = cropRect.centerY() + panY.coerceIn(-1f, 1f) * slackY
            val src = Rect(
                (cx - srcW / 2f).toInt().coerceAtLeast(0),
                (cy - srcH / 2f).toInt().coerceAtLeast(0),
                (cx + srcW / 2f).toInt().coerceAtMost(bmp.width),
                (cy + srcH / 2f).toInt().coerceAtMost(bmp.height),
            )
            canvas.drawBitmap(bmp, src, Rect(0, 0, width, height), paint)
        }

        private fun startSensors() {
            val sensor = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return
            baseRoll = null
            basePitch = null
            sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }

        private fun stopSensors() {
            runCatching { sensors.unregisterListener(this) }
            tiltX = 0f
            tiltY = 0f
        }

        private val rotation = FloatArray(9)
        private val orientation = FloatArray(3)

        override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotation, event.values)
            SensorManager.getOrientation(rotation, orientation)
            val pitch = orientation[1]
            val roll = orientation[2]
            // Tilt is measured from how the phone was held when the screen came on, and the
            // reference slowly follows so a new resting angle becomes the new centre.
            val bp = basePitch ?: pitch.also { basePitch = it }
            val br = baseRoll ?: roll.also { baseRoll = it }
            basePitch = bp + (pitch - bp) * 0.01f
            baseRoll = br + (roll - br) * 0.01f
            val targetX = ((roll - br) / 0.35f).coerceIn(-1f, 1f)
            val targetY = ((pitch - bp) / 0.35f).coerceIn(-1f, 1f)
            tiltX += (targetX - tiltX) * 0.2f
            tiltY += (targetY - tiltY) * 0.2f
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
}
