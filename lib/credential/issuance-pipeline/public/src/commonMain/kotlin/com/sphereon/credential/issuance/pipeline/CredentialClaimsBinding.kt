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

import com.sphereon.attribute.flow.AttributeProvenanceRef
import com.sphereon.attribute.flow.PipelinePhase
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.credential.claims.mapper.api.model.ClaimMappingConfiguration
import kotlinx.serialization.Serializable

/**
 * Binds one credential (matching a `credential_configuration_id`) to the pipeline: the semantic
 * attribute set it draws from, an optional claim-name mapping, which sources feed it per phase,
 * and its deferral policy.
 *
 * It does NOT restate selective-disclosure flags, data types, or mandatory-ness — those are
 * resolved from [semanticAttributeSetRef] by the OCA-backed credential-design service.
 */
@JsExportCompat
@Serializable
data class CredentialClaimsBinding(
    /** Matches the credential's `credential_configuration_id`. */
    val id: String,
    /**
     * The semantic attribute set (OCA bundle / vocabulary subset) this credential draws from.
     * The credential-design service resolves SD policy + mandatory claims from it.
     */
    val semanticAttributeSetRef: SemanticAttributeSetRef,
    /**
     * Optional mapping FROM semantic-model attribute names TO the credential's claim structure
     * (renaming / restructuring per vct / doctype). Null when names map 1:1. NOT where selective
     * disclosure is decided.
     */
    val claimMappingConfigId: String? = null,
    /** Inline alternative to [claimMappingConfigId]. */
    val inlineClaimMappingConfig: ClaimMappingConfiguration? = null,
    /** Which of the pipeline's sources this credential consumes, per phase. */
    @JsExportIgnoreCompat
    val consumedSources: Map<PipelinePhase, List<AttributeProvenanceRef>> = emptyMap(),
    /** Per-credential deferral policy. */
    val deferralPolicy: DeferralPolicy = DeferralPolicy.disabled(),
)
