package dev.ohaiibuzzle.flashcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ohaiibuzzle.flashcontrol.accessibility.AccessibilityFlashSettings

@Composable
internal fun AccessibilityPanel(
    settings: AccessibilityFlashSettings,
    serviceEnabled: Boolean,
    onToggleService: () -> Unit,
    onPickTapPoint: () -> Unit,
    onFlashOffsetChange: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Floating shutter button", fontWeight = FontWeight.SemiBold)
            Text("Tap point: ${settings.tapPercentLabel}")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onPickTapPoint) {
                    Text("Pick Point")
                }
                OutlinedButton(onClick = onToggleService) {
                    Text(if (serviceEnabled) "Disable Service" else "Enable Service")
                }
            }
            BoxedNumberInput(
                label = "Flash offset",
                value = settings.flashOffsetMs,
                range = -2000..2000,
                step = 100,
                onChange = onFlashOffsetChange
            )
        }
    }
}
