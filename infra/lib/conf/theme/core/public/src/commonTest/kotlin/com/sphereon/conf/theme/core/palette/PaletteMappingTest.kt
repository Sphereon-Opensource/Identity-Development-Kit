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

import kotlin.test.Test
import kotlin.test.assertTrue

class PaletteMappingTest {
    @Test
    fun defaultLightMappingShouldCoverAllCoreM3Roles() {
        val expectedKeys =
            listOf(
                "color.primary",
                "color.onPrimary",
                "color.primaryContainer",
                "color.onPrimaryContainer",
                "color.secondary",
                "color.onSecondary",
                "color.secondaryContainer",
                "color.onSecondaryContainer",
                "color.error",
                "color.onError",
                "color.errorContainer",
                "color.onErrorContainer",
                "color.surface",
                "color.onSurface",
                "color.surfaceVariant",
                "color.onSurfaceVariant",
                "color.background",
                "color.onBackground",
                "color.outline",
                "color.outlineVariant",
            )
        for (key in expectedKeys) {
            assertTrue(key in DefaultPaletteMapping.LIGHT, "Missing light mapping for: $key")
        }
    }

    @Test
    fun defaultDarkMappingShouldCoverAllCoreM3Roles() {
        val expectedKeys =
            listOf(
                "color.primary",
                "color.onPrimary",
                "color.primaryContainer",
                "color.onPrimaryContainer",
                "color.secondary",
                "color.onSecondary",
                "color.secondaryContainer",
                "color.onSecondaryContainer",
                "color.error",
                "color.onError",
                "color.errorContainer",
                "color.onErrorContainer",
                "color.surface",
                "color.onSurface",
                "color.surfaceVariant",
                "color.onSurfaceVariant",
                "color.background",
                "color.onBackground",
                "color.outline",
                "color.outlineVariant",
            )
        for (key in expectedKeys) {
            assertTrue(key in DefaultPaletteMapping.DARK, "Missing dark mapping for: $key")
        }
    }

    @Test
    fun allMappingRefsShouldUseValidStops() {
        val validStops = PaletteScale.STOPS.toSet()
        for ((key, ref) in DefaultPaletteMapping.LIGHT) {
            assertTrue(
                ref.stop in validStops,
                "Light mapping '$key' uses invalid stop ${ref.stop}",
            )
        }
        for ((key, ref) in DefaultPaletteMapping.DARK) {
            assertTrue(
                ref.stop in validStops,
                "Dark mapping '$key' uses invalid stop ${ref.stop}",
            )
        }
    }

    @Test
    fun allMappingRefsShouldUseValidScaleNames() {
        val validScales = setOf("brand", "secondary", "neutral", "error", "success", "warning", "info", "pending")
        for ((key, ref) in DefaultPaletteMapping.LIGHT) {
            assertTrue(
                ref.scale in validScales,
                "Light mapping '$key' uses invalid scale '${ref.scale}'",
            )
        }
        for ((key, ref) in DefaultPaletteMapping.DARK) {
            assertTrue(
                ref.scale in validScales,
                "Dark mapping '$key' uses invalid scale '${ref.scale}'",
            )
        }
    }

    @Test
    fun lightAndDarkMappingsShouldHaveSameKeys() {
        val lightKeys = DefaultPaletteMapping.LIGHT.keys
        val darkKeys = DefaultPaletteMapping.DARK.keys
        assertTrue(
            lightKeys == darkKeys,
            "Light and dark mappings should have the same token keys. " +
                "Light-only: ${lightKeys - darkKeys}, Dark-only: ${darkKeys - lightKeys}",
        )
    }

    @Test
    fun defaultPaletteMappingShouldMatchLightAndDark() {
        val mapping = DefaultPaletteMapping.DEFAULT
        assertTrue(mapping.light == DefaultPaletteMapping.LIGHT)
        assertTrue(mapping.dark == DefaultPaletteMapping.DARK)
    }
}
