package dev.ohaiibuzzle.flashcontrol.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ohaiibuzzle.flashcontrol.ble.CobFlashController
import dev.ohaiibuzzle.flashcontrol.hasBlePermissions
import dev.ohaiibuzzle.flashcontrol.requiredBlePermissions
import kotlinx.coroutines.delay

private const val SETTINGS_PREFS = "flash_settings"
private const val PREF_PRE_FLASH_MS = "pre_flash_ms"
private const val PREF_TRIGGER_MS = "trigger_ms"

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
    var devicesExpanded by remember { mutableStateOf(false) }
    var preFlashTouched by remember { mutableStateOf(false) }
    var triggerTouched by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            controller.refreshBondedDevices()
        } else {
            controller.status = "Bluetooth permissions denied"
        }
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

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (context.hasBlePermissions()) {
                            controller.refreshBondedDevices()
                        } else {
                            permissionLauncher.launch(requiredBlePermissions())
                        }
                    }
                ) {
                    Text("Refresh Bonded")
                }
                OutlinedButton(
                    enabled = controller.connected,
                    onClick = { controller.disconnect() }
                ) {
                    Text("Disconnect")
                }
            }

            DeviceList(
                controller = controller,
                expanded = devicesExpanded,
                onExpandedChange = { devicesExpanded = it }
            )

            CommandPanel(
                connected = controller.ready,
                preFlashMs = preFlashMs,
                triggerMs = triggerMs,
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
                }
            )
        }
    }
}

@Composable
private fun ApplyStatusIndicator(ready: Boolean, applying: Boolean) {
    val text = if (applying) "Applying" else "Ready"
    val color = if (applying) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)

    if (ready || applying) {
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun StatusPanel(controller: CobFlashController) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(controller.status, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Battery: ${controller.batteryPercent?.let { "$it%" } ?: "--"}")
                Text("Brightness: ${controller.brightnessPercent?.let { "$it%" } ?: "--"}")
            }
            Text("Last RX: ${controller.lastRx ?: "--"}")
            Text("Last TX: ${controller.lastTx ?: "--"}")
        }
    }
}

@Composable
private fun DeviceList(
    controller: CobFlashController,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bonded devices", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { onExpandedChange(!expanded) }) {
                    Text(if (expanded) "Hide" else "Show")
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.height(170.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(controller.devices, key = { it.address }) { device ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.name.ifBlank { "(unnamed)" }, fontWeight = FontWeight.Medium)
                                Text(device.address)
                            }
                            Button(onClick = { controller.connect(device.address) }) {
                                Text("Connect")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandPanel(
    connected: Boolean,
    preFlashMs: Int,
    triggerMs: Int,
    onTestFlash: () -> Unit,
    onPreFlashChange: (Int) -> Unit,
    onTriggerChange: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CommandSlider(
                label = "Pre-flash",
                value = preFlashMs,
                range = 100..500,
                step = 100,
                enabled = connected,
                onChange = onPreFlashChange
            )
            CommandSlider(
                label = "Trigger time",
                value = triggerMs,
                range = 100..300,
                step = 100,
                enabled = connected,
                onChange = onTriggerChange
            )
            Button(
                enabled = connected,
                modifier = Modifier.fillMaxWidth(),
                onClick = onTestFlash
            ) {
                Text("Test Flash")
            }
        }
    }
}

@Composable
private fun CommandSlider(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$label: ${value}ms", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        }
        Slider(
            enabled = enabled,
            value = value.toFloat(),
            onValueChange = { raw ->
                val snapped = ((raw.toInt() + step / 2) / step) * step
                onChange(snapped.coerceIn(range.first, range.last))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0)
        )
    }
}
