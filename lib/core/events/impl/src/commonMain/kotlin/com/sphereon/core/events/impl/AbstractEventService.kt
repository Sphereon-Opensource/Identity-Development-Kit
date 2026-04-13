/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events.impl

import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.events.EncryptedPart
import com.sphereon.core.events.Event
import com.sphereon.core.events.EventBuilder
import com.sphereon.core.events.EventContext
import com.sphereon.core.events.EventEncryptionService
import com.sphereon.core.events.EventHub
import com.sphereon.core.events.EventService
import com.sphereon.core.events.EventSigningService
import com.sphereon.core.events.EventStore

/**
 * Abstract base class for EventService implementations.
 *
 * Provides common functionality for all scope-specific event services:
 * - Event emission with optional signing and encryption
 * - Integration with EventHub for broadcasting
 * - Integration with EventStore for persistence
 * - EventBuilder creation with scope-appropriate context
 *
 * Subclasses provide scope-specific context and builder configuration.
 */
abstract class AbstractEventService(
    override val eventHub: EventHub,
    protected val eventStore: EventStore,
    protected val signingService: EventSigningService,
    protected val encryptionService: EventEncryptionService,
) : EventService {
    /**
     * Get the EventContext for events emitted from this service.
     * Subclasses provide scope-appropriate context.
     */
    protected abstract fun getEventContext(): EventContext

    override suspend fun emit(event: Event) {
        emit(event, sign = false, encrypt = false, keyAlias = null, encryptionKeyAlias = null)
    }

    override suspend fun emit(
        event: Event,
        sign: Boolean,
        encrypt: Boolean,
        keyAlias: String?,
        encryptionKeyAlias: String?,
        encryptParts: Set<EncryptedPart>,
    ) {
        var finalEvent = event

        // Sign the event if requested
        if (sign) {
            val signResult = signingService.sign(finalEvent, keyAlias)
            if (signResult.isOk) {
                finalEvent = signResult.value
            }
            // If signing fails, we still emit the unsigned event
            // The caller can check event.signature to verify
        }

        // Encrypt the event if requested
        if (encrypt) {
            val encryptResult = encryptionService.encrypt(finalEvent, encryptionKeyAlias, encryptParts)
            if (encryptResult.isOk) {
                finalEvent = encryptResult.value
            }
            // If encryption fails, we still emit the unencrypted event
            // The caller can check event.encryption to verify
        }

        // Store the event
        eventStore.store(finalEvent)

        // Broadcast the event
        eventHub.publish(finalEvent)
    }

    override fun eventBuilder(): EventBuilder = DefaultEventBuilder(scope, getEventContext())
}
