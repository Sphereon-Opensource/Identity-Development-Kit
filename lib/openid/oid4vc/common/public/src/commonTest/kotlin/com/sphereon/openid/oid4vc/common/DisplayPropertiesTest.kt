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

package com.sphereon.openid.oid4vc.common

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DisplayPropertiesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun fullRoundTrip() {
        val display =
            DisplayProperties(
                name = "University Degree",
                locale = "en-US",
                logo = LogoProperties(uri = "https://example.com/logo.png", altText = "University Logo"),
                description = "A degree credential",
                backgroundColor = "#12107c",
                backgroundImage = ImageProperties(uri = "https://example.com/bg.png"),
                textColor = "#FFFFFF",
            )
        val encoded = json.encodeToString(display)
        val decoded = json.decodeFromString<DisplayProperties>(encoded)
        assertEquals(display, decoded)
    }

    @Test
    fun minimalRoundTrip() {
        val display = DisplayProperties(name = "Credential")
        val encoded = json.encodeToString(display)
        val decoded = json.decodeFromString<DisplayProperties>(encoded)
        assertEquals(display, decoded)
        assertNull(decoded.locale)
        assertNull(decoded.logo)
        assertNull(decoded.description)
        assertNull(decoded.backgroundColor)
        assertNull(decoded.backgroundImage)
        assertNull(decoded.textColor)
    }

    @Test
    fun deserializesFromSpecJson() {
        val specJson =
            """
            {
                "name": "University Credential",
                "locale": "en-US",
                "logo": {
                    "uri": "https://university.example.edu/public/logo.png",
                    "alt_text": "a]square logo of a university"
                },
                "background_color": "#12107c",
                "text_color": "#FFFFFF"
            }
            """.trimIndent()
        val decoded = json.decodeFromString<DisplayProperties>(specJson)
        assertEquals("University Credential", decoded.name)
        assertEquals("en-US", decoded.locale)
        assertEquals("https://university.example.edu/public/logo.png", decoded.logo?.uri)
        assertEquals("a]square logo of a university", decoded.logo?.altText)
        assertEquals("#12107c", decoded.backgroundColor)
        assertEquals("#FFFFFF", decoded.textColor)
    }

    @Test
    fun logoPropertiesRoundTrip() {
        val logo = LogoProperties(uri = "https://example.com/logo.png", altText = "Alt text")
        val encoded = json.encodeToString(logo)
        val decoded = json.decodeFromString<LogoProperties>(encoded)
        assertEquals(logo, decoded)
    }

    @Test
    fun imagePropertiesRoundTrip() {
        val image = ImageProperties(uri = "https://example.com/bg.png")
        val encoded = json.encodeToString(image)
        val decoded = json.decodeFromString<ImageProperties>(encoded)
        assertEquals(image, decoded)
    }
}
