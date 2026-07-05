package com.example.autofish

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat

/**
 * Service nền: dùng MediaProjection để lấy hình ảnh màn hình theo thời gian thực,
 * theo dõi độ sáng trung bình trong vùng phao câu đã chọn. Khi phát hiện thay đổi
 * đột ngột (nước bắn lên khi cá cắn câu), tự động gửi lệnh chạm để giật cần rồi
 * thả câu lại.
 */
class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    @Volatile var running = false
        private set

    private enum class State { NEED_CAST, GRACE_PERIOD, WAITING_BITE }
    private var state = State.NEED_CAST
    private var stateChangedAt = 0L
    private var baseline = -1.0
    private val sampleHistory = ArrayDeque<Double>()

    private var tapX = 0f
    private var tapY = 0f
    private var regionLeft = 0
    private var regionTop = 0
    private var regionSize = 0
    private var screenDensity = 0

    // Giới hạn số lần thả cần
    private var maxUses = 0
    @Volatile var castCount = 0
        private set
    var onLimitReached: (() -> Unit)? = null
    private val mainHandler = Handler(android.os.Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        handlerThread = HandlerThread("AutoFishLoop").also { it.start() }
        handler = Handler(handlerThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        val prefs = getSharedPreferences(RegionPrefs.NAME, MODE_PRIVATE)
        tapX = prefs.getFloat(RegionPrefs.KEY_TAP_X, 0f)
        tapY = prefs.getFloat(RegionPrefs.KEY_TAP_Y, 0f)
        regionLeft = prefs.getFloat(RegionPrefs.KEY_X, 0f).toInt()
        regionTop = prefs.getFloat(RegionPrefs.KEY_Y, 0f).toInt()
        regionSize = prefs.getFloat(RegionPrefs.KEY_W, 160f).toInt()
        maxUses = prefs.getInt(RegionPrefs.KEY_MAX_USES, 0)

        if (mediaProjection == null) {
            val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
            val data = intent?.getParcelableExtra<Intent>("data")
            if (data != null) {
                val mgr = getSystemService(MediaProjectionManager::class.java)
                mediaProjection = mgr.getMediaProjection(resultCode, data)
                setupVirtualDisplay()
            }
        }

        // Hiện bong bóng điều khiển Start/Stop
        val overlayIntent = Intent(this, OverlayService::class.java)
        overlayIntent.action = OverlayService.ACTION_SHOW_CONTROL
        startService(overlayIntent)

        return START_STICKY
    }

    private fun setupVirtualDisplay() {
        val metrics = DisplayMetrics()
        getSystemService(android.view.WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)
        screenDensity = metrics.densityDpi

        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AutoFishCapture",
            metrics.widthPixels, metrics.heightPixels, screenDensity,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )
    }

    fun toggleRunning(): Boolean {
        running = !running
        if (running) {
            state = State.NEED_CAST
            stateChangedAt = System.currentTimeMillis()
            baseline = -1.0
            sampleHistory.clear()
            castCount = 0
            maxUses = getSharedPreferences(RegionPrefs.NAME, MODE_PRIVATE)
                .getInt(RegionPrefs.KEY_MAX_USES, 0)
            handler.post(loopRunnable)
        }
        return running
    }

    private val loopRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                tick()
            } catch (e: Exception) {
                // Bỏ qua lỗi đọc frame lẻ tẻ, thử lại ở vòng sau
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val svc = FishingAccessibilityService.instance

        when (state) {
            State.NEED_CAST -> {
                if (maxUses > 0 && castCount >= maxUses) {
                    stopDueToLimit()
                    return
                }
                svc?.tap(tapX, tapY)
                castCount++
                updateNotification()
                state = State.GRACE_PERIOD
                stateChangedAt = now
            }
            State.GRACE_PERIOD -> {
                // Chờ phao ổn định trên mặt nước sau khi thả câu trước khi bắt đầu theo dõi
                if (now - stateChangedAt > GRACE_MS) {
                    state = State.WAITING_BITE
                    stateChangedAt = now
                    baseline = -1.0
                    sampleHistory.clear()
                }
            }
            State.WAITING_BITE -> {
                val sample = sampleRegionBrightness()
                if (sample != null) {
                    if (baseline < 0) {
                        baseline = sample
                    } else {
                        sampleHistory.addLast(sample)
                        if (sampleHistory.size > HISTORY_SIZE) sampleHistory.removeFirst()

                        val diff = kotlin.math.abs(sample - baseline)
                        if (diff > SPLASH_THRESHOLD) {
                            // Phát hiện tõm nước -> giật cần
                            svc?.tap(tapX, tapY)
                            state = State.NEED_CAST
                            stateChangedAt = now
                            // Chờ một chút trước khi thả câu lại (mô phỏng animation giật cần)
                            handler.postDelayed({ /* no-op, NEED_CAST xử lý ở tick tiếp theo */ }, RECAST_DELAY_MS)
                        } else {
                            // Cập nhật baseline từ từ để thích ứng ánh sáng/thời tiết thay đổi chậm
                            baseline = baseline * 0.9 + sample * 0.1
                        }
                    }
                }
                // An toàn: nếu chờ quá lâu không cắn câu, thả lại
                if (now - stateChangedAt > MAX_WAIT_MS) {
                    svc?.tap(tapX, tapY)
                    state = State.NEED_CAST
                    stateChangedAt = now
                }
            }
        }
    }

    private fun stopDueToLimit() {
        running = false
        mainHandler.post {
            android.widget.Toast.makeText(
                this,
                "Đã đạt giới hạn $maxUses lần thả cần, đã tự dừng.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
        mainHandler.post { onLimitReached?.invoke() }
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    /**
     * Đọc frame mới nhất từ ImageReader và tính độ sáng trung bình trong vùng đã chọn.
     */
    private fun sampleRegionBrightness(): Double? {
        val image = imageReader?.acquireLatestImage() ?: return null
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val width = image.width
            val height = image.height

            val left = regionLeft.coerceIn(0, width - 1)
            val top = regionTop.coerceIn(0, height - 1)
            val right = (regionLeft + regionSize).coerceIn(left + 1, width)
            val bottom = (regionTop + regionSize).coerceIn(top + 1, height)

            var sum = 0L
            var count = 0L
            val stepX = ((right - left) / 20).coerceAtLeast(1)
            val stepY = ((bottom - top) / 20).coerceAtLeast(1)

            var y = top
            while (y < bottom) {
                var x = left
                val rowStart = y * rowStride
                while (x < right) {
                    val idx = rowStart + x * pixelStride
                    if (idx + 2 < buffer.capacity()) {
                        val r = buffer.get(idx).toInt() and 0xFF
                        val g = buffer.get(idx + 1).toInt() and 0xFF
                        val b = buffer.get(idx + 2).toInt() and 0xFF
                        sum += (r + g + b)
                        count++
                    }
                    x += stepX
                }
                y += stepY
            }
            return if (count > 0) sum.toDouble() / count else null
        } finally {
            image.close()
        }
    }

    private fun buildNotification(): android.app.Notification {
        val channelId = "autofish_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Auto Fish", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val countText = if (maxUses > 0) "Đã thả cần: $castCount/$maxUses" else "Đã thả cần: $castCount (không giới hạn)"
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(if (running) "Auto Fish đang chạy" else "Auto Fish đã dừng")
            .setContentText(countText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        instance = null
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        handlerThread.quitSafely()
        val overlayIntent = Intent(this, OverlayService::class.java)
        overlayIntent.action = OverlayService.ACTION_HIDE
        startService(overlayIntent)
    }

    companion object {
        var instance: ScreenCaptureService? = null
        private const val NOTIF_ID = 42
        private const val TICK_MS = 150L
        private const val GRACE_MS = 1500L
        private const val MAX_WAIT_MS = 40000L
        private const val RECAST_DELAY_MS = 600L
        private const val HISTORY_SIZE = 10
        // Ngưỡng chênh lệch độ sáng để coi là "tõm nước". Tăng số này nếu bị báo giật nhầm,
        // giảm nếu không bắt được splash.
        private const val SPLASH_THRESHOLD = 22.0
    }
}
