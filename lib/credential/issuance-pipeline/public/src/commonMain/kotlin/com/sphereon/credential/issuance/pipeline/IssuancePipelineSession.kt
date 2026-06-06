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

import com.sphereon.attribute.flow.AttributeBag
import com.sphereon.attribute.flow.PipelinePhase
import com.sphereon.attribute.pipeline.LookupKeySet
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

/**
 * The EDK pipeline engine's session entity: the accumulated [AttributeBag] and [LookupKeySet]
 * for one issuance flow, plus the per-binding deferral / approval state and the lifecycle status.
 *
 * It is a *distinct* entity from the issuer-core `IssuanceSession`: the issuer core owns the
 * OID4VCI protocol session, this owns the multi-source attribute pipeline state. The two are
 * joined by [correlationId]. The sensitive payload ([bag], [lookupKeys], [approval]) is protected
 * at rest per [encryptionMode]; the lifecycle fields are operational and stored plaintext.
 */
@JsExportCompat
@Serializable
data class IssuancePipelineSession(
    /** Stable primary identifier. */
    val sessionId: String,
    /** The pipeline this session runs — carried on the session so a phase can be run without a separate registry lookup. */
    val pipelineConfiguration: PipelineConfiguration,
    /** The tenant this session belongs to. */
    val tenantId: String,
    /**
     * Which VDX issuer instance this session belongs to. Reserved for the multi-issuer-per-tenant
     * work; null for single-issuer deployments.
     */
    val issuerPartyId: String? = null,
    /** Lifecycle status. */
    val status: IssuancePipelineStatus,
    /** External correlation handle — the join key to the issuer-core session and all ingress paths. */
    val correlationId: String,
    /** Attributes accumulated across every phase run so far. Encrypted at rest per [encryptionMode]. */
    val bag: AttributeBag = AttributeBag.empty(),
    /** Lookup keys accumulated so far. Encrypted at rest per [encryptionMode]. */
    val lookupKeys: LookupKeySet = LookupKeySet.empty(),
    /** Phases that have completed for this session. */
    @JsExportIgnoreCompat
    val completedPhases: Set<PipelinePhase> = emptySet(),
    /** The phase currently executing, if any. */
    val currentPhase: PipelinePhase? = null,
    /** Protocol-specific context the front-end adapter carries through phases (e.g. `issuer_state`). */
    @JsExportIgnoreCompat
    val protocolContext: Map<String, JsonElement> = emptyMap(),
    /** Per-binding deferral state, keyed by `bindingId`. Operational metadata; stored plaintext. */
    @JsExportIgnoreCompat
    val deferralEntries: Map<String, DeferralEntry> = emptyMap(),
    /** Approval-gate state, present when a bound credential requires approval. Encrypted at rest. */
    val approval: ApprovalState? = null,
    /** How [bag] / [lookupKeys] / [approval] are protected at rest. */
    val encryptionMode: SessionEncryptionMode = PlatformEncryptedMode,
    val createdAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant,
) {
    /** The id of the [pipelineConfiguration] this session runs. */
    val pipelineId: String get() = pipelineConfiguration.pipelineId
}
