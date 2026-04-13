package com.sphereon.conf.theme.core.palette

import kotlin.test.Test
import kotlin.test.assertTrue

class PaletteMappingTest {

    @Test
    fun defaultLightMappingShouldCoverAllCoreM3Roles() {
        val expectedKeys = listOf(
            "color.primary", "color.onPrimary", "color.primaryContainer", "color.onPrimaryContainer",
            "color.secondary", "color.onSecondary", "color.secondaryContainer", "color.onSecondaryContainer",
            "color.error", "color.onError", "color.errorContainer", "color.onErrorContainer",
            "color.surface", "color.onSurface", "color.surfaceVariant", "color.onSurfaceVariant",
            "color.background", "color.onBackground",
            "color.outline", "color.outlineVariant",
        )
        for (key in expectedKeys) {
            assertTrue(key in DefaultPaletteMapping.LIGHT, "Missing light mapping for: $key")
        }
    }

    @Test
    fun defaultDarkMappingShouldCoverAllCoreM3Roles() {
        val expectedKeys = listOf(
            "color.primary", "color.onPrimary", "color.primaryContainer", "color.onPrimaryContainer",
            "color.secondary", "color.onSecondary", "color.secondaryContainer", "color.onSecondaryContainer",
            "color.error", "color.onError", "color.errorContainer", "color.onErrorContainer",
            "color.surface", "color.onSurface", "color.surfaceVariant", "color.onSurfaceVariant",
            "color.background", "color.onBackground",
            "color.outline", "color.outlineVariant",
        )
        for (key in expectedKeys) {
            assertTrue(key in DefaultPaletteMapping.DARK, "Missing dark mapping for: $key")
        }
    }

    @Test
    fun allMappingRefsShouldUseValidStops() {
        val validStops = PaletteScale.STOPS.toSet()
        for ((key, ref) in DefaultPaletteMapping.LIGHT) {
            assertTrue(ref.stop in validStops,
                "Light mapping '$key' uses invalid stop ${ref.stop}")
        }
        for ((key, ref) in DefaultPaletteMapping.DARK) {
            assertTrue(ref.stop in validStops,
                "Dark mapping '$key' uses invalid stop ${ref.stop}")
        }
    }

    @Test
    fun allMappingRefsShouldUseValidScaleNames() {
        val validScales = setOf("brand", "secondary", "neutral", "error", "success", "warning", "info", "pending")
        for ((key, ref) in DefaultPaletteMapping.LIGHT) {
            assertTrue(ref.scale in validScales,
                "Light mapping '$key' uses invalid scale '${ref.scale}'")
        }
        for ((key, ref) in DefaultPaletteMapping.DARK) {
            assertTrue(ref.scale in validScales,
                "Dark mapping '$key' uses invalid scale '${ref.scale}'")
        }
    }

    @Test
    fun lightAndDarkMappingsShouldHaveSameKeys() {
        val lightKeys = DefaultPaletteMapping.LIGHT.keys
        val darkKeys = DefaultPaletteMapping.DARK.keys
        assertTrue(lightKeys == darkKeys,
            "Light and dark mappings should have the same token keys. " +
                "Light-only: ${lightKeys - darkKeys}, Dark-only: ${darkKeys - lightKeys}")
    }

    @Test
    fun defaultPaletteMappingShouldMatchLightAndDark() {
        val mapping = DefaultPaletteMapping.DEFAULT
        assertTrue(mapping.light == DefaultPaletteMapping.LIGHT)
        assertTrue(mapping.dark == DefaultPaletteMapping.DARK)
    }
}
