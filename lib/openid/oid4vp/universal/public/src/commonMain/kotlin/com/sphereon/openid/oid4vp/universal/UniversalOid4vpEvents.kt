/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.universal

import com.sphereon.core.api.events.EventCategory
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventType
import com.sphereon.openid.oid4vp.verifier.Oid4vpVerifierSessionEventTypes

/**
 * Event subsystem identifiers for Universal OID4VP.
 */
object UniversalOid4vpSubsystems {
    val UNIVERSAL_OID4VP = EventSubsystem("oid4vp.universal")
}

/**
 * Event categories for Universal OID4VP.
 */
object UniversalOid4vpCategories {
    val SESSION = EventCategory("session")
    val CALLBACK = EventCategory("callback")
}

/**
 * Event types for Universal OID4VP REST API.
 *
 * These events are emitted by the Universal OID4VP endpoints and can be
 * subscribed to via the IDK event system.
 */
object UniversalOid4vpEventTypes {
    /**
     * Emitted when a new authorization session is created via the REST API.
     *
     * Payload includes: correlationId, queryId, requestUri
     */
    val SESSION_CREATED = EventType("oid4vp.universal.session.created")

    /**
     * Emitted when a session status changes.
     *
     * Payload includes: correlationId, previousStatus, currentStatus
     */
    val SESSION_STATUS_CHANGED = Oid4vpVerifierSessionEventTypes.STATUS_CHANGED

    /**
     * Emitted when a session is deleted via the REST API.
     *
     * Payload includes: correlationId
     */
    val SESSION_DELETED = EventType("oid4vp.universal.session.deleted")

    /** Emitted immediately before a configured session callback is attempted. */
    val CALLBACK_ATTEMPTED = Oid4vpVerifierSessionEventTypes.CALLBACK_ATTEMPTED

    /**
     * Emitted when a webhook callback is successfully dispatched.
     *
     * Payload includes: correlationId, callbackUrl, status
     */
    val CALLBACK_DISPATCHED = Oid4vpVerifierSessionEventTypes.CALLBACK_SUCCEEDED

    /**
     * Emitted when a webhook callback fails.
     *
     * Payload includes: correlationId, callbackUrl, error
     */
    val CALLBACK_FAILED = Oid4vpVerifierSessionEventTypes.CALLBACK_FAILED

    /**
     * Emitted when a status poll request is received.
     *
     * Payload includes: correlationId, status
     */
    val STATUS_POLLED = EventType("oid4vp.universal.status.polled")

    val ALL: Set<EventType> = setOf(
        SESSION_CREATED,
        SESSION_STATUS_CHANGED,
        SESSION_DELETED,
        CALLBACK_ATTEMPTED,
        CALLBACK_DISPATCHED,
        CALLBACK_FAILED,
        STATUS_POLLED,
    )
}
