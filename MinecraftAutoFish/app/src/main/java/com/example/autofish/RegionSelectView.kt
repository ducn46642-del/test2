package com.example.autofish

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * View hiển thị đè lên toàn màn hình để người dùng:
 * 1) Kéo/resize ô vàng vào đúng vị trí phao câu sẽ rơi xuống nước.
 * 2) Kéo chấm xanh vào đúng vị trí nút "dùng vật phẩm" (nút thả/giật cần trong Minecraft PE).
 * 3) Bấm "LƯU VỊ TRÍ" để lưu lại.
 */
class RegionSelectView(context: Context, private val screenW: Int, private val screenH: Int) : View(context) {

    var boxLeft = screenW / 2f - 80f
    var boxTop = screenH / 2f - 80f
    var boxSize = 160f

    var tapX = screenW - 220f
    var tapY = screenH - 220f

    private val boxStroke = Paint().apply {
        color = Color.parseColor("#FFEB3B"); style = Paint.Style.STROKE; strokeWidth = 6f
    }
    private val boxFill = Paint().apply {
        color = Color.parseColor("#33FFEB3B"); style = Paint.Style.FILL
    }
    private val handlePaint = Paint().apply {
        color = Color.parseColor("#FFEB3B"); style = Paint.Style.FILL
    }
    private val tapPaint = Paint().apply {
        color = Color.parseColor("#2196F3"); style = Paint.Style.FILL; alpha = 180
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE; textSize = 32f; isAntiAlias = true
    }
    private val saveButtonPaint = Paint().apply {
        color = Color.parseColor("#4CAF50"); style = Paint.Style.FILL
    }

    val saveButtonRect = RectF(screenW / 2f - 170f, 70f, screenW / 2f + 170f, 170f)
    private val handleSize = 44f

    var onSave: (() -> Unit)? = null

    private enum class Drag { NONE, BOX, HANDLE, TAP }
    private var dragging = Drag.NONE
    private var lastX = 0f
    private var lastY = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val box = RectF(boxLeft, boxTop, boxLeft + boxSize, boxTop + boxSize)
        canvas.drawRect(box, boxFill)
        canvas.drawRect(box, boxStroke)
        canvas.drawText("Vùng phao câu (kéo để di chuyển)", boxLeft, boxTop - 16f, textPaint)
        canvas.drawRect(box.right - handleSize, box.bottom - handleSize, box.right, box.bottom, handlePaint)

        canvas.drawCircle(tapX, tapY, 60f, tapPaint)
        canvas.drawText("Điểm chạm câu", tapX - 80f, tapY - 75f, textPaint)

        canvas.drawRoundRect(saveButtonRect, 20f, 20f, saveButtonPaint)
        canvas.drawText("LƯU VỊ TRÍ", saveButtonRect.left + 45f, saveButtonRect.centerY() + 12f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragging = when {
                    saveButtonRect.contains(x, y) -> { onSave?.invoke(); Drag.NONE }
                    isNearHandle(x, y) -> Drag.HANDLE
                    isNearTap(x, y) -> Drag.TAP
                    isInsideBox(x, y) -> Drag.BOX
                    else -> Drag.NONE
                }
                lastX = x; lastY = y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastX
                val dy = y - lastY
                when (dragging) {
                    Drag.BOX -> { boxLeft += dx; boxTop += dy }
                    Drag.HANDLE -> boxSize = (boxSize + dx).coerceIn(60f, 500f)
                    Drag.TAP -> { tapX += dx; tapY += dy }
                    Drag.NONE -> {}
                }
                lastX = x; lastY = y
                invalidate()
            }
            MotionEvent.ACTION_UP -> dragging = Drag.NONE
        }
        return true
    }

    private fun isInsideBox(x: Float, y: Float) =
        x in boxLeft..(boxLeft + boxSize) && y in boxTop..(boxTop + boxSize)

    private fun isNearHandle(x: Float, y: Float): Boolean {
        val hx = boxLeft + boxSize
        val hy = boxTop + boxSize
        return x in (hx - handleSize - 24f)..(hx + 24f) && y in (hy - handleSize - 24f)..(hy + 24f)
    }

    private fun isNearTap(x: Float, y: Float): Boolean {
        val dx = x - tapX
        val dy = y - tapY
        return dx * dx + dy * dy <= 80f * 80f
    }
}
