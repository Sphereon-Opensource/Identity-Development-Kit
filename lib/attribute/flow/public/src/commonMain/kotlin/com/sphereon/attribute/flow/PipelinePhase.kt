/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.attribute.flow

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * A phase in a flow that resolves attribute values incrementally.
 *
 * Flow-agnostic and extensible: the companion constants are the phases shared by every flow
 * (session init, IDV completion, credential assembly, post-issuance). Protocol-specific flows
 * (OID4VCI, VC-API, ...) contribute their own additional [PipelinePhase] constants from their
 * own modules; the value is an opaque string so no central enum has to enumerate them.
 *
 * Carried on every [AttributeRecord] so the bag records *when* each datum was contributed.
 */
@JsExportCompat
@Serializable
data class PipelinePhase(
    val value: String,
) {
    companion object {
        /** Session / offer creation — initial attributes injected by the caller. */
        val SESSION_INIT = PipelinePhase("session_init")

        /**
         * Generic, channel-neutral attribute resolution — the phase a non-credential caller (a form,
         * portal page, PDF/API render, or a standalone semantic-enrichment lookup) uses to resolve
         * attributes from bound sources without any issuance flow. Lets the pipeline drive every
         * channel, not only credential issuance.
         */
        val RESOLUTION = PipelinePhase("resolution")

        /** Identity verification completed — IDV results are available. */
        val IDV_COMPLETED = PipelinePhase("idv_completed")

        /** Credential assembly — final enrichment before credential construction. */
        val CREDENTIAL_ASSEMBLY = PipelinePhase("credential_assembly")

        /** Post-issuance — audit, cleanup, retention enforcement. */
        val POST_ISSUANCE = PipelinePhase("post_issuance")
    }
}
