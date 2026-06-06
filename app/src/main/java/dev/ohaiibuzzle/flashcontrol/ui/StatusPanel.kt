package dev.ohaiibuzzle.flashcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ohaiibuzzle.flashcontrol.ble.CobFlashController

@Composable
internal fun ApplyStatusIndicator(ready: Boolean, applying: Boolean) {
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
internal fun StatusPanel(controller: CobFlashController) {
    var expanded by remember { mutableStateOf(false) }

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
            OutlinedButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide Last RX/TX" else "Show Last RX/TX")
            }
            if (expanded) {
                Text("Last RX: ${controller.lastRx ?: "--"}")
                Text("Last TX: ${controller.lastTx ?: "--"}")
            }
        }
    }
}
