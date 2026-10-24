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
import com.chrisalvis.rotato.data.loadScaledBitmap
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.worker.AutomationReceiver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalTime
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
        /** The published wallpaper is a video; it plays whenever this screen has a live surface. */
        private var videoPath: String? = null
        private var surfaceReady = false
        /**
         * A video decoder has drawn into this surface. Android won't let a canvas draw into it
         * afterwards, so a still shown next froze on the video's last frame until the surface
         * is swapped for a fresh one.
         */
        private var surfaceHeldVideo = false
        /**
         * The reverse: a canvas has drawn into this surface (an image was showing). A video
         * decoder can't attach to it afterwards, so the player failed and the screen stayed
         * black; the surface is swapped for a fresh one before the video starts.
         */
        private var surfaceHeldCanvas = false
        // Wallpaper surfaces start opaque, so the first swap goes to RGBA.
        private var opaqueFormat = true
        private var loadToken = 0

        // Unfold reveal: when the surface gets wider, start zoomed in and settle out.
        private var revealStart = 0L
        private val revealMs = 750L

        // Living stills (slow drift) and tilt parallax.
        private var drift = false
        private var parallax = false
        private var doubleTap = true
        private var followSun = false
        private var batteryAware = true
        /** Battery Saver is on, or the battery is low and unplugged: hold still to save power. */
        private var saving = false
        private val powerReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = updatePower(intent.takeIf { it.action == Intent.ACTION_BATTERY_CHANGED })
        }
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
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            val sticky = androidx.core.content.ContextCompat.registerReceiver(
                this@RotatoWallpaperService, powerReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            updatePower(sticky)
        }

        private var batteryLow = false

        private fun updatePower(battery: Intent?) {
            if (battery != null) {
                val level = battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val plugged = battery.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
                batteryLow = !plugged && level >= 0 && level * 100 / scale <= LiveWallpaper.LOW_BATTERY_PERCENT
            }
            val powerSave = getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true
            val now = batteryAware && (powerSave || batteryLow)
            if (now == saving) return
            saving = now
            if (saving) player?.pause() else if (visible) player?.start()
            scheduleFrame()
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            runCatching { unregisterReceiver(powerReceiver) }
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

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceHeldVideo = false
            surfaceHeldCanvas = false
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            val wasNarrow = width > 0 && width.toFloat() / height < 0.75f
            val nowWide = w.toFloat() / h >= 0.75f
            if (wasNarrow && nowWide) revealStart = SystemClock.uptimeMillis()
            width = w
            height = h
            surfaceReady = true
            val video = videoPath
            when {
                // Each screen of a foldable has its own surface, and the inner one is often torn
                // down and rebuilt while folded. A player bound to an old surface draws nothing,
                // so a video starts (or restarts) on whichever surface is live now.
                video != null && player == null -> playVideo(video)
                current == null && player == null -> load(fade = false)
                else -> scheduleFrame()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            main.removeCallbacks(frameCallback)
            releasePlayer()
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                val video = videoPath
                // A new player starts itself once prepared; starting it earlier is an error.
                if (video != null && player == null && surfaceReady) playVideo(video) else if (!saving) player?.start()
                if (parallax) startSensors()
                scheduleFrame()
            } else {
                // A hidden screen's video gives its decoder back so the other screen can use it;
                // it starts again when this screen shows.
                if (videoPath != null) releasePlayer() else player?.pause()
                stopSensors()
                main.removeCallbacks(frameCallback)
            }
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
            when (key) {
                LiveWallpaper.KEY_STAMP -> load(fade = true)
                else -> {
                    readOptions()
                    updatePower(null)
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
            followSun = prefs.getBoolean(LiveWallpaper.KEY_SUN, false)
            batteryAware = prefs.getBoolean(LiveWallpaper.KEY_BATTERY, true)
        }

        /** Loads the published image or video; images decode off the main thread. */
        private fun load(fade: Boolean) {
            val token = ++loadToken
            val published = prefs.getString(LiveWallpaper.KEY_PATH, null)
            if (published != null && prefs.getBoolean(LiveWallpaper.KEY_VIDEO, false)) {
                // A new video gets its own retry.
                if (videoPath != published) videoRetries = 0
                videoPath = published
                playVideo(published)
                return
            }
            videoPath = null
            val crops = LiveWallpaper.parseCrops(prefs.getString(LiveWallpaper.KEY_CROPS, null))
            loader.post {
                // The published image is already fitted to the screen (its crops are in its
                // pixels); the fallback is a raw pool file, so it's sampled down and EXIF-rotated.
                val bmp = if (published != null) {
                    runCatching { BitmapFactory.decodeFile(published) }.getOrNull()
                } else {
                    fallbackPath()?.let { path -> runCatching { loadScaledBitmap(this@RotatoWallpaperService, path) }.getOrNull() }
                }
                if (bmp == null) {
                    main.post { if (token == loadToken) scheduleFrame() }
                    return@post
                }
                val focus = runCatching { findFocusPoint(bmp) }.getOrDefault(PointF(0.5f, 0.5f))
                main.post {
                    if (token != loadToken) {
                        bmp.recycle()
                        return@post
                    }
                    releasePlayer()
                    freshSurfaceAfterVideo()
                    previous?.bitmap?.recycle()
                    previous = if (fade) current else current?.let { it.bitmap.recycle(); null }
                    current = Frame(bmp, focus, crops)
                    fadeStart = SystemClock.uptimeMillis()
                    scheduleFrame()
                }
            }
        }

        /**
         * Before anything is published, show the newest image in the rotation pool, leaving out
         * NSFW ones while the content filter is on. Runs on the loader thread (it reads DataStore).
         */
        private fun fallbackPath(): String? {
            val hidden = runCatching {
                runBlocking {
                    val rotatoPrefs = RotatoPreferences(this@RotatoWallpaperService)
                    if (rotatoPrefs.nsfwHidden.first()) rotatoPrefs.nsfwFileNames.first() else emptySet()
                }
            }.getOrNull() ?: return null
            return File(filesDir, "rotato_images").listFiles()
                ?.filter { !LiveWallpaper.isVideoFile(it) && it.name !in hidden }
                ?.maxByOrNull { it.lastModified() }?.absolutePath
        }

        private fun playVideo(path: String) {
            main.removeCallbacks(frameCallback)
            releasePlayer()
            current?.bitmap?.recycle()
            previous?.bitmap?.recycle()
            current = null
            previous = null
            // With no live surface (this screen is off), wait for onSurfaceChanged to start it.
            if (!surfaceReady || !surfaceHolder.surface.isValid) return
            if (surfaceHeldCanvas) {
                // Swap for a fresh surface; onSurfaceChanged starts the video on it.
                surfaceHeldCanvas = false
                flipSurfaceFormat()
                // In case the swap brings no surface callback, start it shortly anyway.
                main.postDelayed({ if (videoPath == path && player == null && surfaceReady) playVideo(path) }, 350)
                return
            }
            surfaceHeldVideo = true
            // Kept local until it's set up: a bad file throws midway, and that player must be
            // released here since it never became [player].
            val mp = MediaPlayer()
            try {
                mp.setSurface(surfaceHolder.surface)
                mp.setDataSource(path)
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                // While saving power, show the first frame and hold it.
                mp.setOnPreparedListener { if (visible && !saving) it.start() else it.seekTo(0) }
                // A decoder error (e.g. both screens' players competing for it) leaves a dead
                // player; drop it so the next time this screen shows, it starts again. Only if
                // it's still the current one: a newer player must not be released in its place.
                mp.setOnErrorListener { _, _, _ ->
                    main.post {
                        if (player === mp) {
                            releasePlayer()
                            // A surface that still couldn't take the decoder: swap it once more and retry.
                            if (videoPath == path && videoRetries++ < 1) flipSurfaceFormat()
                        }
                    }
                    true
                }
                mp.setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) videoRetries = 0
                    false
                }
                mp.prepareAsync()
                player = mp
            } catch (e: Exception) {
                runCatching { mp.release() }
            }
        }

        /**
         * Swaps the surface for a new one after a video, by flipping its pixel format (the
         * look is the same either way); onSurfaceCreated/onSurfaceChanged then draw the still.
         */
        private fun freshSurfaceAfterVideo() {
            if (!surfaceHeldVideo) return
            surfaceHeldVideo = false
            flipSurfaceFormat()
        }

        private var videoRetries = 0

        private fun flipSurfaceFormat() {
            opaqueFormat = !opaqueFormat
            runCatching {
                surfaceHolder.setFormat(if (opaqueFormat) android.graphics.PixelFormat.RGBX_8888 else android.graphics.PixelFormat.RGBA_8888)
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
            // A video owns this surface (or is about to); drawing on it would block the decoder.
            if (player != null || videoPath != null || width == 0) return
            val holder = surfaceHolder
            val canvas: Canvas = try {
                holder.lockHardwareCanvas()
            } catch (e: Exception) {
                runCatching { holder.lockCanvas() }.getOrNull()
            } ?: return
            surfaceHeldCanvas = true
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
                if (followSun && cur != null) {
                    val time = LocalTime.now()
                    val tint = LiveWallpaper.sunTint(time.hour + time.minute / 60f)
                    if (Color.alpha(tint) > 0) canvas.drawColor(tint)
                }
                if (fadeT >= 1f && prev != null) {
                    prev.bitmap.recycle()
                    previous = null
                }
                animating = fadeT < 1f || revealT < 1f || (visible && !saving && (drift || parallax))
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
            // The sun moves slowly: with nothing animating, repaint once a minute so the tint keeps up.
            when {
                animating && visible -> main.postDelayed(frameCallback, if (drift && !parallax && fadeMs <= 0) 50L else 16L)
                followSun && visible -> main.postDelayed(frameCallback, 60_000L)
            }
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
