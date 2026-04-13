/*
 * Copyright (c) 2025 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Base event interface used across IDK, EDK, and VDX.
 *
 * Events are immutable records of something that happened in the system.
 * They can be:
 * - Emitted automatically from commands via [ICommandExecutionExtension]
 * - Emitted manually using [EventService.emit]
 * - Signed for authenticity using [EventSignature]
 * - Encrypted for confidentiality using [EventEncryption]
 */
@OptIn(ExperimentalUuidApi::class)
interface Event {
    /**
     * Unique event identifier.
     */
    val id: Uuid

    /**
     * Event type (extensible value class).
     * @see EventType
     * @see EventTypes
     */
    val type: EventType

    /**
     * Identifier of the command/service that emitted this event.
     */
    val origin: String

    /**
     * When the event occurred.
     */
    val timestamp: Instant

    /**
     * Context information (session, tenant, principal).
     */
    val context: EventContext

    /**
     * Which subsystem emitted the event (extensible value class).
     * @see EventSubsystem
     * @see EventSubsystems
     */
    val subsystem: EventSubsystem

    /**
     * Event category for filtering (extensible value class).
     * @see EventCategory
     * @see EventCategories
     */
    val category: EventCategory

    /**
     * Serializable payload data specific to the event type.
     * For command events, this contains commandId, durationMs, etc.
     */
    val payload: JsonObject

    /**
     * Optional KMS signature for authenticity (JWS).
     */
    val signature: EventSignature?

    /**
     * Optional encryption for confidentiality (JWE).
     * When present, [payload] and/or [context] may be encrypted.
     */
    val encryption: EventEncryption?
}

/**
 * Signature information for signed events.
 */
@Serializable
data class EventSignature(
    /**
     * Key alias used for signing.
     */
    val keyAlias: String,

    /**
     * KMS provider ID.
     */
    val providerId: String,

    /**
     * Algorithm used (e.g., "ES256", "RS256").
     */
    val algorithm: String,

    /**
     * The JWS signature (compact or detached).
     */
    val jws: String
)

/**
 * Encryption information for encrypted events.
 */
@Serializable
data class EventEncryption(
    /**
     * Key alias used for encryption.
     */
    val keyAlias: String,

    /**
     * KMS provider ID.
     */
    val providerId: String,

    /**
     * Algorithm used (e.g., "A256GCM").
     */
    val algorithm: String,

    /**
     * Key encryption algorithm (e.g., "ECDH-ES+A256KW").
     */
    val keyAlgorithm: String,

    /**
     * Which parts are encrypted.
     */
    val encryptedParts: Set<EncryptedPart>,

    /**
     * The JWE compact serialization.
     */
    val jwe: String
)

/**
 * Parts of an event that can be encrypted.
 */
@Serializable
enum class EncryptedPart {
    PAYLOAD,
    CONTEXT
}

/**
 * Default implementation of [Event].
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class DefaultEvent(
    override val id: Uuid,
    override val type: EventType,
    override val origin: String,
    override val timestamp: Instant,
    override val context: EventContext,
    override val subsystem: EventSubsystem,
    override val category: EventCategory,
    override val payload: JsonObject,
    override val signature: EventSignature? = null,
    override val encryption: EventEncryption? = null
) : Event
