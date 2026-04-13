/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.universal.impl

import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.universal.QrCodeOptions
import com.sphereon.openid.oid4vp.universal.QrCodeService
import dev.zacsweers.metro.Inject
import qrcode.QRCode
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import java.util.Base64

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

    override fun generateDataUri(content: String, options: QrCodeOptions): String {
        return try {
            // Parse colors
            val darkColor = parseColor(options.colorDark)
            val lightColor = parseColor(options.colorLight)

            // Calculate cell size to achieve target image size
            // QR codes typically have ~33-49 cells per side depending on content
            // We target a cell size that produces approximately the requested image size
            // Using a smaller cell size (10 pixels) as default for reasonable file size
            val cellSize = maxOf(1, minOf(25, options.size / 40))

            // Generate QR code and render to PNG bytes
            val pngBytes = QRCode.ofSquares()
                .withSize(cellSize)
                .withColor(darkColor)
                .withBackgroundColor(lightColor)
                .build(content)
                .renderToBytes()

            // Convert to Base64 data URI
            val base64 = Base64.getEncoder().encodeToString(pngBytes)
            "data:image/png;base64,$base64"
        } catch (e: Exception) {
            // Fallback to placeholder on error
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
        }
    }

    private fun parseColor(cssColor: String): Int {
        return try {
            val hex = cssColor.removePrefix("#")
            when (hex.length) {
                3 -> {
                    // Short form: #RGB -> #RRGGBB
                    val r = hex[0].toString().repeat(2).toInt(16)
                    val g = hex[1].toString().repeat(2).toInt(16)
                    val b = hex[2].toString().repeat(2).toInt(16)
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
                6 -> {
                    // Standard form: #RRGGBB
                    val rgb = hex.toInt(16)
                    (0xFF shl 24) or rgb
                }
                8 -> {
                    // With alpha: #AARRGGBB
                    hex.toLong(16).toInt()
                }
                else -> 0xFF000000.toInt() // Default to black
            }
        } catch (e: Exception) {
            0xFF000000.toInt() // Default to black on parse error
        }
    }
}

/**
 * Platform-specific factory for QR code service.
 */
actual fun createPlatformQrCodeService(): QrCodeService = JvmQrCodeService()
