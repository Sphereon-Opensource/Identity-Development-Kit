/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds

/**
 * Synchronous durable side effect performed before an event is broadcast.
 * Implementations must either finish persistence or throw; emission stops on failure.
 */
fun interface EventPersistenceSink {
    suspend fun persist(event: Event)
}

@ContributesTo(AppScope::class)
interface EventPersistenceSinkMultibindings {
    @Multibinds(allowEmpty = true)
    fun eventPersistenceSinks(): Set<EventPersistenceSink>
}
