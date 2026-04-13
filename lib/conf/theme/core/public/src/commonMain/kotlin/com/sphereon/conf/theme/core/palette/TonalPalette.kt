package com.sphereon.conf.theme.core.palette

import com.sphereon.conf.theme.core.model.TonalPaletteResult

/**
 * Generates a 13-tone tonal palette from a given hue and chroma,
 * following Material Design 3 tone stops.
 */
class TonalPalette(
    val hue: Double,
    val chroma: Double
) {
    companion object {
        /** Standard M3 tone stops */
        val TONE_STOPS = intArrayOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 99, 100)

        /**
         * Create a tonal palette from a seed color in hex.
         */
        fun fromHex(hex: String): TonalPalette {
            val hct = HctColor.fromHex(hex)
            return TonalPalette(hct.hue, hct.chroma)
        }
    }

    /**
     * Get the hex color at the specified tone (0–100).
     *
     * Per M3 spec, tone 0 is always pure black and tone 100 is always pure white,
     * regardless of hue and chroma (the sRGB gamut has zero chroma at these extremes).
     */
    fun tone(tone: Int): String {
        if (tone <= 0) return "#000000"
        if (tone >= 100) return "#FFFFFF"
        val argb = HctColor.toArgb(hue, chroma, tone.toDouble())
        return HctColor.argbToHex(argb)
    }

    /**
     * Generate the full tonal palette result with all standard M3 tone stops.
     */
    fun toResult(): TonalPaletteResult = TonalPaletteResult(
        tone0 = tone(0),
        tone10 = tone(10),
        tone20 = tone(20),
        tone30 = tone(30),
        tone40 = tone(40),
        tone50 = tone(50),
        tone60 = tone(60),
        tone70 = tone(70),
        tone80 = tone(80),
        tone90 = tone(90),
        tone95 = tone(95),
        tone99 = tone(99),
        tone100 = tone(100)
    )
}
