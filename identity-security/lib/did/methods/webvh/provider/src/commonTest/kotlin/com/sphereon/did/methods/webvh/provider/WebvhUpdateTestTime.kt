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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

/** Returns an observed timestamp satisfying both existing update/replay order checks. */
internal fun selectWebvhUpdateVersionTime(prior: String, observed: Instant): String? {
    val candidate = observed.toString()
    return candidate.takeIf { observed > Instant.parse(prior) && candidate > prior }
}

/** Waits on the real clock; never synthesizes future timestamps or waits indefinitely. */
internal suspend fun awaitWebvhUpdateVersionTime(prior: String): String =
    withContext(Dispatchers.Default) {
        val started = TimeSource.Monotonic.markNow()
        var selected: String? = null
        while (selected == null) {
            check(started.elapsedNow() < 2.seconds) {
                "No observed WebVH version time strictly after $prior within two seconds"
            }
            selected = selectWebvhUpdateVersionTime(prior, Clock.System.now())
            if (selected == null) delay(1)
        }
        val versionTime = requireNotNull(selected)
        // Verify fixture preconditions before executing the real update command.
        assertTrue(Instant.parse(versionTime) > Instant.parse(prior))
        assertTrue(versionTime > prior)
        assertTrue(Instant.parse(versionTime) <= Clock.System.now())
        versionTime
    }
