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
import java.util.UUID

private val FLASH_CHARACTERISTIC_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
private val CLIENT_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

data class FlashDevice(
    val address: String,
    val name: String
)

class CobFlashController(private val context: Context) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var applyingToken = 0

    val devices = mutableStateListOf<FlashDevice>()
    var status by mutableStateOf("Idle")
    var connected by mutableStateOf(false)
    var ready by mutableStateOf(false)
    var applying by mutableStateOf(false)
    var batteryPercent by mutableStateOf<Int?>(null)
    var brightnessPercent by mutableStateOf<Int?>(null)
    var lastRx by mutableStateOf<String?>(null)
    var lastTx by mutableStateOf<String?>(null)

    private val gattCallback = object : android.bluetooth.BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, statusCode: Int, newState: Int) {
            updateState {
                connected = newState == BluetoothProfile.STATE_CONNECTED
                ready = false
                status = when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> "Connected, discovering services"
                    BluetoothProfile.STATE_DISCONNECTED -> "Disconnected"
                    else -> "Connection state $newState"
                }
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, statusCode: Int) {
            commandCharacteristic = gatt.services
                .flatMap { it.characteristics }
                .firstOrNull { it.uuid == FLASH_CHARACTERISTIC_UUID }

            updateState {
                ready = commandCharacteristic != null
                status = if (ready) "Ready on FFE1" else "FFE1 characteristic not found"
            }
            commandCharacteristic?.let { enableNotifications(gatt, it) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncoming(value)
        }

        @Deprecated("Used by Android 12 and lower")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleIncoming(characteristic.value ?: return)
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

        when (devices.size) {
            0 -> status = "No bonded Bluetooth devices found"
            1 -> {
                status = "One bonded device found, connecting"
                connect(devices.first().address)
            }
            else -> status = "Found ${devices.size} bonded Bluetooth device(s)"
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        if (!context.hasBlePermissions()) {
            status = "Bluetooth permissions required"
            return
        }
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            status = "Invalid Bluetooth address"
            return
        }

        ready = false
        status = "Connecting to $address"
        gatt?.close()
        val device = adapter?.bondedDevices?.firstOrNull { it.address == address }
        if (device == null) {
            status = "Device is not bonded"
            return
        }

        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
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

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
    }

    @SuppressLint("MissingPermission")
    fun close() {
        gatt?.close()
    }

    @SuppressLint("MissingPermission")
    private fun writePayload(payload: ByteArray, markApplying: Boolean = true) {
        val gatt = gatt
        val characteristic = commandCharacteristic
        if (gatt == null || characteristic == null) {
            status = "Not connected to FFE1"
            return
        }

        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
        if (markApplying) {
            markApplying()
        }
        lastTx = payload.toHex()
        status = if (ok) "Sent ${payload.toHex()}" else "Write failed to start"
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

    private fun handleIncoming(value: ByteArray) {
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
                    status = "Battery status: ${update.percent}%"
                }
                is FlashStatusUpdate.PreFlashEcho -> status = "Pre-flash echo: ${update.hex}"
                is FlashStatusUpdate.TriggerEcho -> status = "Trigger echo: ${update.hex}"
                is FlashStatusUpdate.Unknown -> status = "Received ${update.hex}"
            }
        }
    }

    private fun updateState(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
