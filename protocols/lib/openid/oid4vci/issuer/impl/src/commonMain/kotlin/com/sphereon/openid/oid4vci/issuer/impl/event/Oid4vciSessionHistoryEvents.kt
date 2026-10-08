/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.event

import com.sphereon.core.api.events.EventType
import com.sphereon.core.events.SessionEventService
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Emits only allow-listed protocol facts. Protocol material and bearer identifiers are forbidden. */
internal suspend fun SessionEventService.emitOid4vciSessionHistoryEvent(
    type: EventType,
    origin: String,
    instanceId: String,
    protocolSessionId: String,
    correlationId: String? = null,
    oldState: String? = null,
    newState: String? = null,
    stage: String? = null,
    outcome: String? = null,
    credentialConfigurationId: String? = null,
    notificationEvent: String? = null,
    errorCode: String? = null,
    retryAfterSeconds: Int? = null,
) {
    require(instanceId.isNotBlank()) { "instanceId must not be blank" }
    require(protocolSessionId.isNotBlank()) { "protocolSessionId must not be blank" }
    emit(
        eventBuilder()
            .type(type)
            .origin(origin)
            .payload(
                buildJsonObject {
                    put("instanceId", instanceId)
                    put("protocolSessionId", protocolSessionId)
                    correlationId?.takeIf(String::isNotBlank)?.let { put("correlationId", it) }
                    oldState?.let { put("oldState", it) }
                    newState?.let { put("newState", it) }
                    stage?.let { put("stage", it) }
                    outcome?.let { put("outcome", it) }
                    credentialConfigurationId?.let { put("credentialConfigurationId", it) }
                    notificationEvent?.let { put("notificationEvent", it) }
                    errorCode?.let { put("errorCode", it) }
                    retryAfterSeconds?.let { put("retryAfterSeconds", it) }
                },
            ).build(),
    )
}
