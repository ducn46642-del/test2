package com.example.autofish

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility Service dùng để giả lập thao tác chạm (tap) trên màn hình,
 * thay thế cho việc cần quyền root để gửi sự kiện input.
 */
class FishingAccessibilityService : AccessibilityService() {

    companion object {
        var instance: FishingAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Không cần xử lý sự kiện gì, chỉ dùng service này để gửi gesture.
    }

    override fun onInterrupt() {}

    /**
     * Thực hiện một cú chạm tại tọa độ (x, y) trên màn hình.
     */
    fun tap(x: Float, y: Float, durationMs: Long = 50) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }
}
