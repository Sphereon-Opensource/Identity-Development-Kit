/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn

/**
 * Service for cryptographically signing events.
 *
 * EventSigningService uses the Key Management System (KMS) to
 * sign events for authenticity and integrity verification.
 * Signed events include a JWS (JSON Web Signature) that can
 * be verified by any party with access to the public key.
 *
 * ## Why Sign Events?
 *
 * - **Authenticity**: Proves the event came from a trusted source
 * - **Integrity**: Detects any tampering with event data
 * - **Non-repudiation**: Source cannot deny emitting the event
 * - **Audit Trail**: Signed events provide cryptographic proof for compliance
 *
 * ## Usage
 *
 * Signing is typically handled by EventService when `sign = true`:
 *
 * ```kotlin
 * eventService.emit(event, sign = true, keyAlias = "events-signing-key")
 * ```
 *
 * Or use the service directly for advanced scenarios:
 *
 * ```kotlin
 * val signedEvent = eventSigningService.sign(event, "my-key-alias").getOrThrow()
 * val isValid = eventSigningService.verify(signedEvent).getOrThrow()
 * ```
 *
 * ## Key Management
 *
 * The service uses KMS key aliases to reference signing keys.
 * If no key alias is provided, a default event signing key is used
 * (configured via application settings).
 *
 * @see EventEncryptionService for event confidentiality
 */
@JsExportCompat
interface EventSigningService {
    /**
     * Sign an event.
     *
     * Creates a JWS signature of the event payload and metadata,
     * returning a new Event with the signature attached.
     *
     * @param event The event to sign
     * @param keyAlias KMS key alias to use (null = default key)
     * @return Signed event with EventSignature populated
     */
    suspend fun sign(
        event: Event,
        keyAlias: String? = null,
    ): IdkResult<Event, IdkError>

    /**
     * Verify an event's signature.
     *
     * Checks that the JWS signature is valid and the event
     * has not been tampered with.
     *
     * @param event The signed event to verify
     * @return True if signature is valid, false otherwise
     */
    suspend fun verify(event: Event): IdkResult<Boolean, IdkError>

    /**
     * Get the default signing key alias.
     *
     * @return The configured default key alias, or null if none configured
     */
    fun getDefaultKeyAlias(): String?

    /**
     * Check if an event is signed.
     *
     * @param event The event to check
     * @return True if the event has a signature
     */
    fun isSigned(event: Event): Boolean = event.signature != null

    /**
     * DI graph interface for AppScope contribution.
     */
    @SingleIn(AppScope::class)
    @ContributesTo(AppScope::class)
    @JsExportIgnoreCompat
    interface Graph {
        val eventSigningService: EventSigningService
    }
}
