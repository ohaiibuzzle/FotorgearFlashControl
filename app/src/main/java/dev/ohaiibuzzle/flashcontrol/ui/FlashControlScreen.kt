package dev.ohaiibuzzle.flashcontrol.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.ohaiibuzzle.flashcontrol.accessibility.AccessibilityFlashSettingsStore
import dev.ohaiibuzzle.flashcontrol.accessibility.FlashAccessibilityServiceState
import dev.ohaiibuzzle.flashcontrol.accessibility.TapPointPickerActivity
import dev.ohaiibuzzle.flashcontrol.ble.CobFlashController
import dev.ohaiibuzzle.flashcontrol.hasBlePermissions
import dev.ohaiibuzzle.flashcontrol.requiredBlePermissions
import kotlinx.coroutines.delay

private const val SETTINGS_PREFS = "flash_settings"
private const val PREF_PRE_FLASH_MS = "pre_flash_ms"
private const val PREF_TRIGGER_MS = "trigger_ms"
private const val PREF_BRIGHTNESS_LEVEL = "brightness_level"
private const val PREF_BRIGHTNESS_PERCENT = "brightness_percent"

@Composable
fun FlashControlApp() {
    val context = LocalContext.current
    val controller = remember { CobFlashController(context.applicationContext) }
    val preferences = remember {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    }
    var preFlashMs by remember {
        mutableIntStateOf(preferences.getInt(PREF_PRE_FLASH_MS, 300).coerceIn(100, 500))
    }
    var triggerMs by remember {
        mutableIntStateOf(preferences.getInt(PREF_TRIGGER_MS, 200).coerceIn(100, 300))
    }
    var brightnessPercent by remember {
        mutableIntStateOf(
            if (preferences.contains(PREF_BRIGHTNESS_PERCENT)) {
                preferences.getInt(PREF_BRIGHTNESS_PERCENT, 100).coerceIn(20, 100)
            } else {
                preferences.getInt(PREF_BRIGHTNESS_LEVEL, 5).coerceIn(1, 5) * 20
            }
        )
    }
    var devicesExpanded by remember { mutableStateOf(false) }
    var preFlashTouched by remember { mutableStateOf(false) }
    var triggerTouched by remember { mutableStateOf(false) }
    var brightnessTouched by remember { mutableStateOf(false) }
    var accessibilitySettings by remember {
        mutableStateOf(AccessibilityFlashSettingsStore.load(context))
    }
    var accessibilityServiceEnabled by remember {
        mutableStateOf(FlashAccessibilityServiceState.isEnabled(context))
    }
    var showAccessibilityDisclaimer by remember { mutableStateOf(false) }
    var hideAccessibilityDisclaimerChecked by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            controller.refreshBondedDevices()
        } else {
            controller.status = "Bluetooth permissions denied"
        }
    }
    val tapPointPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        accessibilitySettings = AccessibilityFlashSettingsStore.load(context)
    }

    LaunchedEffect(Unit) {
        if (context.hasBlePermissions()) {
            controller.refreshBondedDevices()
        } else {
            permissionLauncher.launch(requiredBlePermissions())
        }
    }

    LaunchedEffect(controller.ready) {
        if (controller.ready) {
            delay(300)
            controller.sendTimings(preFlashMs, triggerMs)
        }
    }

    LaunchedEffect(preFlashMs, controller.ready, preFlashTouched) {
        if (controller.ready && preFlashTouched) {
            delay(500)
            controller.sendPreFlash(preFlashMs)
        }
    }

    LaunchedEffect(triggerMs, controller.ready, triggerTouched) {
        if (controller.ready && triggerTouched) {
            delay(500)
            controller.sendTrigger(triggerMs)
        }
    }

    LaunchedEffect(brightnessPercent, controller.ready, brightnessTouched) {
        if (controller.ready && brightnessTouched) {
            delay(500)
            controller.sendBrightness(brightnessPercent / 20)
        }
    }

    LaunchedEffect(controller.brightnessPercent) {
        controller.brightnessPercent?.let { percent ->
            val reportedPercent = percent.coerceIn(20, 100)
            if (reportedPercent != brightnessPercent) {
                brightnessTouched = false
                brightnessPercent = reportedPercent
                preferences.edit().putInt(PREF_BRIGHTNESS_PERCENT, reportedPercent).apply()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityServiceEnabled = FlashAccessibilityServiceState.isEnabled(context)
                accessibilitySettings = AccessibilityFlashSettingsStore.load(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose { controller.close() }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "COB Flash Control",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                ApplyStatusIndicator(
                    ready = controller.ready,
                    applying = controller.applying
                )
            }

            StatusPanel(controller = controller)

            DeviceList(
                controller = controller,
                expanded = devicesExpanded,
                onExpandedChange = { devicesExpanded = it },
                onRefresh = {
                    if (context.hasBlePermissions()) {
                        controller.refreshBondedDevices()
                    } else {
                        permissionLauncher.launch(requiredBlePermissions())
                    }
                },
                onDisconnect = { controller.disconnect() }
            )

            CommandPanel(
                connected = controller.ready,
                preFlashMs = preFlashMs,
                triggerMs = triggerMs,
                brightnessPercent = brightnessPercent,
                onTestFlash = { controller.testFlash() },
                onPreFlashChange = {
                    preFlashTouched = true
                    preFlashMs = it
                    preferences.edit().putInt(PREF_PRE_FLASH_MS, it).apply()
                },
                onTriggerChange = {
                    triggerTouched = true
                    triggerMs = it
                    preferences.edit().putInt(PREF_TRIGGER_MS, it).apply()
                },
                onBrightnessChange = {
                    brightnessTouched = true
                    brightnessPercent = it
                    controller.brightnessPercent = it
                    preferences.edit().putInt(PREF_BRIGHTNESS_PERCENT, it).apply()
                }
            )

            AccessibilityPanel(
                settings = accessibilitySettings,
                serviceEnabled = accessibilityServiceEnabled,
                onToggleService = {
                    if (accessibilityServiceEnabled) {
                        val disabled = FlashAccessibilityServiceState.requestDisable()
                        accessibilityServiceEnabled = FlashAccessibilityServiceState.isEnabled(context)
                        if (!disabled) {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    } else if (accessibilitySettings.hideAccessibilityDisclaimer) {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } else {
                        hideAccessibilityDisclaimerChecked = false
                        showAccessibilityDisclaimer = true
                    }
                },
                onPickTapPoint = {
                    tapPointPickerLauncher.launch(Intent(context, TapPointPickerActivity::class.java))
                },
                onFlashOffsetChange = { offsetMs ->
                    AccessibilityFlashSettingsStore.saveFlashOffset(context, offsetMs)
                    accessibilitySettings = AccessibilityFlashSettingsStore.load(context)
                }
            )
        }
    }

    if (showAccessibilityDisclaimer) {
        AccessibilityDisclaimerDialog(
            doNotShowAgain = hideAccessibilityDisclaimerChecked,
            onDoNotShowAgainChange = { hideAccessibilityDisclaimerChecked = it },
            onBack = { showAccessibilityDisclaimer = false },
            onGoToSettings = {
                AccessibilityFlashSettingsStore.saveHideAccessibilityDisclaimer(
                    context,
                    hideAccessibilityDisclaimerChecked
                )
                accessibilitySettings = AccessibilityFlashSettingsStore.load(context)
                showAccessibilityDisclaimer = false
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )
    }
}
