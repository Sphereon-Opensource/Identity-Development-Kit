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

package com.sphereon.oauth2.oidf.op

import com.sphereon.core.defaults.time.ClockModule
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Test [Clock] that defaults to wall-clock and lets a test pin or advance virtual time. Used by
 * [OidfOpServerFixture] so OIDC `max_age` / session-staleness assertions can drive elapsed time
 * without `Thread.sleep`. Thread-safe via [AtomicReference] so concurrent Ktor request handlers
 * see a consistent view.
 */
class TestClock : Clock {
    private val override: AtomicReference<Instant?> = AtomicReference(null)

    override fun now(): Instant = override.get() ?: Clock.System.now()

    /** Pin [now] to [instant] until [clearOverride] is called. */
    fun setOverride(instant: Instant) {
        override.set(instant)
    }

    /**
     * Shift the override by [duration]. If no override was set, the current wall-clock instant is
     * captured first so subsequent reads are deterministic.
     */
    fun advance(duration: Duration) {
        while (true) {
            val current = override.get() ?: Clock.System.now()
            val next = current + duration
            if (override.compareAndSet(override.get(), next)) {
                return
            }
        }
    }

    /** Drop the override; [now] returns to wall-clock passthrough. */
    fun clearOverride() {
        override.set(null)
    }
}

/**
 * App-scoped binding of [Clock] to a single shared [TestClock]. Replaces the default
 * [ClockModule] (which binds [Clock.System]) so tests advancing virtual time observe their
 * advancement on every consumer in the merged graph. Picked up by [OidfOpTestAppGraph], which
 * is compiled in jvmTest where this contribution is visible.
 */
@ContributesTo(AppScope::class, replaces = [ClockModule::class])
interface TestClockModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideTestClock(): TestClock = TestClock()

    @Provides
    @SingleIn(AppScope::class)
    fun provideClock(testClock: TestClock): Clock = testClock
}
