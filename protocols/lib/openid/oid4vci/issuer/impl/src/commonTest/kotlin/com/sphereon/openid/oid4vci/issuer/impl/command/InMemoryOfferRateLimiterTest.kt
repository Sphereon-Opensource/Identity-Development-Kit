/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.openid.oid4vci.issuer.command.OfferRateLimit
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private class MutableTestClock(
    var current: Instant,
) : Clock {
    override fun now(): Instant = current

    fun advanceBySeconds(secondsToAdvance: Long) {
        current += secondsToAdvance.seconds
    }
}

class InMemoryOfferRateLimiterTest {
    private val limit = OfferRateLimit(maxPerWindow = 3, windowSeconds = 60)

    @Test
    fun acquisitionsWithinLimitSucceed() =
        runTest {
            val clock = MutableTestClock(Instant.parse("2026-05-15T12:00:00Z"))
            val limiter = InMemoryOfferRateLimiter(clock)

            repeat(limit.maxPerWindow) { index ->
                val result = limiter.tryAcquire("offer-1", limit)
                assertTrue(result.isOk, "acquisition $index should be Ok")
                assertEquals(true, result.value, "acquisition $index should be within limit")
            }
        }

    @Test
    fun acquisitionBeyondLimitIsRejected() =
        runTest {
            val clock = MutableTestClock(Instant.parse("2026-05-15T12:00:00Z"))
            val limiter = InMemoryOfferRateLimiter(clock)

            repeat(limit.maxPerWindow) {
                assertEquals(true, limiter.tryAcquire("offer-1", limit).value)
            }

            val overLimit = limiter.tryAcquire("offer-1", limit)
            assertTrue(overLimit.isOk)
            assertFalse(overLimit.value!!, "the N+1th acquisition must be rejected")
        }

    @Test
    fun windowResetsAfterElapsedTime() =
        runTest {
            val clock = MutableTestClock(Instant.parse("2026-05-15T12:00:00Z"))
            val limiter = InMemoryOfferRateLimiter(clock)

            repeat(limit.maxPerWindow) {
                assertEquals(true, limiter.tryAcquire("offer-1", limit).value)
            }
            assertFalse(limiter.tryAcquire("offer-1", limit).value!!)

            clock.advanceBySeconds(limit.windowSeconds + 1)

            val afterReset = limiter.tryAcquire("offer-1", limit)
            assertTrue(afterReset.isOk)
            assertEquals(true, afterReset.value, "window should reset once it has elapsed")
        }

    @Test
    fun limitsAreTrackedPerOfferId() =
        runTest {
            val clock = MutableTestClock(Instant.parse("2026-05-15T12:00:00Z"))
            val limiter = InMemoryOfferRateLimiter(clock)

            repeat(limit.maxPerWindow) {
                assertEquals(true, limiter.tryAcquire("offer-1", limit).value)
            }
            assertFalse(limiter.tryAcquire("offer-1", limit).value!!)

            // A different offer id has its own independent window.
            assertEquals(true, limiter.tryAcquire("offer-2", limit).value)
        }

    @Test
    fun staleWindowEntriesAreSweptOnNextAcquire() =
        runTest {
            val clock = MutableTestClock(Instant.parse("2026-05-15T12:00:00Z"))
            val limiter = InMemoryOfferRateLimiter(clock)

            // Populate several distinct offer-id windows.
            limiter.tryAcquire("sweep-offer-1", limit)
            limiter.tryAcquire("sweep-offer-2", limit)
            limiter.tryAcquire("sweep-offer-3", limit)
            assertEquals(3, limiter.windowCount(), "three active entries before sweep")

            // Advance past the window expiry so all entries are stale.
            clock.advanceBySeconds(limit.windowSeconds + 1)

            // Any tryAcquire triggers the lazy sweep.
            limiter.tryAcquire("sweep-offer-1", limit)

            // After the sweep only the entry that was just re-opened should remain.
            assertEquals(1, limiter.windowCount(), "stale entries must be evicted on sweep")
        }
}
