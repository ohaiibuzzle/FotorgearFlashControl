package dev.ohaiibuzzle.flashcontrol.accessibility

import android.content.Context
import kotlin.math.roundToInt

private const val PREFS_NAME = "accessibility_flash_settings"
private const val KEY_TAP_X_RATIO = "tap_x_ratio"
private const val KEY_TAP_Y_RATIO = "tap_y_ratio"
private const val KEY_HAS_TAP_POINT = "has_tap_point"
private const val KEY_FLASH_OFFSET_MS = "flash_offset_ms"
private const val KEY_OVERLAY_X = "overlay_x"
private const val KEY_OVERLAY_Y = "overlay_y"

internal data class AccessibilityFlashSettings(
    val hasTapPoint: Boolean,
    val tapXRatio: Float,
    val tapYRatio: Float,
    val flashOffsetMs: Int,
    val overlayX: Int,
    val overlayY: Int
) {
    val tapPercentLabel: String
        get() = if (hasTapPoint) {
            "${(tapXRatio * 100).roundToInt()}%, ${(tapYRatio * 100).roundToInt()}%"
        } else {
            "Not set"
        }
}

internal object AccessibilityFlashSettingsStore {
    fun load(context: Context): AccessibilityFlashSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AccessibilityFlashSettings(
            hasTapPoint = prefs.getBoolean(KEY_HAS_TAP_POINT, false),
            tapXRatio = prefs.getFloat(KEY_TAP_X_RATIO, 0.5f).coerceIn(0f, 1f),
            tapYRatio = prefs.getFloat(KEY_TAP_Y_RATIO, 0.5f).coerceIn(0f, 1f),
            flashOffsetMs = prefs.getInt(KEY_FLASH_OFFSET_MS, 0).coerceIn(-2000, 2000),
            overlayX = prefs.getInt(KEY_OVERLAY_X, 24).coerceAtLeast(0),
            overlayY = prefs.getInt(KEY_OVERLAY_Y, 240).coerceAtLeast(0)
        )
    }

    fun saveTapPoint(context: Context, xRatio: Float, yRatio: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAS_TAP_POINT, true)
            .putFloat(KEY_TAP_X_RATIO, xRatio.coerceIn(0f, 1f))
            .putFloat(KEY_TAP_Y_RATIO, yRatio.coerceIn(0f, 1f))
            .apply()
    }

    fun saveFlashOffset(context: Context, offsetMs: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_FLASH_OFFSET_MS, offsetMs.coerceIn(-2000, 2000))
            .apply()
    }

    fun saveOverlayPosition(context: Context, x: Int, y: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_OVERLAY_X, x.coerceAtLeast(0))
            .putInt(KEY_OVERLAY_Y, y.coerceAtLeast(0))
            .apply()
    }
}
