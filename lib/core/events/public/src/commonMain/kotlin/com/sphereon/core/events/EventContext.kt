/*
 * Copyright (c) 2025 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events

import com.sphereon.di.context.AnonymousContext
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.context.UserContext
import com.sphereon.di.context.createAnonymousSessionContext
import com.sphereon.di.session.SessionContext
import kotlinx.serialization.Serializable

/**
 * Context information for an event, extracted from SessionContext.
 *
 * This data class provides bidirectional mapping:
 * - [fromSessionContext] to create from a SessionContext
 * - [toSessionContext] to recreate a SessionContext for lookup
 */
@Serializable
data class EventContext(
    /**
     * Session identifier.
     */
    val sessionId: String?,

    /**
     * Tenant identifier from the UserContext.
     */
    val tenantId: String?,

    /**
     * Principal identifier from the UserContext.
     * May be null for anonymous contexts.
     */
    val principalId: String?,

    /**
     * Optional correlation ID for tracing related events.
     */
    val correlationId: String? = null
) {
    companion object {
        /**
         * Create EventContext from a SessionContext.
         */
        fun fromSessionContext(ctx: SessionContext, correlationId: String? = null): EventContext = EventContext(
            sessionId = ctx.sessionId.takeIf { it != IdentityConstants.ANONYMOUS_SESSION_ID },
            tenantId = ctx.context.tenant.tenantId.takeIf { it != IdentityConstants.ANONYMOUS_TENANT_ID },
            principalId = ctx.context.principal?.toString()?.takeIf { it != IdentityConstants.ANONYMOUS_PRINCIPAL_ID },
            correlationId = correlationId
        )

        /**
         * Create EventContext from a UserContext.
         */
        fun fromUserContext(ctx: UserContext, correlationId: String? = null): EventContext = EventContext(
            sessionId = null,
            tenantId = ctx.tenant.tenantId.takeIf { it != IdentityConstants.ANONYMOUS_TENANT_ID },
            principalId = ctx.principal?.toString()?.takeIf { it != IdentityConstants.ANONYMOUS_PRINCIPAL_ID },
            correlationId = correlationId
        )

        /**
         * Empty context for app-scope events without session/user context.
         */
        val EMPTY = EventContext(
            sessionId = null,
            tenantId = null,
            principalId = null,
            correlationId = null
        )
    }

    /**
     * Create an anonymous SessionContext for event processing.
     *
     * Note: This creates a minimal SessionContext for lookup/processing purposes.
     * For full context restoration, use the original SessionContext if available.
     */
    fun toSessionContext(): SessionContext {
        return if (sessionId != null) {
            createAnonymousSessionContext(sessionId)
        } else {
            createAnonymousSessionContext("_from_event_context")
        }
    }

    /**
     * Check if this context is effectively anonymous (no real identifiers).
     */
    fun isAnonymous(): Boolean =
        sessionId == null && tenantId == null && principalId == null
}
