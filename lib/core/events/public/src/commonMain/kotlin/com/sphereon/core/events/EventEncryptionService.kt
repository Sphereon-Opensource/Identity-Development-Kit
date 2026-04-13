/*
 * Copyright (c) 2025 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn

/**
 * Service for encrypting event data for confidentiality.
 *
 * EventEncryptionService uses the Key Management System (KMS) to
 * encrypt sensitive event data. Encrypted events include a JWE
 * (JSON Web Encryption) that protects specified parts of the event.
 *
 * ## Why Encrypt Events?
 *
 * - **Confidentiality**: Protect sensitive payload data
 * - **Context Privacy**: Hide session/tenant information from intermediaries
 * - **Compliance**: Meet regulatory requirements for data protection
 * - **Defense in Depth**: Additional protection layer beyond transport encryption
 *
 * ## Encrypted Parts
 *
 * You can choose which parts of the event to encrypt:
 * - **PAYLOAD**: Encrypt the event payload (most common)
 * - **CONTEXT**: Encrypt the event context (sessionId, tenantId, principalId)
 *
 * ## Usage
 *
 * Encryption is typically handled by EventService when `encrypt = true`:
 *
 * ```kotlin
 * eventService.emit(
 *     event,
 *     encrypt = true,
 *     encryptionKeyAlias = "events-encryption-key",
 *     encryptParts = setOf(EncryptedPart.PAYLOAD, EncryptedPart.CONTEXT)
 * )
 * ```
 *
 * Or use the service directly for advanced scenarios:
 *
 * ```kotlin
 * val encryptedEvent = eventEncryptionService.encrypt(
 *     event,
 *     keyAlias = "my-key",
 *     parts = setOf(EncryptedPart.PAYLOAD)
 * ).getOrThrow()
 *
 * val decryptedEvent = eventEncryptionService.decrypt(encryptedEvent).getOrThrow()
 * ```
 *
 * ## Key Management
 *
 * The service uses KMS key aliases to reference encryption keys.
 * Typically, a public key is used for encryption and the corresponding
 * private key is used for decryption.
 *
 * @see EventSigningService for event authenticity
 * @see EncryptedPart for specifying what to encrypt
 */
interface EventEncryptionService {

    /**
     * Encrypt specified parts of an event.
     *
     * Creates a JWE encryption of the specified event parts,
     * returning a new Event with the encryption info attached.
     * The original payload/context values are replaced with
     * encrypted placeholders.
     *
     * @param event The event to encrypt
     * @param keyAlias KMS key alias for the encryption key (null = default key)
     * @param parts Which parts of the event to encrypt (default: PAYLOAD only)
     * @return Encrypted event with EventEncryption populated
     */
    suspend fun encrypt(
        event: Event,
        keyAlias: String? = null,
        parts: Set<EncryptedPart> = setOf(EncryptedPart.PAYLOAD)
    ): IdkResult<Event, IdkError>

    /**
     * Decrypt an encrypted event.
     *
     * Decrypts the JWE and restores the original payload/context values.
     *
     * @param event The encrypted event to decrypt
     * @param keyAlias KMS key alias for the decryption key (null = derived from event)
     * @return Decrypted event with original payload/context restored
     */
    suspend fun decrypt(
        event: Event,
        keyAlias: String? = null
    ): IdkResult<Event, IdkError>

    /**
     * Get the default encryption key alias.
     *
     * @return The configured default key alias, or null if none configured
     */
    fun getDefaultKeyAlias(): String?

    /**
     * Check if an event is encrypted.
     *
     * @param event The event to check
     * @return True if the event has encryption
     */
    fun isEncrypted(event: Event): Boolean = event.encryption != null

    /**
     * Check which parts of an event are encrypted.
     *
     * @param event The event to check
     * @return Set of encrypted parts, or empty set if not encrypted
     */
    fun getEncryptedParts(event: Event): Set<EncryptedPart> =
        event.encryption?.encryptedParts ?: emptySet()

    /**
     * DI component interface for AppScope contribution.
     */
    @SingleIn(AppScope::class)
    @ContributesTo(AppScope::class)
    interface Component {
        val eventEncryptionService: EventEncryptionService
    }
}
