package dev.ohaiibuzzle.flashcontrol.accessibility

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast

class TapPointPickerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TapPointPickerView(this))
    }

    private inner class TapPointPickerView(context: Activity) : View(context) {
        private val backgroundPaint = Paint().apply {
            color = Color.rgb(18, 18, 18)
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 42f
        }
        private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            textAlign = Paint.Align.CENTER
            textSize = 28f
        }
        private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(229, 57, 53)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
            val centerX = width / 2f
            val centerY = height / 2f
            canvas.drawText("Tap the shutter point", centerX, centerY - 48f, textPaint)
            canvas.drawText("This position will be used by the floating button", centerX, centerY, subTextPaint)
            canvas.drawCircle(centerX, centerY + 96f, 28f, targetPaint)
            canvas.drawLine(centerX - 48f, centerY + 96f, centerX + 48f, centerY + 96f, targetPaint)
            canvas.drawLine(centerX, centerY + 48f, centerX, centerY + 144f, targetPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP && width > 0 && height > 0) {
                AccessibilityFlashSettingsStore.saveTapPoint(
                    context = this@TapPointPickerActivity,
                    xRatio = event.x / width,
                    yRatio = event.y / height
                )
                Toast.makeText(this@TapPointPickerActivity, "Tap point saved", Toast.LENGTH_SHORT).show()
                finish()
                return true
            }
            return true
        }
    }
}
