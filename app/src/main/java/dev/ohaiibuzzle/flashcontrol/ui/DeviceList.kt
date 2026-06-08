package dev.ohaiibuzzle.flashcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ohaiibuzzle.flashcontrol.ble.CobFlashController

@Composable
internal fun DeviceList(
    controller: CobFlashController,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        val connectionStateVersion = controller.connectionStateVersion
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Devices", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { onExpandedChange(!expanded) }) {
                    Text(if (expanded) "Hide" else "Show")
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onRefresh) {
                        Text("Refresh Paired")
                    }
                    OutlinedButton(
                        enabled = controller.connected,
                        onClick = onDisconnect
                    ) {
                        Text("Disconnect All")
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.height(170.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(controller.devices, key = { it.address }) { device ->
                        @Suppress("UNUSED_VARIABLE")
                        val observedConnectionState = connectionStateVersion
                        val isConnected = controller.isDeviceConnected(device.address)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.name.ifBlank { "(unnamed)" }, fontWeight = FontWeight.Medium)
                                Text(device.address)
                            }
                            if (isConnected) {
                                OutlinedButton(onClick = { controller.disconnect(device.address) }) {
                                    Text("Disconnect")
                                }
                            } else {
                                Button(onClick = { controller.connect(device.address) }) {
                                    Text("Connect")
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
