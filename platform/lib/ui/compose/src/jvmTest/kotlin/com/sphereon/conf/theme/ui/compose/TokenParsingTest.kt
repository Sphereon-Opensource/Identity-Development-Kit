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

package com.sphereon.conf.theme.ui.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenParsingTest {
    @Test
    fun parseColorHex6() {
        val color = parseColor("#FF5722")
        assertEquals(0xFF, (color.red * 255).toInt())
        assertEquals(0x57, (color.green * 255).toInt())
        assertEquals(0x22, (color.blue * 255).toInt())
    }

    @Test
    fun parseColorHex3() {
        val color = parseColor("#F00")
        assertEquals(0xFF, (color.red * 255).toInt())
        assertEquals(0x00, (color.green * 255).toInt())
        assertEquals(0x00, (color.blue * 255).toInt())
    }

    @Test
    fun parseColorHex8() {
        val color = parseColor("#FF572280")
        assertEquals(0xFF, (color.red * 255).toInt())
        assertEquals(0x57, (color.green * 255).toInt())
        assertEquals(0x22, (color.blue * 255).toInt())
        assertEquals(0x80, (color.alpha * 255).toInt())
    }

    @Test
    fun parseColorTransparent() {
        val color = parseColor("transparent")
        assertEquals(Color.Transparent, color)
    }

    @Test
    fun parseColorInvalidReturnsFallback() {
        val fallback = Color.Red
        val color = parseColor("not-a-color", fallback)
        assertEquals(fallback, color)
    }

    @Test
    fun parseDpPixels() {
        assertEquals(16.dp, parseDp("16px"))
    }

    @Test
    fun parseDpDp() {
        assertEquals(24.dp, parseDp("24dp"))
    }

    @Test
    fun parseDpPlainNumber() {
        assertEquals(12.dp, parseDp("12"))
    }

    @Test
    fun parseDpInvalidReturnsFallback() {
        assertEquals(8.dp, parseDp("abc", 8.dp))
    }

    @Test
    fun parseBrushReadsAVerticalGradient() {
        assertNotNull(parseBrush("linear-gradient(180deg, #854EE9 0%, #4F16B7 100%)"))
    }

    @Test
    fun parseBrushReadsTheOtherAxisAlignedDirections() {
        assertNotNull(parseBrush("linear-gradient(90deg, #854EE9 0%, #4F16B7 100%)"))
        assertNotNull(parseBrush("linear-gradient(to top, #854EE9, #4F16B7)"))
        assertNotNull(parseBrush("linear-gradient(to left, #854EE9, #4F16B7)"))
    }

    @Test
    fun parseBrushDefaultsToTopToBottomWhenNoAngleIsGiven() {
        assertNotNull(parseBrush("linear-gradient(#854EE9, #4F16B7)"))
    }

    @Test
    fun parseBrushRejectsWhatItCannotRenderFaithfully() {
        // A non axis-aligned angle cannot be expressed without the drawn size, so it is refused
        // rather than rendered at the wrong angle.
        assertNull(parseBrush("linear-gradient(37deg, #854EE9 0%, #4F16B7 100%)"))
        // Stops that are not plain colours, and gradient kinds with no Brush equivalent here.
        assertNull(parseBrush("linear-gradient(180deg, color-mix(in srgb, #854EE9 50%, #000) 0%, #4F16B7 100%)"))
        assertNull(parseBrush("radial-gradient(1200px 600px at 100% -10%, #854EE9, transparent 60%)"))
        assertNull(parseBrush("#854EE9"))
        assertNull(parseBrush("linear-gradient(180deg, #854EE9 0%)"))
    }

    @Test
    fun tokenFillPrefersTheBrushAndFallsBackToAFlatColor() {
        val gradient = tokenFill("linear-gradient(180deg, #854EE9 0%, #4F16B7 100%)", Color.Red)
        assertNotNull(gradient.brush)
        assertEquals(Color.Transparent, gradient.containerColor)

        val flat = tokenFill("#854EE9", Color.Red)
        assertNull(flat.brush)
        assertEquals(parseColor("#854EE9"), flat.containerColor)

        val unreadable = tokenFill("not-a-color", Color.Red)
        assertNull(unreadable.brush)
        assertEquals(Color.Red, unreadable.containerColor)
    }

    @Test
    fun anUnrenderableGradientWarnsOnceThroughTheInjectedSink() {
        val seen = mutableListOf<String>()
        TokenDiagnostics.resetWarnings()
        TokenDiagnostics.setWarningSink { message -> seen += message }
        try {
            val unsupported = "linear-gradient(37deg, #854EE9 0%, #4F16B7 100%)"
            tokenFill(unsupported, Color.Red)
            tokenFill(unsupported, Color.Red)
            tokenFill(unsupported, Color.Red)
            assertEquals(1, seen.size, "A gradient the renderer cannot honour should warn once per value, not per call")
            assertTrue(seen.single().contains("37deg"), "The warning should name the value that could not be rendered")

            // A plain colour failing to parse as a gradient is the normal case, not a problem.
            tokenFill("#854EE9", Color.Red)
            tokenFill("transparent", Color.Red)
            assertEquals(1, seen.size, "Flat colour values must not warn")

            // A second, different unsupported value is its own warning.
            tokenFill("radial-gradient(1200px 600px at 100% -10%, #854EE9, transparent 60%)", Color.Red)
            assertEquals(2, seen.size)
        } finally {
            TokenDiagnostics.setWarningSink(null)
            TokenDiagnostics.resetWarnings()
        }
    }

    @Test
    fun nothingIsEmittedWhenNoSinkIsInstalled() {
        TokenDiagnostics.resetWarnings()
        TokenDiagnostics.setWarningSink(null)
        // The library must never print on its own; with no sink this is simply silent.
        assertNull(tokenFill("linear-gradient(37deg, #854EE9 0%, #4F16B7 100%)", Color.Red).brush)
    }
}
