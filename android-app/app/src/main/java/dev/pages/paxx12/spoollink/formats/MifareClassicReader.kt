package dev.pages.paxx12.spoollink.formats

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.MifareClassic

object MifareClassicReader {
    private val defaultKeys = listOf(
        byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
        byteArrayOf(0xA0.toByte(), 0xA1.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte()),
        byteArrayOf(0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte()),
    )

    fun tagDescription(tag: Tag): String? {
        val mc = MifareClassic.get(tag) ?: return null
        return when (mc.type) {
            MifareClassic.TYPE_PLUS -> "Mifare Plus"
            MifareClassic.TYPE_PRO -> "Mifare Pro"
            else -> when (mc.size) {
                MifareClassic.SIZE_1K -> "Mifare Classic 1K"
                MifareClassic.SIZE_2K -> "Mifare Classic 2K"
                MifareClassic.SIZE_4K -> "Mifare Classic 4K"
                else -> "Mifare Classic"
            }
        }
    }

    fun tryReadNdef(tag: Tag): NdefMessage? {
        val mc = MifareClassic.get(tag) ?: return null
        return try {
            mc.connect()
            val bytes = readNdefBytes(mc)
            mc.close()
            bytes?.let(::parseNdefTlv)
        } catch (_: Exception) {
            runCatching { mc.close() }
            null
        }
    }

    private fun readNdefBytes(mc: MifareClassic): ByteArray? {
        val result = mutableListOf<Byte>()
        val startSector = if (mc.sectorCount > 1) 1 else 0
        for (sector in startSector until mc.sectorCount) {
            val authed = defaultKeys.any { key ->
                runCatching { mc.authenticateSectorWithKeyA(sector, key) }.getOrDefault(false)
            }
            if (!authed) continue
            val first = mc.sectorToBlock(sector)
            val count = mc.getBlockCountInSector(sector)
            for (block in first until first + count - 1) {
                runCatching { result.addAll(mc.readBlock(block).toList()) }
            }
        }
        return if (result.isEmpty()) null else result.toByteArray()
    }

    private fun parseNdefTlv(data: ByteArray): NdefMessage? {
        var i = 0
        while (i < data.size) {
            when (val type = data[i++].toInt() and 0xFF) {
                0x00 -> continue
                0xFE -> return null
                0x03 -> {
                    if (i >= data.size) return null
                    val len = if ((data[i].toInt() and 0xFF) == 0xFF) {
                        if (i + 2 >= data.size) return null
                        val v = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i + 2].toInt() and 0xFF)
                        i += 3; v
                    } else {
                        data[i++].toInt() and 0xFF
                    }
                    if (i + len > data.size) return null
                    return runCatching { NdefMessage(data.sliceArray(i until i + len)) }.getOrNull()
                }
                else -> {
                    if (i >= data.size) return null
                    i += 1 + (data[i].toInt() and 0xFF)
                }
            }
        }
        return null
    }
}
