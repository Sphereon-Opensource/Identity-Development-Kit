/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:OptIn(ExperimentalTime::class)

package com.sphereon.catalog.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class CatalogListingWindowTest {
    private val noon = Instant.parse("2026-08-15T12:00:00Z")

    @Test
    fun openWindowIsListedFromStartWithNoEnd() {
        val window = CatalogListingWindow.open(noon)
        assertFalse(window.includes(noon - 1.hours))
        assertTrue(window.includes(noon))
        assertTrue(window.includes(noon + 24.hours))
        assertFalse(window.isEmpty())
    }

    @Test
    fun closedWindowStopsAtEnd() {
        val window = CatalogListingWindow(start = noon, end = noon + 2.hours)
        assertTrue(window.includes(noon))
        assertTrue(window.includes(noon + 1.hours))
        assertFalse(window.includes(noon + 2.hours))
    }

    @Test
    fun emptyWindowIsNeverListed() {
        val window = CatalogListingWindow.never(noon)
        assertTrue(window.isEmpty())
        assertFalse(window.includes(noon))
        assertFalse(window.includes(noon + 1.hours))
        assertFalse(window.includes(noon - 1.hours))
    }

    @Test
    fun alwaysWindowIsListedAtAnyFiniteInstant() {
        assertTrue(CatalogListingWindow.ALWAYS.includes(noon))
        assertTrue(CatalogListingWindow.ALWAYS.includes(Instant.parse("1990-01-01T00:00:00Z")))
    }

    @Test
    fun epochMillisRoundTripPreservesBounds() {
        val window = CatalogListingWindow(start = noon, end = noon + 2.hours)
        val restored = CatalogListingWindow.fromEpochMillis(window.startEpochMillis(), window.endEpochMillis())
        assertTrue(restored.includes(noon))
        assertFalse(restored.includes(noon + 2.hours))
    }
}
