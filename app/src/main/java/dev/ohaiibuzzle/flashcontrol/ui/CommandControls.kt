package dev.ohaiibuzzle.flashcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun CommandPanel(
    connected: Boolean,
    preFlashMs: Int,
    triggerMs: Int,
    brightnessPercent: Int,
    onTestFlash: () -> Unit,
    onPreFlashChange: (Int) -> Unit,
    onTriggerChange: (Int) -> Unit,
    onBrightnessChange: (Int) -> Unit
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
                label = "Brightness",
                value = brightnessPercent,
                range = 20..100,
                step = 20,
                valueLabel = { "$it%" },
                enabled = connected,
                onChange = onBrightnessChange
            )
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
internal fun BoxedNumberInput(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontWeight = FontWeight.Medium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { onChange((value - step).coerceIn(range.first, range.last)) }
            ) {
                Text("-")
            }
            OutlinedTextField(
                value = text,
                onValueChange = { raw ->
                    if (raw.matches(Regex("-?\\d{0,4}"))) {
                        text = raw
                        raw.toIntOrNull()?.let { parsed ->
                            onChange(parsed.coerceIn(range.first, range.last))
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                suffix = { Text("ms") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedButton(
                onClick = { onChange((value + step).coerceIn(range.first, range.last)) }
            ) {
                Text("+")
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
    valueLabel: (Int) -> String = { "${it}ms" },
    onChange: (Int) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$label: ${valueLabel(value)}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
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
