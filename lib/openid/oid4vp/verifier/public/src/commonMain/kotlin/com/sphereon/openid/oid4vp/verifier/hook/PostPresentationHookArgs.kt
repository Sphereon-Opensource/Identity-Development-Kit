/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vp.verifier.hook

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/**
 * Args delivered to every registered `hook.post-presentation.**` ServiceCommand
 * once the verifier has parsed + validated a wallet's authorization response in
 * `HandleDirectPostResponseCommandImpl`. Mirrors the
 * `PostIssuanceHookArgs` shape from the OID4VCI issuer side so consuming
 * modules can write near-identical subscribers.
 *
 * Hook contract:
 *  - Fires only on a successfully validated response. Validation failure
 *    short-circuits before dispatch.
 *  - Per-hook failures are isolated by the dispatcher; one failing hook does
 *    not affect the redirect URI returned to the wallet.
 *  - [boundInvitationToken] is the seam VDX uses to correlate a presentation
 *    back to an outstanding invitation (analogous to
 *    `PostIssuanceHookArgs.boundUsageToken`). Absent when the verifier
 *    session was created outside an invitation flow.
 */
@Serializable
@JsExportCompat
data class PostPresentationHookArgs(
    val tenantId: String,
    val authorizationSessionId: String?,
    val transactionId: String?,
    val state: String?,
    val nonce: String?,
    val verifierClientId: String,
    val matchedCredentialsCount: Int,
    val occurredAt: Instant,
    val boundInvitationToken: String? = null,
    val payload: JsonObject? = null,
)

/**
 * Return shape every hook produces. Mirrors `PostIssuanceHookResult` so
 * consumers can be written symmetrically.
 */
@Serializable
@JsExportCompat
data class PostPresentationHookResult(
    val handled: Boolean = true,
    val message: String? = null,
)
