/* Copyright 2026 Sphereon International B.V. Licensed under Apache-2.0. */
package com.sphereon.oauth2.server.authorization.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

/** Minimal normalized evidence. Raw tokens, codes and secret material have no fields here. */
@Serializable
data class NormalizedAuthenticationEvidence(
    val hostedAuthorizationServerId: String,
    val federationBindingId: String?,
    val upstreamIssuer: String?,
    val upstreamSubject: String?,
    val localSubject: String,
    val acr: String?,
    val amr: List<String>,
    val authTime: Instant,
    val governedClaims: Map<String, ProvenancedAuthenticationClaim>,
    val downstreamTransactionId: String,
    val upstreamTransactionId: String?,
    val validatedAt: Instant,
    val hostedAuthorizationServerRevision: Long,
    val federationBindingRevision: Long?,
    val upstreamResourceRevision: Long?,
)

@Serializable
data class ProvenancedAuthenticationClaim(
    val value: JsonElement,
    val sourceClaim: String,
    val issuer: String,
)
