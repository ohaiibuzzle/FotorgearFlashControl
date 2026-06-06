package dev.ohaiibuzzle.flashcontrol.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CobFlashProtocolTest {
    @Test
    fun preFlashCommand_encodesMillisecondsLittleEndian() {
        assertArrayEquals(
            byteArrayOf(0x03, 0xF4.toByte(), 0x01),
            CobFlashProtocol.preFlashCommand(500)
        )
    }

    @Test
    fun triggerCommand_encodesMillisecondsLittleEndian() {
        assertArrayEquals(
            byteArrayOf(0x04, 0x2C, 0x01),
            CobFlashProtocol.triggerCommand(300)
        )
    }

    @Test
    fun testFlashCommand_matchesIosCommand() {
        assertArrayEquals(
            byteArrayOf(0x05, 0x00),
            CobFlashProtocol.testFlashCommand()
        )
    }

    @Test
    fun brightnessStatus_mapsDeviceLevelsToPercent() {
        val expected = listOf(20, 40, 60, 80, 100)

        val actual = (1..5).map { level ->
            val update = CobFlashProtocol.parseStatus(byteArrayOf(0x01, level.toByte()))
            (update as FlashStatusUpdate.Brightness).percent
        }

        assertEquals(expected, actual)
    }

    @Test
    fun batteryStatus_usesRawHexByteAsPercent() {
        val update = CobFlashProtocol.parseStatus(byteArrayOf(0x02, 0x30))

        assertEquals(48, (update as FlashStatusUpdate.Battery).percent)
    }

    @Test
    fun statusParser_preservesEchoAndUnknownHex() {
        assertEquals(
            FlashStatusUpdate.PreFlashEcho("03 F4 01"),
            CobFlashProtocol.parseStatus(byteArrayOf(0x03, 0xF4.toByte(), 0x01))
        )
        assertEquals(
            FlashStatusUpdate.TriggerEcho("04 2C 01"),
            CobFlashProtocol.parseStatus(byteArrayOf(0x04, 0x2C, 0x01))
        )
        assertEquals(
            FlashStatusUpdate.Unknown("7F 01"),
            CobFlashProtocol.parseStatus(byteArrayOf(0x7F, 0x01))
        )
    }
}
