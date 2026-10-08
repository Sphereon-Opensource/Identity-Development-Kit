/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

@file:OptIn(ExperimentalTime::class)

package com.sphereon.openid.oid4vci.issuer.hook

import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedWalletInstanceAttestationEvidence
import com.sphereon.openid.oid4vci.issuer.proof.VerifiedKeyAttestation
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Neutral args DTO carried to every post-issuance hook `ServiceCommand`.
 * Hooks register under the `hook.post-issuance.*` command-id convention;
 * the issuer resolves the configured set at runtime and dispatches to each
 * whose [com.sphereon.core.api.service.ServiceCommand.supports] returns
 * true.
 *
 * Bytes vs. metadata: [credentialResponse] carries the signed credential
 * bytes + format verbatim. Hooks that only need metadata (audit, revocation
 * list) ignore the payload; hooks that forward to external systems
 * (webhooks, SIEM with payload) have them at hand without an extra fetch.
 * No flag is needed on the args — responsibility lives with the hook that
 * knows what it wants.
 */
data class PostIssuanceHookArgs(
    /** The freshly signed credential + format + any protocol-level transcript items. */
    val credentialResponse: CredentialResponse,
    /** Effective credential-configuration identifier (spec: OID4VCI credential_configuration_id). */
    val credentialConfigurationId: String?,
    /** Tenant that owns the issuance. */
    val tenantId: String,
    /** When the signed credential was emitted (from the issuer's clock). */
    val issuedAt: Instant,
    /**
     * Opaque reference to the invitation / pre-auth usage token this issuance
     * consumed, if any. Populated for redemption-originated credentials so
     * downstream hooks (e.g. `redemption-consume`) can flip the batch row to
     * CONSUMED. Null for direct issuance flows that don't use invitation
     * binding.
     */
    val boundUsageToken: String? = null,
    /** Pre-authorized code associated with this issuance, if the pre-auth flow was used. */
    val preAuthCode: String? = null,
    /**
     * Verified Wallet Unit key-attestation evidence summaries from credential-request proof
     * verification. Hooks can use the status references later for PID revocation tracking.
     */
    val keyAttestations: List<VerifiedKeyAttestation> = emptyList(),
    /**
     * Persisted Wallet Unit WIA/status/trust evidence accepted by the AS at PAR/token time.
     * Present only for production Wallet Unit-bound OID4VCI issuance.
     */
    val walletInstanceAttestation: ValidatedWalletInstanceAttestationEvidence? = null,
    /**
     * Stable local identifier (Uuid string) of the subject the credential
     * was issued to, if known. Some flows issue without a local Identity
     * (e.g. walk-up self-attestation); in that case [subject] may be null.
     */
    val subject: String? = null,
)

/**
 * Result returned by a hook `ServiceCommand`. Trivially extensible; today
 * only carries [handled] so the dispatcher / tests can observe whether the
 * hook ran its own logic or short-circuited via
 * [com.sphereon.core.api.service.ServiceCommand.supports] / internal gate.
 */
data class PostIssuanceHookResult(
    val handled: Boolean,
)
