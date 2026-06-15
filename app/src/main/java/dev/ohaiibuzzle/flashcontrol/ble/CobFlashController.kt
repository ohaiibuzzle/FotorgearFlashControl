package dev.ohaiibuzzle.flashcontrol.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.ohaiibuzzle.flashcontrol.hasBlePermissions
import java.util.ArrayDeque
import java.util.UUID

private val FLASH_CHARACTERISTIC_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
private val CLIENT_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
private const val FLASH_DEVICE_NAME = "FG-COB-FLASH"
private const val BLE_PREFS = "cob_flash_ble"
private const val PREF_LAST_CONNECTED_ADDRESS = "last_connected_address"
private const val PREF_LAST_CONNECTED_ADDRESSES = "last_connected_addresses"
private const val AUTO_CONNECT_TIMEOUT_MS = 7000L

data class FlashDevice(
    val address: String,
    val name: String
)

private data class FlashConnection(
    val address: String,
    val gatt: BluetoothGatt,
    var commandCharacteristic: BluetoothGattCharacteristic? = null,
    var connected: Boolean = false,
    var ready: Boolean = false,
    var batteryPercent: Int? = null
)

class CobFlashController(private val context: Context) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val preferences = context.getSharedPreferences(BLE_PREFS, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val connections = mutableMapOf<String, FlashConnection>()
    private val pendingAutoConnectAddresses = ArrayDeque<String>()
    private var autoConnecting = false
    private var activeAutoConnectAddress: String? = null
    private var autoConnectToken = 0
    private var applyingToken = 0

    val devices = mutableStateListOf<FlashDevice>()
    var status by mutableStateOf("Idle")
    var connected by mutableStateOf(false)
    var ready by mutableStateOf(false)
    var readyDeviceCount by mutableStateOf(0)
    var connectionStateVersion by mutableStateOf(0)
    var applying by mutableStateOf(false)
    var batteryPercent by mutableStateOf<Int?>(null)
    var brightnessPercent by mutableStateOf<Int?>(null)
    var lastRx by mutableStateOf<String?>(null)
    var lastTx by mutableStateOf<String?>(null)

    private val gattCallback = object : android.bluetooth.BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, statusCode: Int, newState: Int) {
            val address = gatt.device.address
            val connection = connections[address]
            updateState {
                connection?.connected = newState == BluetoothProfile.STATE_CONNECTED
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connection?.ready = false
                    connection?.commandCharacteristic = null
                }
                refreshConnectionState()
                status = when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> "Connected, discovering services"
                    BluetoothProfile.STATE_DISCONNECTED -> "Disconnected"
                    else -> "Connection state $newState"
                }
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (
                newState == BluetoothProfile.STATE_DISCONNECTED &&
                autoConnecting &&
                activeAutoConnectAddress == address
            ) {
                connectNextAutoCandidate()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, statusCode: Int) {
            val address = gatt.device.address
            val connection = connections[address] ?: FlashConnection(address, gatt).also {
                connections[address] = it
            }
            val characteristic = gatt.services
                .flatMap { it.characteristics }
                .firstOrNull { it.uuid == FLASH_CHARACTERISTIC_UUID }

            if (characteristic == null) {
                connection.ready = false
                connection.commandCharacteristic = null
                connections.remove(address)
                updateState {
                    refreshConnectionState()
                    status = "FFE1 characteristic not found on $address"
                }
                gatt.close()
                if (autoConnecting && activeAutoConnectAddress == address) {
                    connectNextAutoCandidate()
                }
                return
            }

            rememberConnectedAddress(address)
            connection.connected = true
            connection.ready = true
            connection.commandCharacteristic = characteristic
            updateState {
                refreshConnectionState()
                status = readyStatus()
            }
            enableNotifications(gatt, characteristic)
            if (autoConnecting && activeAutoConnectAddress == address) {
                connectNextAutoCandidate()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncoming(gatt.device.address, value)
        }

        @Deprecated("Used by Android 12 and lower")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleIncoming(gatt.device.address, characteristic.value ?: return)
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshBondedDevices() {
        if (!context.hasBlePermissions()) {
            status = "Bluetooth permissions required"
            return
        }
        if (adapter?.isEnabled != true) {
            status = "Bluetooth is off"
            return
        }

        devices.clear()
        adapter.bondedDevices
            .map { device ->
                FlashDevice(
                    address = device.address,
                    name = device.name.orEmpty()
                )
            }
            .sortedWith(compareBy<FlashDevice> { it.name.ifBlank { "~" } }.thenBy { it.address })
            .forEach { devices.add(it) }

        val lastConnectedDevices = loadLastConnectedAddresses().mapNotNull { address ->
            devices.firstOrNull { it.address == address }
        }
        val namedFlashDevices = devices.filter { it.name == FLASH_DEVICE_NAME }

        val candidates = buildAutoConnectCandidates(lastConnectedDevices, namedFlashDevices)
        when {
            devices.isEmpty() -> status = "No bonded Bluetooth devices found"
            candidates.isNotEmpty() -> {
                pendingAutoConnectAddresses.clear()
                pendingAutoConnectAddresses.addAll(candidates.map { it.address })
                status = "Trying flash control device"
                connectNextAutoCandidate()
            }
            else -> {
                pendingAutoConnectAddresses.clear()
                autoConnecting = false
                status = "Found ${devices.size} bonded Bluetooth device(s)"
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        pendingAutoConnectAddresses.remove(address)
        connectInternal(address)
    }

    private fun buildAutoConnectCandidates(
        lastConnectedDevices: List<FlashDevice>,
        namedFlashDevices: List<FlashDevice>
    ): List<FlashDevice> {
        return buildList {
            lastConnectedDevices.forEach { add(it) }
            val lastConnectedAddresses = lastConnectedDevices.map { it.address }.toSet()
            namedFlashDevices
                .filterNot { it.address in lastConnectedAddresses }
                .forEach { add(it) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectNextAutoCandidate() {
        val address = pendingAutoConnectAddresses.pollFirst()
        if (address == null) {
            autoConnecting = false
            activeAutoConnectAddress = null
            autoConnectToken += 1
            updateState {
                refreshConnectionState()
                status = if (readyDeviceCount > 0) readyStatus() else "No bonded flash control service found"
            }
            return
        }
        if (connections[address]?.ready == true) {
            connectNextAutoCandidate()
            return
        }
        autoConnecting = true
        activeAutoConnectAddress = address
        val token = ++autoConnectToken
        connectInternal(address)
        mainHandler.postDelayed({
            if (
                autoConnecting &&
                activeAutoConnectAddress == address &&
                autoConnectToken == token &&
                connections[address]?.ready != true
            ) {
                connections.remove(address)?.gatt?.close()
                updateState {
                    refreshConnectionState()
                    status = "Timed out connecting to $address"
                }
                connectNextAutoCandidate()
            }
        }, AUTO_CONNECT_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    private fun connectInternal(address: String) {
        if (!context.hasBlePermissions()) {
            status = "Bluetooth permissions required"
            return
        }
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            status = "Invalid Bluetooth address"
            return
        }

        updateState {
            refreshConnectionState()
            status = "Connecting to $address"
        }
        val device = adapter?.bondedDevices?.firstOrNull { it.address == address }
        if (device == null) {
            status = "Device is not bonded"
            return
        }

        connections[address]?.gatt?.close()
        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
        connections[address] = FlashConnection(address = address, gatt = gatt)
    }

    fun sendPreFlash(ms: Int) {
        writePayload(CobFlashProtocol.preFlashCommand(ms))
    }

    fun sendTrigger(ms: Int) {
        writePayload(CobFlashProtocol.triggerCommand(ms))
    }

    fun sendBrightness(level: Int) {
        writePayload(CobFlashProtocol.brightnessCommand(level))
    }

    fun testFlash() {
        writePayload(CobFlashProtocol.testFlashCommand())
    }

    fun sendTimings(preFlashMs: Int, triggerMs: Int) {
        markApplying(durationMs = 1500)
        writePayload(CobFlashProtocol.preFlashCommand(preFlashMs), markApplying = false)
        mainHandler.postDelayed({
            writePayload(CobFlashProtocol.triggerCommand(triggerMs), markApplying = false)
        }, 1000)
    }

    fun isDeviceConnected(address: String): Boolean {
        return connections[address]?.let { it.connected || it.ready } == true
    }

    fun deviceBatteryPercent(address: String): Int? {
        return connections[address]?.batteryPercent
    }

    @SuppressLint("MissingPermission")
    fun disconnect(address: String) {
        pendingAutoConnectAddresses.remove(address)
        connections.remove(address)?.let { connection ->
            connection.gatt.disconnect()
            connection.gatt.close()
        }
        refreshConnectionState()
        status = if (readyDeviceCount > 0) readyStatus() else "Disconnected"
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        pendingAutoConnectAddresses.clear()
        autoConnecting = false
        connections.values.forEach { connection ->
            connection.gatt.disconnect()
            connection.gatt.close()
        }
        connections.clear()
        refreshConnectionState()
        status = "Disconnected"
    }

    @SuppressLint("MissingPermission")
    fun close() {
        connections.values.forEach { it.gatt.close() }
        connections.clear()
        refreshConnectionState()
    }

    @SuppressLint("MissingPermission")
    private fun writePayload(payload: ByteArray, markApplying: Boolean = true) {
        val readyConnections = connections.values.filter { it.ready && it.commandCharacteristic != null }
        if (readyConnections.isEmpty()) {
            status = "Not connected to FFE1"
            return
        }

        val successCount = readyConnections.count { connection ->
            val characteristic = connection.commandCharacteristic ?: return@count false
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                connection.gatt.writeCharacteristic(
                    characteristic,
                    payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                ) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = payload
                @Suppress("DEPRECATION")
                connection.gatt.writeCharacteristic(characteristic)
            }
        }
        if (markApplying) {
            markApplying()
        }
        lastTx = payload.toHex()
        status = if (successCount == readyConnections.size) {
            "Sent ${payload.toHex()} to ${readyConnections.size} flash device(s)"
        } else {
            "Write started on $successCount/${readyConnections.size} flash device(s)"
        }
    }

    private fun markApplying(durationMs: Long = 700) {
        val token = ++applyingToken
        applying = true
        mainHandler.postDelayed({
            if (token == applyingToken) {
                applying = false
            }
        }, durationMs)
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_UUID) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun handleIncoming(address: String, value: ByteArray) {
        val hex = value.toHex()
        updateState {
            lastRx = hex
            when (val update = CobFlashProtocol.parseStatus(value)) {
                is FlashStatusUpdate.Brightness -> {
                    brightnessPercent = update.percent
                    status = "Brightness status: ${update.percent}%"
                }
                is FlashStatusUpdate.Battery -> {
                    batteryPercent = update.percent
                    connections[address]?.batteryPercent = update.percent
                    connectionStateVersion += 1
                    status = "Battery status: ${update.percent}%"
                }
                is FlashStatusUpdate.PreFlashEcho -> status = "Pre-flash echo: ${update.hex}"
                is FlashStatusUpdate.TriggerEcho -> status = "Trigger echo: ${update.hex}"
                is FlashStatusUpdate.Unknown -> status = "Received ${update.hex}"
            }
        }
    }

    private fun loadLastConnectedAddresses(): List<String> {
        val rememberedAddresses = preferences.getString(PREF_LAST_CONNECTED_ADDRESSES, null)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { BluetoothAdapter.checkBluetoothAddress(it) }
            .orEmpty()
        if (rememberedAddresses.isNotEmpty()) {
            return rememberedAddresses
        }

        return listOfNotNull(preferences.getString(PREF_LAST_CONNECTED_ADDRESS, null))
            .filter { BluetoothAdapter.checkBluetoothAddress(it) }
    }

    private fun rememberConnectedAddress(address: String) {
        val updatedAddresses = (listOf(address) + loadLastConnectedAddresses().filterNot { it == address })
            .take(8)
        preferences.edit()
            .putString(PREF_LAST_CONNECTED_ADDRESS, address)
            .putString(PREF_LAST_CONNECTED_ADDRESSES, updatedAddresses.joinToString(","))
            .apply()
    }

    private fun refreshConnectionState() {
        connected = connections.values.any { it.connected }
        readyDeviceCount = connections.values.count { it.ready }
        ready = readyDeviceCount > 0
        connectionStateVersion += 1
    }

    private fun readyStatus(): String {
        return "Ready on $readyDeviceCount flash device(s)"
    }

    private fun updateState(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
