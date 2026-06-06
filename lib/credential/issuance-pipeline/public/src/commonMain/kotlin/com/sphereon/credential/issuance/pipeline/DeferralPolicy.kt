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

/**
 * Per-credential deferral policy: whether issuance may be deferred when required attributes are
 * not yet available, and the bounds on that deferral.
 *
 * Carried on a [CredentialClaimsBinding] and optionally overridden per session at offer
 * creation. Phase 1 ships the type; the deferral *decision* that consults it is a later phase.
 */
@JsExportCompat
@Serializable
data class DeferralPolicy(
    /** When false, missing required attributes fail the credential request rather than deferring. */
    val enabled: Boolean = false,
    /** Hint to the wallet for the `/deferred_credential` poll interval. */
    val pollIntervalSeconds: Int = 60,
    /** Max time from the first `/credential` 202 until the pipeline gives up. Defaults to 7 days. */
    val maxDeferralSeconds: Long = 7L * 24 * 3600,
    /**
     * When true, even with all required attributes present the session moves to an
     * awaiting-approval state rather than issuing — an explicit approval is required.
     */
    val approvalRequired: Boolean = false,
) {
    companion object {
        fun disabled(): DeferralPolicy = DeferralPolicy(enabled = false)
    }
}
