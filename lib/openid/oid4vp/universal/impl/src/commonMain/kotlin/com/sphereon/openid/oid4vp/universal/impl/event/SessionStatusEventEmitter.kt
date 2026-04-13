/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.universal.impl.event

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.events.SessionEventService
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.universal.UniversalOid4vpCategories
import com.sphereon.openid.oid4vp.universal.UniversalOid4vpEventTypes
import com.sphereon.openid.oid4vp.universal.UniversalOid4vpSubsystems
import com.sphereon.openid.oid4vp.verifier.callback.AuthorizationSessionCallbackDispatcher
import com.sphereon.openid.oid4vp.verifier.callback.AuthorizationSessionStatusUpdate
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Service for emitting events and dispatching callbacks when authorization session status changes.
 *
 * This service:
 * 1. Emits Universal OID4VP events via the SessionEventService
 * 2. Dispatches webhook callbacks when configured on the session
 *
 * Designed to be called by components that modify session status (e.g., store implementations,
 * HTTP command handlers).
 */
interface SessionStatusEventEmitter {

    /**
     * Emit an event and optionally dispatch a callback for a session status change.
     *
     * @param session The session that changed
     * @param previousStatus The status before the change (null for newly created sessions)
     */
    suspend fun onStatusChange(
        session: AuthorizationSession,
        previousStatus: AuthorizationSessionStatus?
    ): IdkResult<Unit, IdkError>

    /**
     * Emit a session deleted event.
     *
     * @param correlationId The correlation ID of the deleted session
     */
    suspend fun onSessionDeleted(correlationId: String): IdkResult<Unit, IdkError>

    /**
     * Emit a status polled event.
     *
     * @param session The session that was polled
     */
    suspend fun onStatusPolled(session: AuthorizationSession): IdkResult<Unit, IdkError>

    /**
     * Component interface for DI.
     */
    @SingleIn(SessionScope::class)
    @ContributesTo(SessionScope::class)
    interface Component {
        val sessionStatusEventEmitter: SessionStatusEventEmitter
    }
}

/**
 * Default implementation of [SessionStatusEventEmitter].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SessionStatusEventEmitter>())
class SessionStatusEventEmitterImpl(
    private val sessionEventService: SessionEventService,
    private val callbackDispatcher: AuthorizationSessionCallbackDispatcher
) : SessionStatusEventEmitter {

    override suspend fun onStatusChange(
        session: AuthorizationSession,
        previousStatus: AuthorizationSessionStatus?
    ): IdkResult<Unit, IdkError> {
        // 1. Emit SESSION_STATUS_CHANGED event
        val eventType = if (previousStatus == null) {
            UniversalOid4vpEventTypes.SESSION_CREATED
        } else {
            UniversalOid4vpEventTypes.SESSION_STATUS_CHANGED
        }

        val event = sessionEventService.eventBuilder()
            .type(eventType)
            .origin("universal-oid4vp")
            .subsystem(UniversalOid4vpSubsystems.UNIVERSAL_OID4VP)
            .category(UniversalOid4vpCategories.SESSION)
            .correlationId(session.correlationId)
            .payload(buildJsonObject {
                put("correlationId", session.correlationId)
                put("status", session.status.name)
                previousStatus?.let { put("previousStatus", it.name) }
                put("updatedAt", session.updatedAt)
            })
            .build()

        sessionEventService.emit(event)

        // 2. Dispatch webhook callback if configured
        val callback = session.callback
        if (callback != null) {
            val shouldDispatch = callback.statuses.isEmpty() || session.status in callback.statuses

            if (shouldDispatch) {
                val update = AuthorizationSessionStatusUpdate(
                    correlationId = session.correlationId,
                    status = session.status,
                    updatedAt = session.updatedAt,
                    errorCode = session.error?.code,
                    errorMessage = session.error?.message
                )

                val dispatchResult = callbackDispatcher.dispatch(callback.url, update)

                // Emit callback event (success or failure)
                val callbackEventType = if (dispatchResult.isOk) {
                    UniversalOid4vpEventTypes.CALLBACK_DISPATCHED
                } else {
                    UniversalOid4vpEventTypes.CALLBACK_FAILED
                }

                val callbackPayload = buildJsonObject {
                    put("correlationId", session.correlationId)
                    put("callbackUrl", callback.url)
                    put("status", session.status.name)
                    if (dispatchResult.isErr) {
                        put("error", dispatchResult.error.message.defaultMessage)
                    }
                }

                val callbackEvent = sessionEventService.eventBuilder()
                    .type(callbackEventType)
                    .origin("universal-oid4vp")
                    .subsystem(UniversalOid4vpSubsystems.UNIVERSAL_OID4VP)
                    .category(UniversalOid4vpCategories.CALLBACK)
                    .correlationId(session.correlationId)
                    .payload(callbackPayload)
                    .build()

                sessionEventService.emit(callbackEvent)
            }
        }

        return Ok(Unit)
    }

    override suspend fun onSessionDeleted(correlationId: String): IdkResult<Unit, IdkError> {
        val event = sessionEventService.eventBuilder()
            .type(UniversalOid4vpEventTypes.SESSION_DELETED)
            .origin("universal-oid4vp")
            .subsystem(UniversalOid4vpSubsystems.UNIVERSAL_OID4VP)
            .category(UniversalOid4vpCategories.SESSION)
            .correlationId(correlationId)
            .payload(buildJsonObject {
                put("correlationId", correlationId)
                put("deletedAt", Clock.System.now().toEpochMilliseconds())
            })
            .build()

        sessionEventService.emit(event)

        return Ok(Unit)
    }

    override suspend fun onStatusPolled(session: AuthorizationSession): IdkResult<Unit, IdkError> {
        val event = sessionEventService.eventBuilder()
            .type(UniversalOid4vpEventTypes.STATUS_POLLED)
            .origin("universal-oid4vp")
            .subsystem(UniversalOid4vpSubsystems.UNIVERSAL_OID4VP)
            .category(UniversalOid4vpCategories.SESSION)
            .correlationId(session.correlationId)
            .payload(buildJsonObject {
                put("correlationId", session.correlationId)
                put("status", session.status.name)
                put("polledAt", Clock.System.now().toEpochMilliseconds())
            })
            .build()

        sessionEventService.emit(event)

        return Ok(Unit)
    }
}
