package com.sphereon.openid.oid4vc.common.impl

import com.sphereon.openid.oid4vc.common.QrCodeOptions
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.InflaterInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmQrCodeServiceTest {
    @Test
    fun `generates a valid rgba png without awt rendering`() {
        val dataUri = JvmQrCodeService().generateDataUri(
            "openid4vp://?request_uri=https%3A%2F%2Fverifier.example%2Frequest%2F123",
            QrCodeOptions(size = 300, colorDark = "#112233", colorLight = "#fefefe"),
        )

        assertTrue(dataUri.startsWith("data:image/png;base64,"))
        val png = Base64.getDecoder().decode(dataUri.substringAfter(','))
        assertTrue(png.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE))

        var offset = 8
        var width = 0
        var height = 0
        val imageData = mutableListOf<ByteArray>()
        while (offset < png.size) {
            val length = png.readInt(offset)
            val type = png.copyOfRange(offset + 4, offset + 8)
            val data = png.copyOfRange(offset + 8, offset + 8 + length)
            val expectedCrc = png.readInt(offset + 8 + length).toLong() and 0xFFFF_FFFFL
            val actualCrc = CRC32().apply { update(type); update(data) }.value
            assertEquals(expectedCrc, actualCrc)
            when (type.decodeToString()) {
                "IHDR" -> {
                    width = data.readInt(0)
                    height = data.readInt(4)
                    assertEquals(6, data[9].toInt())
                }
                "IDAT" -> imageData += data
                "IEND" -> break
            }
            offset += 12 + length
        }

        assertTrue(width > 1)
        assertEquals(width, height)
        val compressed = ByteArray(imageData.sumOf { it.size })
        var compressedOffset = 0
        imageData.forEach { chunk ->
            chunk.copyInto(compressed, compressedOffset)
            compressedOffset += chunk.size
        }
        val inflated = InflaterInputStream(compressed.inputStream()).readBytes()
        assertEquals(height * (1 + width * 4), inflated.size)
        assertTrue(inflated.indices.any { index ->
            index + 2 < inflated.size &&
                inflated[index].toInt() and 0xFF == 0x11 &&
                inflated[index + 1].toInt() and 0xFF == 0x22 &&
                inflated[index + 2].toInt() and 0xFF == 0x33
        })
    }

    private fun ByteArray.readInt(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    }
}
