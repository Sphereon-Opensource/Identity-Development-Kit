/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.events.EncryptedPart
import com.sphereon.core.events.Event
import com.sphereon.core.events.EventEncryptionService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * No-op implementation of EventEncryptionService.
 *
 * This is the default IDK implementation that does not perform
 * any actual encryption. For KMS-integrated encryption, use EDK's
 * implementation.
 *
 * When encryption is requested but not available:
 * - encrypt() returns the event unchanged (no encryption applied)
 * - decrypt() returns the event unchanged (no decryption performed)
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<EventEncryptionService>())
class NoOpEventEncryptionService : EventEncryptionService {
    override suspend fun encrypt(
        event: Event,
        keyAlias: String?,
        parts: Set<EncryptedPart>,
    ): IdkResult<Event, IdkError> {
        // Return the event unchanged - no encryption capability in IDK
        return Ok(event)
    }

    override suspend fun decrypt(
        event: Event,
        keyAlias: String?,
    ): IdkResult<Event, IdkError> {
        // Return the event unchanged - no decryption capability in IDK
        return Ok(event)
    }

    override fun getDefaultKeyAlias(): String? = null
}
