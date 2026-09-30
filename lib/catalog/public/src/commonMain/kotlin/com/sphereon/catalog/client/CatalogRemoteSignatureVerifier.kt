/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.client

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * Identifies the trust domain and the local catalog a remote TS 11 listing is imported into.
 * Signer trust is decided by the domain, never by the remote server.
 */
data class CatalogRemoteTrustScope(
    val domainId: String,
    val catalogId: String,
)

/** What was proven about a signed remote listing. */
data class CatalogRemoteSignatureEvidence(
    val issuer: String,
    val keyId: String,
    val algorithm: String,
    val keyFingerprint: String? = null,
    val anchorId: String? = null,
)

/**
 * A decoded remote body plus its signature evidence. [signature] is null when the listing was
 * unsigned and the domain policy accepted it.
 */
data class VerifiedRemoteBody<T>(
    val value: T,
    val signature: CatalogRemoteSignatureEvidence?,
)

/**
 * SPI implemented by the layer that owns trust domains. Implementations verify a compact JWS
 * against the domain's CATALOG_SIGNER anchors and decide whether unsigned listings are acceptable.
 */
interface CatalogRemoteSignatureVerifier {
    suspend fun verifySigned(
        scope: CatalogRemoteTrustScope,
        compactJws: String,
    ): IdkResult<CatalogRemoteSignatureEvidence, IdkError>

    suspend fun allowsUnsigned(scope: CatalogRemoteTrustScope): IdkResult<Boolean, IdkError>
}
