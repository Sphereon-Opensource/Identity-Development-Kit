/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package com.sphereon.core.events.impl

import com.sphereon.core.events.DefaultEvent
import com.sphereon.core.events.EventContext
import com.sphereon.core.events.NoOpSessionStatusEventPublisher
import com.sphereon.core.events.SessionEventService
import com.sphereon.core.events.SessionStatusChange
import com.sphereon.core.events.SessionStatusEventPayload
import com.sphereon.core.events.SessionStatusEventPublisher
import com.sphereon.di.context.ResolvedTenantIdProvider
import com.sphereon.di.session.SessionScope
import com.sphereon.di.context.IdentityConstants
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Persists every session status transition before [SessionEventService] broadcasts it. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<SessionStatusEventPublisher>(),
    replaces = [NoOpSessionStatusEventPublisher::class],
)
class EventHubSessionStatusEventPublisher(
    private val sessionEventService: SessionEventService,
    private val resolvedTenantIdProvider: ResolvedTenantIdProvider,
) : SessionStatusEventPublisher {
    override suspend fun publish(change: SessionStatusChange) {
        val context = EventContext.fromSessionContext(sessionEventService.sessionContext, correlationId = change.correlationId)
        val effectiveTenantId = (resolvedTenantIdProvider.currentTenantId()
            ?: sessionEventService.sessionContext.context.tenant.tenantId)
            .takeUnless { it == IdentityConstants.ANONYMOUS_TENANT_ID }
        val suppliedTenantId = change.tenantId?.takeUnless { it == IdentityConstants.ANONYMOUS_TENANT_ID }
        require(change.tenantId == null || suppliedTenantId == effectiveTenantId) {
            "Session status change tenant does not match the effective session tenant"
        }
        sessionEventService.emit(
            DefaultEvent(
                id = Uuid.random(),
                type = change.type,
                origin = "${change.subsystem.value}-session-store",
                timestamp = Clock.System.now(),
                context = context.copy(tenantId = effectiveTenantId),
                subsystem = change.subsystem,
                category = SessionStatusEventPayload.category,
                payload = SessionStatusEventPayload.encode(change),
            ),
        )
    }
}
