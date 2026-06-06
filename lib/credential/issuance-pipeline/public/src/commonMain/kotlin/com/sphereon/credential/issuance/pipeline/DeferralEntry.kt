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
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Per-binding deferral state on an [IssuancePipelineSession] — present once a credential's
 * `/credential` request has returned 202. Keyed in the session by `bindingId`.
 *
 * Operational metadata only (ids and timestamps); it carries no PII and is stored plaintext
 * alongside the encrypted session payload.
 */
@JsExportCompat
@Serializable
data class DeferralEntry(
    /** The `transaction_id` handed to the wallet for `/deferred_credential` polling. */
    val transactionId: String,
    /** When this binding first deferred. */
    val firstDeferredAt: Instant,
    /** When the wallet last polled `/deferred_credential` for this binding, if ever. */
    val lastPolledAt: Instant? = null,
    /** How many times the wallet has polled. */
    val pollCount: Int = 0,
    /** `firstDeferredAt + deferralPolicy.maxDeferralSeconds` — the hard deadline. */
    val expiresAt: Instant,
)
