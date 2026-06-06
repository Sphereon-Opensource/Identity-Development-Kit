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

import com.sphereon.attribute.pipeline.AttributeSourceBinding
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.Serializable

/**
 * A registered pipeline: the ordered set of attribute-source bindings that feed it, the
 * credential-claims bindings it can assemble, and the lookup keys callers are expected to supply
 * at session init.
 *
 * The engine validates a configuration at registration time: every `consumedLookupKeys` value of
 * a bound source must either be a `producedLookupKeys` value of some other bound source or appear
 * in [expectedInitialLookupKeys]; the source dependency graph must be acyclic.
 */
@JsExportCompat
@Serializable
data class PipelineConfiguration(
    /** Stable identifier — referenced by [IssuancePipelineSession.pipelineId]. */
    val pipelineId: String,
    /** The attribute sources bound into this pipeline, each with its phases and binding config. */
    @JsExportIgnoreCompat
    val sourceBindings: List<AttributeSourceBinding> = emptyList(),
    /** The credentials this pipeline can assemble claims for. */
    @JsExportIgnoreCompat
    val claimsBindings: List<CredentialClaimsBinding> = emptyList(),
    /**
     * Lookup keys the caller is expected to provide at session init (from invitation context,
     * static-offer config, or the init call). A source may consume one of these without any
     * other source producing it.
     */
    @JsExportIgnoreCompat
    val expectedInitialLookupKeys: Set<String> = emptySet(),
)
