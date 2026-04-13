package com.sphereon.conf.theme.core.palette

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
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
data class HctColor(
    val hue: Double,
    val chroma: Double,
    val tone: Double
) {
    companion object {
        /**
         * Create an HCT color from an sRGB hex string (e.g. "#6750A4").
         */
        fun fromHex(hex: String): HctColor {
            val argb = hexToArgb(hex)
            return fromArgb(argb)
        }

        /**
         * Create an HCT color from an ARGB int.
         */
        fun fromArgb(argb: Int): HctColor {
            val r = ((argb shr 16) and 0xFF) / 255.0
            val g = ((argb shr 8) and 0xFF) / 255.0
            val b = (argb and 0xFF) / 255.0

            val linR = srgbToLinear(r)
            val linG = srgbToLinear(g)
            val linB = srgbToLinear(b)

            // sRGB -> XYZ (D65)
            val x = 0.4124564 * linR + 0.3575761 * linG + 0.1804375 * linB
            val y = 0.2126729 * linR + 0.7151522 * linG + 0.0721750 * linB
            val z = 0.0193339 * linR + 0.1191920 * linG + 0.9503041 * linB

            // XYZ -> CIELAB
            val xn = 0.95047
            val yn = 1.0
            val zn = 1.08883

            val fx = labF(x / xn)
            val fy = labF(y / yn)
            val fz = labF(z / zn)

            val lStar = 116.0 * fy - 16.0
            val a = 500.0 * (fx - fy)
            val bLab = 200.0 * (fy - fz)

            val chromaVal = sqrt(a * a + bLab * bLab)
            var hueVal = atan2(bLab, a) * 180.0 / PI
            if (hueVal < 0) hueVal += 360.0

            return HctColor(hue = hueVal, chroma = chromaVal, tone = lStar)
        }

        /**
         * Convert HCT back to an ARGB int.
         * Uses CIELAB with the given hue, chroma, and tone (L*).
         */
        fun toArgb(hue: Double, chroma: Double, tone: Double): Int {
            val hueRad = hue * PI / 180.0
            val a = chroma * cos(hueRad)
            val b = chroma * sin(hueRad)

            val fy = (tone + 16.0) / 116.0
            val fx = a / 500.0 + fy
            val fz = fy - b / 200.0

            val xn = 0.95047
            val yn = 1.0
            val zn = 1.08883

            val x = xn * labFInv(fx)
            val y = yn * labFInv(fy)
            val z = zn * labFInv(fz)

            // XYZ -> linear sRGB
            val linR = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
            val linG = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
            val linB = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z

            val rr = linearToSrgb(linR)
            val gg = linearToSrgb(linG)
            val bb = linearToSrgb(linB)

            val ri = clampComponent(rr)
            val gi = clampComponent(gg)
            val bi = clampComponent(bb)

            return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        /**
         * Convert an ARGB int to a hex string like "#RRGGBB".
         */
        fun argbToHex(argb: Int): String {
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            return "#${r.hex()}${g.hex()}${b.hex()}"
        }

        /**
         * Parse a hex color string to ARGB int.
         */
        fun hexToArgb(hex: String): Int {
            val clean = hex.removePrefix("#")
            val value = clean.toLong(16).toInt()
            return when (clean.length) {
                6 -> (0xFF shl 24) or value
                8 -> value
                else -> (0xFF shl 24) or value
            }
        }

        private fun srgbToLinear(c: Double): Double =
            if (c <= 0.04045) c / 12.92
            else ((c + 0.055) / 1.055).pow(2.4)

        private fun linearToSrgb(c: Double): Double =
            if (c <= 0.0031308) 12.92 * c
            else 1.055 * c.pow(1.0 / 2.4) - 0.055

        private fun labF(t: Double): Double {
            val delta = 6.0 / 29.0
            return if (t > delta * delta * delta) cbrt(t)
            else t / (3.0 * delta * delta) + 4.0 / 29.0
        }

        private fun labFInv(t: Double): Double {
            val delta = 6.0 / 29.0
            return if (t > delta) t * t * t
            else 3.0 * delta * delta * (t - 4.0 / 29.0)
        }

        private fun clampComponent(v: Double): Int =
            (v * 255.0).roundToInt().coerceIn(0, 255)

        private fun Int.hex(): String = this.toString(16).padStart(2, '0').uppercase()
    }
}
