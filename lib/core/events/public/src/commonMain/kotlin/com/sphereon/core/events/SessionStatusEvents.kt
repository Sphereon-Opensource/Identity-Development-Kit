/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events

import com.sphereon.core.api.events.EventCategories
import com.sphereon.core.api.events.EventCategory
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventType
import com.sphereon.core.api.http.callback.CallbackSigningAlgorithm
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Event types published when a protocol session moves to a new status. */
object SessionStatusEventTypes {
    val OID4VCI_ISSUANCE_SESSION_STATUS_CHANGED = EventType("oid4vci.issuance.session.status_changed")
    val OID4VP_VERIFICATION_SESSION_STATUS_CHANGED = EventType("oid4vp.verification.session.status_changed")
}

/**
 * Callback the session owner configured when the session was created. Carried on the status event
 * so a subscriber can deliver it without reading the session store.
 */
@Serializable
data class SessionCallbackDescriptor(
    val url: String,
    /** Status names that trigger the callback. Empty selects every status. */
    val statuses: List<String> = emptyList(),
    val includeData: Boolean = false,
    val secretRef: String? = null,
    val signing: CallbackSigningAlgorithm? = null,
)

/** One status transition of a protocol session. */
@Serializable
data class SessionStatusChange(
    val type: EventType,
    val subsystem: EventSubsystem,
    val sessionId: String,
    val correlationId: String,
    val instanceId: String,
    val tenantId: String?,
    val previousStatus: String?,
    val status: String,
    val callback: SessionCallbackDescriptor? = null,
    val data: JsonObject? = null,
)

/** Payload shape of the events in [SessionStatusEventTypes]. */
object SessionStatusEventPayload {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val category: EventCategory get() = EventCategories.LIFECYCLE

    fun encode(change: SessionStatusChange): JsonObject =
        json.encodeToJsonElement(SessionStatusChange.serializer(), change).jsonObject

    fun decode(payload: JsonObject): SessionStatusChange =
        json.decodeFromJsonElement(SessionStatusChange.serializer(), payload)

    fun decodeOrNull(payload: JsonObject): SessionStatusChange? =
        try {
            decode(payload)
        } catch (_: IllegalArgumentException) {
            null
        }
}

/**
 * Seam through which protocol session stores announce status transitions. The default binding does
 * nothing; the event-hub implementation publishes a [DefaultEvent] carrying [SessionStatusChange].
 */
@JsExportIgnoreCompat
interface SessionStatusEventPublisher {
    suspend fun publish(change: SessionStatusChange)
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SessionStatusEventPublisher>())
class NoOpSessionStatusEventPublisher : SessionStatusEventPublisher {
    override suspend fun publish(change: SessionStatusChange) = Unit
}
