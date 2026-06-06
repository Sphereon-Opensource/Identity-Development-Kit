/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.credential.issuance.pipeline

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How an [IssuancePipelineSession]'s sensitive payload (the attribute bag, lookup keys, approval
 * evidence) is protected at rest.
 *
 * Sealed; variants are top-level (mirroring [com.sphereon.attribute.flow.AttributeRetentionPolicy])
 * so kotlinx-serialization and JS export stay well-behaved.
 */
@JsExportCompat
@Serializable
sealed interface SessionEncryptionMode

/** No encryption — the payload is stored as plaintext. For local development / non-PII flows only. */
@Serializable
@SerialName("plaintext")
data object PlaintextMode : SessionEncryptionMode

/**
 * Tier 1: the payload is AEAD-encrypted under a per-session DEK that is itself wrapped by the
 * tenant KEK. Decryptable by the platform without any client-held secret.
 */
@Serializable
@SerialName("platform-encrypted")
data object PlatformEncryptedMode : SessionEncryptionMode

/**
 * Tier 2: the effective encryption key is additionally bound to the session's `correlationId`
 * via HKDF, so decryption requires re-presenting the `correlationId`. The platform alone cannot
 * read the payload.
 *
 * @property fallbackToPlatform when true, a session whose `correlationId` cannot be re-presented
 *   degrades to [PlatformEncryptedMode] semantics rather than being unreadable.
 */
@Serializable
@SerialName("client-bound")
data class ClientBoundMode(
    val fallbackToPlatform: Boolean = false,
) : SessionEncryptionMode
