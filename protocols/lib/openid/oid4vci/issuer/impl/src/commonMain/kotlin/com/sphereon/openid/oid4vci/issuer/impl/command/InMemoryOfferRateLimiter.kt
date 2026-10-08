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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.issuer.command.OfferRateLimit
import com.sphereon.openid.oid4vci.issuer.command.OfferRateLimiter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * In-memory fixed-window [OfferRateLimiter].
 *
 * Each `offerId` carries a window that opens on its first acquisition. Acquisitions within the
 * window increment a counter; once the counter reaches [OfferRateLimit.maxPerWindow] further
 * acquisitions fail until the window elapses, at which point it resets on the next acquisition.
 *
 * The window-state map is `AppScope`-scoped so it survives across the per-fetch sessions that
 * mint fresh offers. State is held entirely in memory: process restart resets all windows, which
 * is acceptable for a fixed-window abuse guard. Sliding-window and per-IP buckets are follow-ups.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<OfferRateLimiter>())
class InMemoryOfferRateLimiter(
    private val clock: Clock,
) : OfferRateLimiter {
    private data class Window(
        val startedAtMillis: Long,
        val count: Int,
    )

    private val mutex = Mutex()
    private val windows = mutableMapOf<String, Window>()

    override suspend fun tryAcquire(
        offerId: String,
        limit: OfferRateLimit,
    ): IdkResult<Boolean, IdkError> {
        val nowMillis = clock.now().toEpochMilliseconds()
        val windowMillis = limit.windowSeconds * MILLIS_PER_SECOND
        return mutex.withLock {
            // Lazy sweep: remove entries whose window has fully elapsed. A stale entry that would
            // be reset on the next access contributes nothing and accumulates indefinitely in an
            // AppScope singleton, so we evict them here while we already hold the lock.
            windows.entries.removeAll { (_, w) -> nowMillis - w.startedAtMillis >= windowMillis }

            val current = windows[offerId]
            val active =
                current?.takeIf { nowMillis - it.startedAtMillis < windowMillis }
            if (active == null) {
                windows[offerId] = Window(startedAtMillis = nowMillis, count = 1)
                Ok(true)
            } else if (active.count < limit.maxPerWindow) {
                windows[offerId] = active.copy(count = active.count + 1)
                Ok(true)
            } else {
                Ok(false)
            }
        }
    }

    /** Returns the number of active (non-stale) window entries. For testing only. */
    internal fun windowCount(): Int = windows.size

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
