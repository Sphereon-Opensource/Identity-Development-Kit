/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.methods.webvh.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class WebvhUpdateTestTimeTest {
    @Test
    fun equalClockSampleIsRejected() {
        val prior = "2026-10-03T12:00:00.001Z"
        assertNull(selectWebvhUpdateVersionTime(prior, Instant.parse(prior)))
    }

    @Test
    fun backwardsClockSampleIsRejected() {
        assertNull(
            selectWebvhUpdateVersionTime("2026-10-03T12:00:00.002Z", Instant.parse("2026-10-03T12:00:00.001Z")),
        )
    }

    @Test
    fun wholeSecondPriorRequiresBothInstantAndLexicalProgress() {
        val prior = "2026-10-03T12:00:00Z"
        assertNull(selectWebvhUpdateVersionTime(prior, Instant.parse("2026-10-03T12:00:00.001Z")))
        val observed = Instant.parse("2026-10-03T12:00:01Z")
        assertEquals(observed.toString(), selectWebvhUpdateVersionTime(prior, observed))
    }

    @Test
    fun fractionalProgressReturnsTheActualObservedSample() {
        val observed = Instant.parse("2026-10-03T12:00:00.002Z")
        val selected = selectWebvhUpdateVersionTime("2026-10-03T12:00:00.001Z", observed)
        assertEquals(observed.toString(), selected)
        assertEquals(observed, Instant.parse(requireNotNull(selected)))
    }
}
