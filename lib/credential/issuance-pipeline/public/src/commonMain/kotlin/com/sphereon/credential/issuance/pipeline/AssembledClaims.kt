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

import com.sphereon.attribute.flow.AttributeEvidence
import com.sphereon.attribute.flow.AttributeKey
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The output of a [CredentialClaimsAssembler]: the claims input that feeds the issuer core's
 * existing `IssuanceContext` / `CredentialFormatHandler` path.
 *
 * It is NOT a credential, and deliberately carries no signing material, no `sdPolicies`, and no
 * `mandatoryClaims` — selective disclosure and mandatory-ness are resolved by the OCA-backed
 * credential-design service, and signing is the format handler's job. The pipeline's
 * responsibility ends at "here are the resolved, mapped claims plus the key/evidence refs to
 * hand through".
 */
@JsExportCompat
@Serializable
data class AssembledClaims(
    /** The [CredentialClaimsBinding.id] this output was assembled for. */
    val bindingId: String,
    /**
     * The attribute map that flows into `IssuanceContext.attributes`. Claim names already mapped
     * to the credential's structure; values resolved and priority-merged from the bag.
     */
    @JsExportIgnoreCompat
    val attributes: Map<String, JsonElement>,
    /** Holder binding key, if the pipeline gathered one — handed through to the format handler. */
    val holderKey: AttributeKey? = null,
    /** Issuer signing key reference, if the pipeline gathered one — handed through, not used here. */
    val issuerKeyRef: AttributeKey? = null,
    /** Evidence the pipeline gathered, handed through to the format handler / builder. */
    @JsExportIgnoreCompat
    val evidence: List<AttributeEvidence> = emptyList(),
)
