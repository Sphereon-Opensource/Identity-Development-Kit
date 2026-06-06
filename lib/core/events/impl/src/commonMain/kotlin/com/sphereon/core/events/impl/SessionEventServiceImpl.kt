/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events.impl

import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.events.EventContext
import com.sphereon.core.events.EventEncryptionService
import com.sphereon.core.events.EventHub
import com.sphereon.core.events.EventService
import com.sphereon.core.events.EventSigningService
import com.sphereon.core.events.EventStore
import com.sphereon.core.events.SessionEventService
import com.sphereon.core.events.UserEventService
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Session-scoped event service implementation.
 *
 * This service is a singleton within a session context.
 * Events emitted from this service have full EventContext populated:
 * sessionId, tenantId, and principalId.
 *
 * ## Use Cases
 *
 * - Command lifecycle events (started, completed, failed)
 * - Session lifecycle events (created, closed)
 * - Request/response events
 * - Any events that occur within a session context
 *
 * This is the most commonly used event service for command implementations.
 *
 * ## DI Registration
 *
 * Registered as:
 * - `SessionEventService` (non-multibinding) for direct injection
 * - `EventService` (multibinding) for collecting all scope implementations
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SessionEventService>())
@ContributesBinding(SessionScope::class, binding = binding<SessionEventService?>())
@ContributesIntoSet(SessionScope::class, binding = binding<EventService>())
class SessionEventServiceImpl(
    override val parent: UserEventService,
    override val sessionContext: SessionContext,
    eventHub: EventHub,
    eventStore: EventStore,
    signingService: EventSigningService,
    encryptionService: EventEncryptionService,
) : AbstractEventService(eventHub, eventStore, signingService, encryptionService),
    SessionEventService {
    override val scope: IdkScope = IdkScope.SESSION

    override fun getEventContext(): EventContext = EventContext.fromSessionContext(sessionContext)
}
