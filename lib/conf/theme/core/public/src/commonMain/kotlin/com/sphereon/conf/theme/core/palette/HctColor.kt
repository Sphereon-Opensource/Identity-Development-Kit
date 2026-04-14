/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.conf.theme.core.palette

import com.sphereon.core.compat.JsExportCompat
import kotlin.jvm.JvmStatic
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * HCT (Hue-Chroma-Tone) color representation used by Material Design 3.
 * Combines CIELAB hue and chroma with CIE L* lightness (tone).
 *
 * This is a simplified implementation sufficient for palette generation.
 */
@JsExportCompat
data class HctColor(
    val hue: Double,
    val chroma: Double,
    val tone: Double,
) {
    companion object {
        // Byte/channel extraction constants
        private const val BYTE_MASK = 0xFF
        private const val SHIFT_RED = 16
        private const val SHIFT_GREEN = 8
        private const val SHIFT_ALPHA = 24
        private const val CHANNEL_MAX = 255.0
        private const val CHANNEL_MAX_INT = 255
        private const val HEX_RADIX = 16
        private const val HEX_PAD_LENGTH = 2
        private const val HEX_RGB_LENGTH = 6
        private const val HEX_ARGB_LENGTH = 8

        // sRGB <-> XYZ D65 conversion matrix coefficients
        private const val SRGB_TO_XYZ_R_X = 0.4124564
        private const val SRGB_TO_XYZ_R_Y = 0.2126729
        private const val SRGB_TO_XYZ_R_Z = 0.0193339
        private const val SRGB_TO_XYZ_G_X = 0.3575761
        private const val SRGB_TO_XYZ_G_Y = 0.7151522
        private const val SRGB_TO_XYZ_G_Z = 0.1191920
        private const val SRGB_TO_XYZ_B_X = 0.1804375
        private const val SRGB_TO_XYZ_B_Y = 0.0721750
        private const val SRGB_TO_XYZ_B_Z = 0.9503041

        private const val XYZ_TO_SRGB_R_X = 3.2404542
        private const val XYZ_TO_SRGB_R_Y = -1.5371385
        private const val XYZ_TO_SRGB_R_Z = -0.4985314
        private const val XYZ_TO_SRGB_G_X = -0.9692660
        private const val XYZ_TO_SRGB_G_Y = 1.8760108
        private const val XYZ_TO_SRGB_G_Z = 0.0415560
        private const val XYZ_TO_SRGB_B_X = 0.0556434
        private const val XYZ_TO_SRGB_B_Y = -0.2040259
        private const val XYZ_TO_SRGB_B_Z = 1.0572252

        // D65 illuminant reference white point
        private const val D65_XN = 0.95047
        private const val D65_ZN = 1.08883

        // CIELAB conversion coefficients
        private const val LAB_L_SCALE = 116.0
        private const val LAB_L_OFFSET = 16.0
        private const val LAB_A_SCALE = 500.0
        private const val LAB_B_SCALE = 200.0
        private const val LAB_DELTA_NUM = 6.0
        private const val LAB_DELTA_DEN = 29.0
        private const val LAB_INV_SCALE = 3.0
        private const val LAB_INV_OFFSET_NUM = 4.0

        // Angle conversion
        private const val DEGREES_HALF_CIRCLE = 180.0
        private const val DEGREES_FULL_CIRCLE = 360.0

        // sRGB linearization thresholds
        private const val SRGB_LINEAR_THRESHOLD = 0.04045
        private const val SRGB_LINEAR_DIVISOR = 12.92
        private const val SRGB_LINEAR_OFFSET = 0.055
        private const val SRGB_LINEAR_SCALE = 1.055
        private const val SRGB_GAMMA = 2.4
        private const val SRGB_INV_THRESHOLD = 0.0031308

        /**
         * Create an HCT color from an sRGB hex string (e.g. "#6750A4").
         */
        @JvmStatic
        fun fromHex(hex: String): HctColor {
            val argb = hexToArgb(hex)
            return fromArgb(argb)
        }

        /**
         * Create an HCT color from an ARGB int.
         */
        @JvmStatic
        fun fromArgb(argb: Int): HctColor {
            val r = ((argb shr SHIFT_RED) and BYTE_MASK) / CHANNEL_MAX
            val g = ((argb shr SHIFT_GREEN) and BYTE_MASK) / CHANNEL_MAX
            val b = (argb and BYTE_MASK) / CHANNEL_MAX

            val linR = srgbToLinear(r)
            val linG = srgbToLinear(g)
            val linB = srgbToLinear(b)

            // sRGB -> XYZ (D65)
            val x = SRGB_TO_XYZ_R_X * linR + SRGB_TO_XYZ_G_X * linG + SRGB_TO_XYZ_B_X * linB
            val y = SRGB_TO_XYZ_R_Y * linR + SRGB_TO_XYZ_G_Y * linG + SRGB_TO_XYZ_B_Y * linB
            val z = SRGB_TO_XYZ_R_Z * linR + SRGB_TO_XYZ_G_Z * linG + SRGB_TO_XYZ_B_Z * linB

            // XYZ -> CIELAB
            val fx = labF(x / D65_XN)
            val fy = labF(y)
            val fz = labF(z / D65_ZN)

            val lStar = LAB_L_SCALE * fy - LAB_L_OFFSET
            val a = LAB_A_SCALE * (fx - fy)
            val bLab = LAB_B_SCALE * (fy - fz)

            val chromaVal = sqrt(a * a + bLab * bLab)
            var hueVal = atan2(bLab, a) * DEGREES_HALF_CIRCLE / PI
            if (hueVal < 0) {
                hueVal += DEGREES_FULL_CIRCLE
            }

            return HctColor(hue = hueVal, chroma = chromaVal, tone = lStar)
        }

        /**
         * Convert HCT back to an ARGB int.
         * Uses CIELAB with the given hue, chroma, and tone (L*).
         */
        @JvmStatic
        fun toArgb(
            hue: Double,
            chroma: Double,
            tone: Double,
        ): Int {
            val hueRad = hue * PI / DEGREES_HALF_CIRCLE
            val a = chroma * cos(hueRad)
            val b = chroma * sin(hueRad)

            val fy = (tone + LAB_L_OFFSET) / LAB_L_SCALE
            val fx = a / LAB_A_SCALE + fy
            val fz = fy - b / LAB_B_SCALE

            val x = D65_XN * labFInv(fx)
            val y = labFInv(fy)
            val z = D65_ZN * labFInv(fz)

            // XYZ -> linear sRGB
            val linR = XYZ_TO_SRGB_R_X * x + XYZ_TO_SRGB_R_Y * y + XYZ_TO_SRGB_R_Z * z
            val linG = XYZ_TO_SRGB_G_X * x + XYZ_TO_SRGB_G_Y * y + XYZ_TO_SRGB_G_Z * z
            val linB = XYZ_TO_SRGB_B_X * x + XYZ_TO_SRGB_B_Y * y + XYZ_TO_SRGB_B_Z * z

            val rr = linearToSrgb(linR)
            val gg = linearToSrgb(linG)
            val bb = linearToSrgb(linB)

            val ri = clampComponent(rr)
            val gi = clampComponent(gg)
            val bi = clampComponent(bb)

            return (BYTE_MASK shl SHIFT_ALPHA) or (ri shl SHIFT_RED) or (gi shl SHIFT_GREEN) or bi
        }

        /**
         * Convert an ARGB int to a hex string like "#RRGGBB".
         */
        @JvmStatic
        fun argbToHex(argb: Int): String {
            val r = (argb shr SHIFT_RED) and BYTE_MASK
            val g = (argb shr SHIFT_GREEN) and BYTE_MASK
            val b = argb and BYTE_MASK
            return "#${r.hex()}${g.hex()}${b.hex()}"
        }

        /**
         * Parse a hex color string to ARGB int.
         */
        @JvmStatic
        fun hexToArgb(hex: String): Int {
            val clean = hex.removePrefix("#")
            val value = clean.toLong(HEX_RADIX).toInt()
            return when (clean.length) {
                HEX_RGB_LENGTH -> (BYTE_MASK shl SHIFT_ALPHA) or value
                HEX_ARGB_LENGTH -> value
                else -> (BYTE_MASK shl SHIFT_ALPHA) or value
            }
        }

        private fun srgbToLinear(c: Double): Double =
            if (c <= SRGB_LINEAR_THRESHOLD) {
                c / SRGB_LINEAR_DIVISOR
            } else {
                ((c + SRGB_LINEAR_OFFSET) / SRGB_LINEAR_SCALE).pow(SRGB_GAMMA)
            }

        private fun linearToSrgb(c: Double): Double =
            if (c <= SRGB_INV_THRESHOLD) {
                SRGB_LINEAR_DIVISOR * c
            } else {
                SRGB_LINEAR_SCALE * c.pow(1.0 / SRGB_GAMMA) - SRGB_LINEAR_OFFSET
            }

        private fun labF(t: Double): Double {
            val delta = LAB_DELTA_NUM / LAB_DELTA_DEN
            return if (t > delta * delta * delta) {
                cbrt(t)
            } else {
                t / (LAB_INV_SCALE * delta * delta) + LAB_INV_OFFSET_NUM / LAB_DELTA_DEN
            }
        }

        private fun labFInv(t: Double): Double {
            val delta = LAB_DELTA_NUM / LAB_DELTA_DEN
            return if (t > delta) {
                t * t * t
            } else {
                LAB_INV_SCALE * delta * delta * (t - LAB_INV_OFFSET_NUM / LAB_DELTA_DEN)
            }
        }

        private fun clampComponent(v: Double): Int = (v * CHANNEL_MAX).roundToInt().coerceIn(0, CHANNEL_MAX_INT)

        private fun Int.hex(): String = this.toString(HEX_RADIX).padStart(HEX_PAD_LENGTH, '0').uppercase()
    }
}
