package com.example.autofish

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var regionView: RegionSelectView? = null
    private var bubbleView: View? = null
    private var borderView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SELECT_REGION -> showRegionSelector()
            ACTION_SHOW_CONTROL -> showControlBubble()
            ACTION_HIDE -> hideAll()
        }
        return START_NOT_STICKY
    }

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun showRegionSelector() {
        if (regionView != null) return
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val prefs = getSharedPreferences(RegionPrefs.NAME, MODE_PRIVATE)

        val view = RegionSelectView(this, metrics.widthPixels, metrics.heightPixels)
        if (prefs.contains(RegionPrefs.KEY_X)) {
            view.boxLeft = prefs.getFloat(RegionPrefs.KEY_X, view.boxLeft)
            view.boxTop = prefs.getFloat(RegionPrefs.KEY_Y, view.boxTop)
            view.boxSize = prefs.getFloat(RegionPrefs.KEY_W, view.boxSize)
            view.tapX = prefs.getFloat(RegionPrefs.KEY_TAP_X, view.tapX)
            view.tapY = prefs.getFloat(RegionPrefs.KEY_TAP_Y, view.tapY)
        }
        view.onSave = {
            prefs.edit()
                .putFloat(RegionPrefs.KEY_X, view.boxLeft)
                .putFloat(RegionPrefs.KEY_Y, view.boxTop)
                .putFloat(RegionPrefs.KEY_W, view.boxSize)
                .putFloat(RegionPrefs.KEY_H, view.boxSize)
                .putFloat(RegionPrefs.KEY_TAP_X, view.tapX)
                .putFloat(RegionPrefs.KEY_TAP_Y, view.tapY)
                .apply()
            windowManager.removeView(regionView)
            regionView = null
            stopSelf()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(view, params)
        regionView = view
    }

    private fun showControlBubble() {
        if (bubbleView != null) return
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val prefs = getSharedPreferences(RegionPrefs.NAME, MODE_PRIVATE)

        // Khung viền chỉ để tham khảo vị trí vùng phao câu (không chặn thao tác chạm game)
        val border = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        val boxW = prefs.getFloat(RegionPrefs.KEY_W, 160f).toInt()
        val borderParams = WindowManager.LayoutParams(
            boxW, boxW,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        borderParams.gravity = Gravity.TOP or Gravity.START
        borderParams.x = prefs.getFloat(RegionPrefs.KEY_X, 0f).toInt()
        borderParams.y = prefs.getFloat(RegionPrefs.KEY_Y, 0f).toInt()
        border.setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
        windowManager.addView(border, borderParams)
        borderView = border

        // Bong bóng nổi Start/Stop, có thể kéo đi được
        val bubble = TextView(this).apply {
            text = "▶"
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4CAF50"))
            gravity = android.view.Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }
        val bubbleParams = WindowManager.LayoutParams(
            140, 140,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        bubbleParams.gravity = Gravity.TOP or Gravity.START
        bubbleParams.x = 20
        bubbleParams.y = metrics.heightPixels / 3

        var downX = 0f; var downY = 0f
        var startX = 0; var startY = 0
        var moved = false

        bubble.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = bubbleParams.x; startY = bubbleParams.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12) moved = true
                    bubbleParams.x = startX + dx
                    bubbleParams.y = startY + dy
                    windowManager.updateViewLayout(bubble, bubbleParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        val running = ScreenCaptureService.instance?.toggleRunning() ?: false
                        bubble.text = if (running) "⏸" else "▶"
                        bubble.setBackgroundColor(if (running) Color.parseColor("#F44336") else Color.parseColor("#4CAF50"))
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubble, bubbleParams)
        bubbleView = bubble

        // Khi ScreenCaptureService tự dừng vì đạt giới hạn số lần thả cần,
        // cập nhật lại icon bong bóng về trạng thái "đã dừng".
        ScreenCaptureService.instance?.onLimitReached = {
            bubble.text = "▶"
            bubble.setBackgroundColor(Color.parseColor("#4CAF50"))
        }
    }

    private fun hideAll() {
        regionView?.let { windowManager.removeView(it) }
        bubbleView?.let { windowManager.removeView(it) }
        borderView?.let { windowManager.removeView(it) }
        regionView = null; bubbleView = null; borderView = null
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideAll()
    }

    companion object {
        const val ACTION_SELECT_REGION = "select_region"
        const val ACTION_SHOW_CONTROL = "show_control"
        const val ACTION_HIDE = "hide"
    }
}
