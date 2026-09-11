/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.openid.oid4vp.common

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.Serializable

/**
 * Governed trust roots for OID4VP request-object and client identifier validation. A transmitted
 * x5c chain is identity evidence only; the roots it must chain to come from this provider at every
 * request boundary so disable, removal, rotation and validity changes are observed immediately.
 */
interface Oid4vpRequestTrustMaterialProvider {
    suspend fun resolve(): IdkResult<Oid4vpRequestTrustMaterial, IdkError>
}

@Serializable
data class Oid4vpRequestTrustMaterial(
    val x509: List<Oid4vpX509TrustAnchor> = emptyList(),
)

@Serializable
data class Oid4vpX509TrustAnchor(
    val certificatePem: String,
) {
    init {
        require(certificatePem.isNotBlank()) { "oid4vp_x509_trust_anchor_blank" }
    }
}
