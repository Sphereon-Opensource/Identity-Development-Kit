/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events

import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.events.EventCategory
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventType
import com.sphereon.core.api.tracing.TraceContext

/**
 * Super interface for event emission services.
 *
 * This is the scope-agnostic base interface. Use the scope-specific
 * marker interfaces ([AppEventService], [UserEventService], [SessionEventService])
 * for injection.
 *
 * Following the ConfigService/LogService patterns:
 * - Super interface defines the contract
 * - Marker interfaces add scope information
 * - Implementations contribute via multibinding to `Set<EventService>`
 * - Scope-specific bindings are also available (non-multibinding)
 *
 * @see AppEventService for app-scoped singleton
 * @see UserEventService for user-context scoped
 * @see SessionEventService for session-scoped
 */
interface EventService {
    /**
     * Which scope this service operates in.
     */
    val scope: IdkScope

    /**
     * The central event hub for broadcasting.
     */
    val eventHub: EventHub

    /**
     * Emit an event without signing or encryption.
     */
    suspend fun emit(event: Event)

    /**
     * Emit an event with optional signing and/or encryption.
     *
     * @param event The event to emit
     * @param sign Whether to sign the event for authenticity
     * @param encrypt Whether to encrypt payload/context for confidentiality
     * @param keyAlias Optional key alias for signing (uses default if null)
     * @param encryptionKeyAlias Optional key alias for encryption (uses default if null)
     * @param encryptParts Which parts to encrypt (default: payload only)
     */
    suspend fun emit(
        event: Event,
        sign: Boolean = false,
        encrypt: Boolean = false,
        keyAlias: String? = null,
        encryptionKeyAlias: String? = null,
        encryptParts: Set<EncryptedPart> = setOf(EncryptedPart.PAYLOAD),
    )

    /**
     * Create a builder for constructing events.
     */
    fun eventBuilder(): EventBuilder
}

/**
 * Builder interface for constructing events.
 */
interface EventBuilder {
    fun type(type: EventType): EventBuilder

    fun origin(origin: String): EventBuilder

    fun context(context: EventContext): EventBuilder

    fun subsystem(subsystem: EventSubsystem): EventBuilder

    fun category(category: EventCategory): EventBuilder

    fun payload(payload: kotlinx.serialization.json.JsonObject): EventBuilder

    fun correlationId(correlationId: String): EventBuilder

    /**
     * Override the schema version for the event being built. Defaults to [DEFAULT_EVENT_VERSION] (`"v1"`).
     */
    fun version(version: String): EventBuilder

    /**
     * Populate tracing fields on the event's context from a [TraceContext].
     * Has no effect when [traceContext] is null. Pre-existing non-null trace
     * fields on the context are preserved.
     */
    fun trace(traceContext: TraceContext?): EventBuilder

    /**
     * Build the event.
     * @throws IllegalStateException if required fields are not set
     */
    fun build(): Event
}
