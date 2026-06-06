package dev.ohaiibuzzle.flashcontrol.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import android.widget.Toast
import dev.ohaiibuzzle.flashcontrol.ble.CobFlashController
import java.security.Key
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class FlashAccessibilityService : AccessibilityService() {
    companion object {
        private var activeService: FlashAccessibilityService? = null

        fun disableActiveService(): Boolean {
            val service = activeService ?: return false
            service.disableSelf()
            return true
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var controller: CobFlashController
    private var windowManager: WindowManager? = null
    private var overlayButton: View? = null
    private var suppressOverlayTapUntilMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
        controller = CobFlashController(applicationContext)
        controller.refreshBondedDevices()
        showOverlayButton()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        return event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                super.onKeyEvent(event)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        removeOverlayButton()
        if (activeService === this) {
            activeService = null
        }
        if (::controller.isInitialized) {
            controller.close()
        }
        super.onDestroy()
    }

    private fun showOverlayButton() {
        if (overlayButton != null) return

        windowManager = getSystemService(WindowManager::class.java)
        val settings = AccessibilityFlashSettingsStore.load(this)
        val buttonSize = 72.dp
        val button = TextView(this).apply {
            text = "●"
            contentDescription = "Flash and tap shutter"
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(229, 57, 53))
                setStroke(4.dp, Color.WHITE)
            }
            elevation = 8.dp.toFloat()
            setOnLongClickListener {
                controller.refreshBondedDevices()
                Toast.makeText(this@FlashAccessibilityService, "Reconnecting flash", Toast.LENGTH_SHORT).show()
                true
            }
        }
        val params = WindowManager.LayoutParams(
            buttonSize,
            buttonSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = settings.overlayX
            y = settings.overlayY
        }
        button.setOnTouchListener(DraggableOverlayTouchListener(params))

        windowManager?.addView(button, params)
        overlayButton = button
    }

    private fun removeOverlayButton() {
        overlayButton?.let { view ->
            windowManager?.removeView(view)
        }
        overlayButton = null
    }

    private fun triggerFlashAndTap() {
        val settings = AccessibilityFlashSettingsStore.load(this)
        if (!settings.hasTapPoint) {
            Toast.makeText(this, "Pick a shutter point in the app first", Toast.LENGTH_SHORT).show()
            return
        }
        if (!controller.ready) {
            controller.refreshBondedDevices()
            Toast.makeText(this, "Flash is not ready yet", Toast.LENGTH_SHORT).show()
        }

        val offsetMs = settings.flashOffsetMs
        if (offsetMs < 0) {
            fireFlash()
            mainHandler.postDelayed({ tapSavedPoint(settings) }, (-offsetMs).toLong())
        } else {
            tapSavedPoint(settings)
            mainHandler.postDelayed({ fireFlash() }, offsetMs.toLong())
        }
    }

    private fun fireFlash() {
        if (controller.ready) {
            controller.testFlash()
        }
    }

    private fun tapSavedPoint(settings: AccessibilityFlashSettings) {
        val metrics = resources.displayMetrics
        val x = (settings.tapXRatio * metrics.widthPixels).roundToInt().toFloat()
        val y = (settings.tapYRatio * metrics.heightPixels).roundToInt().toFloat()
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 80L))
            .build()
        suppressOverlayTapUntilMs = SystemClock.uptimeMillis() + 600L
        overlayButton?.visibility = View.INVISIBLE
        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    restoreOverlayButton()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    restoreOverlayButton()
                }
            },
            mainHandler
        )
        if (!dispatched) {
            restoreOverlayButton()
        } else {
            mainHandler.postDelayed({ restoreOverlayButton() }, 250L)
        }
    }

    private fun restoreOverlayButton() {
        overlayButton?.visibility = View.VISIBLE
    }

    private inner class DraggableOverlayTouchListener(
        private val params: WindowManager.LayoutParams
    ) : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(this@FlashAccessibilityService).scaledTouchSlop
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longPressConsumed = false
        private val longPressRunnable = Runnable {
            overlayButton?.let { view ->
                longPressConsumed = view.performLongClick()
                view.isPressed = false
            }
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (SystemClock.uptimeMillis() < suppressOverlayTapUntilMs) {
                        return true
                    }
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    longPressConsumed = false
                    view.isPressed = true
                    mainHandler.postDelayed(
                        longPressRunnable,
                        ViewConfiguration.getLongPressTimeout().toLong()
                    )
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (!dragging && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) {
                        dragging = true
                        mainHandler.removeCallbacks(longPressRunnable)
                    }
                    if (dragging) {
                        updateOverlayPosition(
                            view = view,
                            x = startX + deltaX.roundToInt(),
                            y = startY + deltaY.roundToInt(),
                            persist = false
                        )
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    view.isPressed = false
                    if (dragging) {
                        updateOverlayPosition(view, params.x, params.y, persist = true)
                    } else if (!longPressConsumed) {
                        view.performClick()
                        triggerFlashAndTap()
                    }
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    view.isPressed = false
                    if (dragging) {
                        updateOverlayPosition(view, params.x, params.y, persist = true)
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun updateOverlayPosition(view: View, x: Int, y: Int, persist: Boolean) {
        val manager = windowManager ?: return
        val metrics = resources.displayMetrics
        val maxX = max(0, metrics.widthPixels - view.width)
        val maxY = max(0, metrics.heightPixels - view.height)
        val clampedX = min(max(0, x), maxX)
        val clampedY = min(max(0, y), maxY)
        val layoutParams = view.layoutParams as WindowManager.LayoutParams
        layoutParams.x = clampedX
        layoutParams.y = clampedY
        manager.updateViewLayout(view, layoutParams)
        if (persist) {
            AccessibilityFlashSettingsStore.saveOverlayPosition(this, clampedX, clampedY)
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).roundToInt()
}
