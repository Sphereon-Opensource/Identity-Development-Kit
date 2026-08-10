/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TestTimeSource

class MonotonicOAuth2ArtifactClockTest {
    @Test
    fun `backwards wall clock correction does not extend artifact lifetime`() {
        val initial = Instant.parse("2026-07-19T06:00:00Z")
        val wallClock = MutableClock(initial)
        val monotonic = TestTimeSource()
        val clock = MonotonicOAuth2ArtifactClock(wallClock, monotonic)

        wallClock.instant = initial - 30.seconds
        monotonic += 62.seconds

        assertEquals(initial + 62.seconds, clock.now())
    }
}

private class MutableClock(
    var instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}
