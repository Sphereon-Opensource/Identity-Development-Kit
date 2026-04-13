/*
 * Copyright (c) 2025 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events.impl

import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.events.AppEventService
import com.sphereon.core.events.EventContext
import com.sphereon.core.events.EventEncryptionService
import com.sphereon.core.events.EventHub
import com.sphereon.core.events.EventService
import com.sphereon.core.events.EventSigningService
import com.sphereon.core.events.EventStore
import com.sphereon.core.events.UserEventService
import com.sphereon.di.context.UserContext
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet

/**
 * User-scoped event service implementation.
 *
 * This service is a singleton within a user context (tenant + principal).
 * Events emitted from this service have tenantId and principalId populated
 * but no sessionId.
 *
 * ## Use Cases
 *
 * - User context lifecycle events
 * - Events that occur with user identity but outside a session
 * - Background operations for a specific user
 *
 * ## DI Registration
 *
 * Registered as:
 * - `UserEventService` (non-multibinding) for direct injection
 * - `EventService` (multibinding) for collecting all scope implementations
 *
 * ## Note on UserContext
 *
 * UserContext is passed via factory to UserContextComponent but is not automatically injectable
 * (kotlin-inject-anvil limitation). We inject UserContextInstance instead, which provides access
 * to the UserContext via its `context` property.
 */
@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<UserEventService>())
@ContributesIntoSet(UserScope::class, binding = binding<EventService>())
class UserEventServiceImpl(
    override val parent: AppEventService,
    private val userContextInstance: UserContextInstance,
    eventHub: EventHub,
    eventStore: EventStore,
    signingService: EventSigningService,
    encryptionService: EventEncryptionService
) : AbstractEventService(eventHub, eventStore, signingService, encryptionService), UserEventService {

    override val userContext: UserContext
        get() = userContextInstance.context

    override val scope: IdkScope = IdkScope.USER

    override fun getEventContext(): EventContext =
        EventContext.fromUserContext(userContext)
}
