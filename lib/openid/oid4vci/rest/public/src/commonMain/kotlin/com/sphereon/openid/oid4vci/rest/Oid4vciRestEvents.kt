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

package com.sphereon.openid.oid4vci.rest

import com.sphereon.core.api.events.EventCategory
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventType

/**
 * Event subsystem identifiers for OID4VCI REST API.
 */
object Oid4vciRestSubsystems {
    val OID4VCI_REST = EventSubsystem("oid4vci.rest")
}

/**
 * Event categories for OID4VCI REST API.
 */
object Oid4vciRestCategories {
    val SESSION = EventCategory("session")
    val CALLBACK = EventCategory("callback")
}

/**
 * Event types for the OID4VCI backend REST API.
 */
object Oid4vciRestEventTypes {
    /**
     * Emitted when a new credential offer session is created.
     *
     * Payload includes: correlationId, credentialConfigurationIds, offerUri
     */
    val SESSION_CREATED = EventType("oid4vci.rest.session.created")

    /**
     * Emitted when a session status changes.
     *
     * Payload includes: correlationId, previousStatus, currentStatus, updatedAt
     */
    val SESSION_STATUS_CHANGED = EventType("oid4vci.rest.session.status_changed")

    /**
     * Emitted when a session is deleted.
     *
     * Payload includes: correlationId, deletedAt
     */
    val SESSION_DELETED = EventType("oid4vci.rest.session.deleted")

    /**
     * Emitted when a webhook callback is successfully dispatched.
     *
     * Payload includes: correlationId, callbackUrl, status
     */
    val CALLBACK_DISPATCHED = EventType("oid4vci.rest.callback.dispatched")

    /**
     * Emitted when a webhook callback fails.
     *
     * Payload includes: correlationId, callbackUrl, error
     */
    val CALLBACK_FAILED = EventType("oid4vci.rest.callback.failed")

    /**
     * Emitted when a status poll request is received.
     *
     * Payload includes: correlationId, status, polledAt
     */
    val STATUS_POLLED = EventType("oid4vci.rest.status.polled")
}
