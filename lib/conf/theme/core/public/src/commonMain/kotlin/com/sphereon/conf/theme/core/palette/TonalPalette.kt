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

import com.sphereon.conf.theme.core.model.TonalPaletteResult

/**
 * Generates a 13-tone tonal palette from a given hue and chroma,
 * following Material Design 3 tone stops.
 */
class TonalPalette(
    val hue: Double,
    val chroma: Double,
) {
    /**
     * Get the hex color at the specified tone (0-100).
     *
     * Per M3 spec, tone 0 is always pure black and tone 100 is always pure white,
     * regardless of hue and chroma (the sRGB gamut has zero chroma at these extremes).
     */
    fun tone(tone: Int): String {
        if (tone <= 0) {
            return "#000000"
        }
        if (tone >= TONE_100) {
            return "#FFFFFF"
        }
        val argb = HctColor.toArgb(hue, chroma, tone.toDouble())
        return HctColor.argbToHex(argb)
    }

    /**
     * Generate the full tonal palette result with all standard M3 tone stops.
     */
    fun toResult(): TonalPaletteResult =
        TonalPaletteResult(
            tone0 = tone(0),
            tone10 = tone(TONE_10),
            tone20 = tone(TONE_20),
            tone30 = tone(TONE_30),
            tone40 = tone(TONE_40),
            tone50 = tone(TONE_50),
            tone60 = tone(TONE_60),
            tone70 = tone(TONE_70),
            tone80 = tone(TONE_80),
            tone90 = tone(TONE_90),
            tone95 = tone(TONE_95),
            tone99 = tone(TONE_99),
            tone100 = tone(TONE_100),
        )

    companion object {
        private const val TONE_10 = 10
        private const val TONE_20 = 20
        private const val TONE_30 = 30
        private const val TONE_40 = 40
        private const val TONE_50 = 50
        private const val TONE_60 = 60
        private const val TONE_70 = 70
        private const val TONE_80 = 80
        private const val TONE_90 = 90
        private const val TONE_95 = 95
        private const val TONE_99 = 99
        private const val TONE_100 = 100

        /** Standard M3 tone stops */
        val TONE_STOPS = intArrayOf(0, TONE_10, TONE_20, TONE_30, TONE_40, TONE_50, TONE_60, TONE_70, TONE_80, TONE_90, TONE_95, TONE_99, TONE_100)

        /**
         * Create a tonal palette from a seed color in hex.
         */
        fun fromHex(hex: String): TonalPalette {
            val hct = HctColor.fromHex(hex)
            return TonalPalette(hct.hue, hct.chroma)
        }
    }
}
