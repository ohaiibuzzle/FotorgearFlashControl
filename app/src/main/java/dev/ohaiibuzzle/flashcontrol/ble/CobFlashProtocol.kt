package dev.ohaiibuzzle.flashcontrol.ble

import java.util.Locale

internal object CobFlashProtocol {
    fun preFlashCommand(ms: Int): ByteArray = timedCommand(0x03, ms)

    fun triggerCommand(ms: Int): ByteArray = timedCommand(0x04, ms)

    fun testFlashCommand(): ByteArray = byteArrayOf(0x05, 0x00)

    fun parseStatus(value: ByteArray): FlashStatusUpdate {
        if (value.size < 2) {
            return FlashStatusUpdate.Unknown(value.toHex())
        }

        val payload = value[1].toInt() and 0xff
        return when (val command = value[0].toInt() and 0xff) {
            0x01 -> FlashStatusUpdate.Brightness(payload.coerceIn(0, 5) * 20)
            0x02 -> FlashStatusUpdate.Battery(payload.coerceIn(0, 100))
            0x03 -> FlashStatusUpdate.PreFlashEcho(value.toHex())
            0x04 -> FlashStatusUpdate.TriggerEcho(value.toHex())
            else -> FlashStatusUpdate.Unknown(value.toHex())
        }
    }

    private fun timedCommand(command: Int, ms: Int): ByteArray {
        val value = ms.coerceIn(0, 0xffff)
        return byteArrayOf(
            command.toByte(),
            (value and 0xff).toByte(),
            ((value shr 8) and 0xff).toByte()
        )
    }
}

internal sealed interface FlashStatusUpdate {
    data class Brightness(val percent: Int) : FlashStatusUpdate
    data class Battery(val percent: Int) : FlashStatusUpdate
    data class PreFlashEcho(val hex: String) : FlashStatusUpdate
    data class TriggerEcho(val hex: String) : FlashStatusUpdate
    data class Unknown(val hex: String) : FlashStatusUpdate
}

internal fun ByteArray.toHex(): String {
    return joinToString(separator = " ") { byte ->
        "%02X".format(Locale.US, byte.toInt() and 0xff)
    }
}
