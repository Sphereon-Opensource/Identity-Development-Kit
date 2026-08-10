/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.time

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.TimeSource

/** Qualifier for clocks used to expire short-lived, server-issued opaque artifacts. */
internal const val OAUTH2_ARTIFACT_CLOCK = "oauth2-artifact-clock"

/**
 * A wall-clock-compatible clock whose elapsed time cannot move backwards while the process runs.
 *
 * Authorization codes and PAR request URIs express absolute expiry instants in their storage
 * contracts, but their security lifetime is relative to issuance. Anchoring an instant once and
 * advancing it from the platform monotonic source prevents a backwards NTP/VM clock correction
 * from extending those lifetimes. A process restart deliberately re-anchors to the current wall
 * clock so persisted absolute instants remain meaningful.
 */
internal class MonotonicOAuth2ArtifactClock(
    wallClock: Clock = Clock.System,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) : Clock {
    private val anchorInstant = wallClock.now()
    private val anchorMark = timeSource.markNow()

    override fun now(): Instant = anchorInstant + anchorMark.elapsedNow()
}

@ContributesTo(AppScope::class)
interface OAuth2ArtifactClockModule {
    @Provides
    @SingleIn(AppScope::class)
    @Named(OAUTH2_ARTIFACT_CLOCK)
    fun provideOAuth2ArtifactClock(): Clock = MonotonicOAuth2ArtifactClock()
}
