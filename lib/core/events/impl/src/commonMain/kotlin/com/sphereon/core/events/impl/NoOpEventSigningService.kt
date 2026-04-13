/*
 * Copyright (c) 2025 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.events.Event
import com.sphereon.core.events.EventSigningService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * No-op implementation of EventSigningService.
 *
 * This is the default IDK implementation that does not perform
 * any actual signing. For KMS-integrated signing, use EDK's
 * implementation.
 *
 * When signing is requested but not available:
 * - sign() returns the event unchanged (no signature added)
 * - verify() always returns true (no verification performed)
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<EventSigningService>())
class NoOpEventSigningService : EventSigningService {

    override suspend fun sign(event: Event, keyAlias: String?): IdkResult<Event, IdkError> {
        // Return the event unchanged - no signing capability in IDK
        return Ok(event)
    }

    override suspend fun verify(event: Event): IdkResult<Boolean, IdkError> {
        // If there's no signature, it's trivially "valid"
        // If there is a signature, we can't verify it, so return true to not block
        // Real verification would happen in EDK's KMS-integrated implementation
        return Ok(true)
    }

    override fun getDefaultKeyAlias(): String? = null
}
