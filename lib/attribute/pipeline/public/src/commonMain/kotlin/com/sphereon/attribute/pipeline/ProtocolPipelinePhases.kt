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
 * OID4VCI-specific [PipelinePhase] constants, mapping pipeline phases onto OID4VCI protocol
 * moments. (The generic, protocol-agnostic phases live on [PipelinePhase] itself.)
 */
object Oid4vciPipelinePhase {
    /** Authorization-code flow — OIDC federation, wallet VP at the AS. */
    val AUTHORIZATION = PipelinePhase("oid4vci_authorization")

    /** Pre-authorized-code flow — backend provides attributes upfront. */
    val PRE_AUTHORIZED = PipelinePhase("oid4vci_pre_authorized")

    /** Token exchange. */
    val TOKEN = PipelinePhase("oid4vci_token")

    /** Wallet calls `/credential`. */
    val CREDENTIAL_REQUEST = PipelinePhase("oid4vci_credential_request")

    /** Wallet polls `/deferred_credential`. */
    val DEFERRED = PipelinePhase("oid4vci_deferred")
}

/**
 * VC-API (VCALM) lifecycle [PipelinePhase] constants.
 */
object VcalmPipelinePhase {
    /** Issuer coordinator POSTs an unsigned credential to `/credentials/issue`. */
    val ISSUE_REQUEST = PipelinePhase("vcalm_issue_request")

    /** Post-signing enrichment (status-list registration, evidence attachment). */
    val POST_ISSUE = PipelinePhase("vcalm_post_issue")

    /** Verify-then-issue: a VCALM exchange verified a presentation, claims available. */
    val VERIFICATION_RESULT = PipelinePhase("vcalm_verification_result")
}
