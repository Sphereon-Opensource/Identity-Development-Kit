/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vp.verifier.impl.event

import com.sphereon.core.api.events.EventType
import com.sphereon.core.events.SessionEventService
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Emits only protocol state and bounded diagnostics; presentation material is never accepted. */
internal suspend fun SessionEventService.emitOid4vpSessionHistoryEvent(
    type: EventType,
    origin: String,
    session: AuthorizationSession,
    oldState: String? = null,
    newState: String? = session?.status?.name,
    stage: String? = null,
    outcome: String? = null,
    errorCode: String? = session?.error?.code,
) {
    emit(
        eventBuilder()
            .type(type)
            .origin(origin)
            .payload(
                buildJsonObject {
                    put("instanceId", session.instanceId)
                    put("protocolSessionId", session.sessionId)
                    put("correlationId", session.correlationId)
                    session.templateId?.let { put("templateId", it) }
                    oldState?.let { put("oldState", it) }
                    newState?.let { put("newState", it) }
                    stage?.let { put("stage", it) }
                    outcome?.let { put("outcome", it) }
                    errorCode?.let { put("errorCode", it) }
                },
            ).build(),
    )
}
