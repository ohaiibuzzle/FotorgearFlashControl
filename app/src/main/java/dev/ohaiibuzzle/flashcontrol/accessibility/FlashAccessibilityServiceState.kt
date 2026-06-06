package dev.ohaiibuzzle.flashcontrol.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

internal object FlashAccessibilityServiceState {
    fun isEnabled(context: Context): Boolean {
        val expected =
            ComponentName(context, FlashAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        return splitter.any { it.equals(expected, ignoreCase = true) }
    }

    fun requestDisable(): Boolean {
        return FlashAccessibilityService.disableActiveService()
    }
}
