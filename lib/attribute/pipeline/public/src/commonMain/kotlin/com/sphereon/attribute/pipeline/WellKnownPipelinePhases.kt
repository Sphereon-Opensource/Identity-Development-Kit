/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.attribute.pipeline

import com.sphereon.attribute.flow.PipelinePhase

/**
 * The set of phases a phase-agnostic [AttributeSource] declares as supported — the generic
 * [PipelinePhase] constants plus the OID4VCI protocol phases.
 *
 * A source that genuinely runs in any phase declares [ALL]; the per-pipeline
 * [AttributeSourceBinding.phases] is what actually scopes it within a given pipeline.
 */
object WellKnownPipelinePhases {
    val ALL: Set<PipelinePhase> =
        setOf(
            PipelinePhase.SESSION_INIT,
            PipelinePhase.RESOLUTION,
            PipelinePhase.IDV_COMPLETED,
            PipelinePhase.CREDENTIAL_ASSEMBLY,
            PipelinePhase.POST_ISSUANCE,
            Oid4vciPipelinePhase.AUTHORIZATION,
            Oid4vciPipelinePhase.PRE_AUTHORIZED,
            Oid4vciPipelinePhase.TOKEN,
            Oid4vciPipelinePhase.CREDENTIAL_REQUEST,
            Oid4vciPipelinePhase.DEFERRED,
        )
}
