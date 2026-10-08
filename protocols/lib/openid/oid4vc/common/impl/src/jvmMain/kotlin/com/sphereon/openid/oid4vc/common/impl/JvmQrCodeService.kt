/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.openid.oid4vc.common.impl

import com.sphereon.core.api.encodeToBase64
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vc.common.QrCodeOptions
import com.sphereon.openid.oid4vc.common.QrCodeService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import qrcode.QRCode
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/**
 * JVM implementation of QR code generation using qrcode-kotlin.
 *
 * Generates QR codes as PNG data URIs in the format:
 * `data:image/png;base64,...`
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<QrCodeService>(), replaces = [FallbackQrCodeService::class])
class JvmQrCodeService : QrCodeService {
    override fun generateDataUri(
        content: String,
        options: QrCodeOptions,
    ): String =
        try {
            val darkColor = parseColor(options.colorDark)
            val lightColor = parseColor(options.colorLight)
            val cellSize = maxOf(1, minOf(25, options.size / 40))

            val qrCode =
                QRCode
                    .ofSquares()
                    .withSize(cellSize)
                    .withColor(darkColor)
                    .withBackgroundColor(lightColor)
                    // qrcode-kotlin's square shape defaults to a non-zero inner spacing, drawing
                    // each module as a smaller square with a white border inside its cell. That
                    // leaves visible gaps between dark modules which many camera scanners cannot
                    // reconstruct into a valid matrix (and makes the code look washed out). Force
                    // 0 so modules are contiguous. The 4-module quiet zone is already part of the
                    // rendered matrix, so no extra `withMargin` is needed.
                    .withInnerSpacing(0)
                    .build(content)
            val pngBytes = encodePng(qrCode, cellSize, darkColor, lightColor)

            val base64 = pngBytes.encodeToBase64()
            "data:image/png;base64,$base64"
        } catch (_: Exception) {
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
        }

    /**
     * Encodes the QR matrix directly instead of using qrcode-kotlin's JVM renderer. The latter
     * creates a java.awt BufferedImage, which attempts to load libawt_headless at runtime and can
     * terminate a GraalVM native executable before an exception can be caught.
     */
    private fun encodePng(
        qrCode: QRCode,
        cellSize: Int,
        darkColor: Int,
        lightColor: Int,
    ): ByteArray {
        val matrix = qrCode.rawData
        val size = matrix.size * cellSize
        val scanlines = ByteArrayOutputStream(size * (size * 4 + 1))
        repeat(size) { y ->
            scanlines.write(0) // PNG filter type: None
            val row = matrix[y / cellSize]
            repeat(size) { x ->
                val color = if (row[x / cellSize].dark) darkColor else lightColor
                scanlines.write((color ushr 16) and 0xFF)
                scanlines.write((color ushr 8) and 0xFF)
                scanlines.write(color and 0xFF)
                scanlines.write((color ushr 24) and 0xFF)
            }
        }

        val compressed = ByteArrayOutputStream()
        DeflaterOutputStream(compressed).use { it.write(scanlines.toByteArray()) }

        return ByteArrayOutputStream().also { png ->
            png.write(PNG_SIGNATURE)
            png.writeChunk("IHDR", ByteArrayOutputStream(13).also { header ->
                DataOutputStream(header).use { data ->
                    data.writeInt(size)
                    data.writeInt(size)
                    data.writeByte(8) // bit depth
                    data.writeByte(6) // RGBA
                    data.writeByte(0) // compression
                    data.writeByte(0) // filter
                    data.writeByte(0) // no interlace
                }
            }.toByteArray())
            png.writeChunk("IDAT", compressed.toByteArray())
            png.writeChunk("IEND", byteArrayOf())
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeChunk(type: String, data: ByteArray) {
        val typeBytes = type.encodeToByteArray()
        DataOutputStream(this).writeInt(data.size)
        write(typeBytes)
        write(data)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }
        DataOutputStream(this).writeInt(crc.value.toInt())
    }

    private fun parseColor(cssColor: String): Int =
        try {
            val hex = cssColor.removePrefix("#")
            when (hex.length) {
                3 -> {
                    val r = hex[0].toString().repeat(2).toInt(16)
                    val g = hex[1].toString().repeat(2).toInt(16)
                    val b = hex[2].toString().repeat(2).toInt(16)
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }

                6 -> {
                    val rgb = hex.toInt(16)
                    (0xFF shl 24) or rgb
                }

                8 -> {
                    hex.toLong(16).toInt()
                }

                else -> {
                    0xFF000000.toInt()
                }
            }
        } catch (_: Exception) {
            0xFF000000.toInt()
        }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    }
}

actual fun createPlatformQrCodeService(): QrCodeService = JvmQrCodeService()
