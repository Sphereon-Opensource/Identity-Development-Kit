/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.api.events

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Extensible event type identifier using a value class.
 *
 * This design allows EDK and VDX to define their own event types
 * by creating additional objects with EventType instances.
 *
 * Example:
 * ```kotlin
 * // IDK defaults in EventTypes object
 * object EventTypes {
 *     val COMMAND_STARTED = EventType("command.started")
 * }
 *
 * // EDK can extend:
 * object EdkEventTypes {
 *     val PARTY_CREATED = EventType("party.created")
 * }
 *
 * // VDX can extend:
 * object VdxEventTypes {
 *     val RESOURCE_BOOKED = EventType("resource.booked")
 * }
 * ```
 */
@Serializable
@JvmInline
value class EventType(
    val value: String,
) {
    /**
     * Check if this event type matches a pattern (glob-style).
     * Supports * for single segment and ** for multiple segments.
     */
    fun matches(pattern: String): Boolean {
        if (pattern == "**") {
            return true
        }
        if (pattern == value) {
            return true
        }

        val regexPattern =
            pattern
                .replace(".", "\\.")
                .replace("**", ".*")
                .replace("*", "[^.]*")
        return value.matches(Regex(regexPattern))
    }

    override fun toString(): String = value

    companion object {
        /**
         * Create a custom event type with optional version.
         */
        fun custom(
            name: String,
            version: String = "1.0",
        ): EventType = EventType("custom.$name.v$version")
    }
}

/**
 * IDK default event types.
 * EDK and VDX can define their own objects with additional types.
 */
object EventTypes {
    // Command lifecycle events (automatic from CommandExtension)
    val COMMAND_STARTED = EventType("command.started")
    val COMMAND_COMPLETED = EventType("command.completed")
    val COMMAND_FAILED = EventType("command.failed")

    // Session lifecycle events
    val SESSION_CREATED = EventType("session.created")
    val SESSION_CLOSED = EventType("session.closed")

    // User context lifecycle events
    val USER_CONTEXT_CREATED = EventType("user-context.created")
    val USER_CONTEXT_CLOSED = EventType("user-context.closed")

    // Generic custom event
    val CUSTOM = EventType("custom")

    // OAuth2 server (authorization + resource) protocol events
    val OAUTH2_TOKEN_ISSUED = EventType("oauth2.token.issued")
    val OAUTH2_TOKEN_FAILED = EventType("oauth2.token.failed")
    val OAUTH2_TOKEN_INTROSPECTED = EventType("oauth2.token.introspected")
    val OAUTH2_TOKEN_REVOKED = EventType("oauth2.token.revoked")

    // OID4VCI issuer protocol events
    val OID4VCI_OFFER_CREATED = EventType("oid4vci.offer.created")
    val OID4VCI_TOKEN_ISSUED = EventType("oid4vci.token.issued")
    val OID4VCI_CREDENTIAL_ISSUED = EventType("oid4vci.credential.issued")
    val OID4VCI_CREDENTIAL_FAILED = EventType("oid4vci.credential.failed")
    val OID4VCI_CREDENTIAL_DEFERRED_ISSUED = EventType("oid4vci.credential.deferred_issued")
    val OID4VCI_NOTIFICATION_RECEIVED = EventType("oid4vci.notification.received")

    // OID4VP verifier protocol events
    val OID4VP_REQUEST_CREATED = EventType("oid4vp.request.created")
    val OID4VP_RESPONSE_RECEIVED = EventType("oid4vp.response.received")
    val OID4VP_RESPONSE_VERIFIED = EventType("oid4vp.response.verified")
    val OID4VP_RESPONSE_FAILED = EventType("oid4vp.response.failed")

    /**
     * Helper to create custom types with version.
     */
    fun custom(
        name: String,
        version: String = "1.0",
    ): EventType = EventType.custom(name, version)
}
