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
}
