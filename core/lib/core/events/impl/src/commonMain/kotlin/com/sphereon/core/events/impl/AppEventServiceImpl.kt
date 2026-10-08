/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events.impl

import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.events.AppEventService
import com.sphereon.core.events.EventContext
import com.sphereon.core.events.EventEncryptionService
import com.sphereon.core.events.EventHub
import com.sphereon.core.events.EventPersistenceSink
import com.sphereon.core.events.EventService
import com.sphereon.core.events.EventSigningService
import com.sphereon.core.events.EventStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * App-scoped event service implementation.
 *
 * This service is a singleton at the application level.
 * Events emitted from this service have an empty EventContext
 * since no user/session information is available.
 *
 * ## Use Cases
 *
 * - Application lifecycle events (startup, shutdown)
 * - System-level events (configuration changes)
 * - Events that occur outside of user context
 *
 * ## DI Registration
 *
 * Registered as:
 * - `AppEventService` (non-multibinding) for direct injection
 * - `EventService` (multibinding) for collecting all scope implementations
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<AppEventService>())
@ContributesIntoSet(AppScope::class, binding = binding<EventService>())
class AppEventServiceImpl(
    eventHub: EventHub,
    eventStore: EventStore,
    signingService: EventSigningService,
    encryptionService: EventEncryptionService,
    persistenceSinks: Set<EventPersistenceSink>,
) : AbstractEventService(eventHub, eventStore, signingService, encryptionService, persistenceSinks),
    AppEventService {
    override val scope: IdkScope = IdkScope.APP

    override fun getEventContext(): EventContext = EventContext.EMPTY
}
