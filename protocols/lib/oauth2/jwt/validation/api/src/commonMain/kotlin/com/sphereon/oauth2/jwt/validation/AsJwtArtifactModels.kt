/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.oauth2.jwt.validation

import com.sphereon.crypto.resolution.IdentifierOptsOrResult
import com.sphereon.oauth2.common.model.CanonicalAuthorizationServerIssuer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Protocol JWTs whose signing authority may be admitted by an authorization-server trust
 * attachment. This is deliberately closed: credential JWTs, client assertions, DPoP proofs,
 * and request objects are not AS-issued trust artifacts.
 */
@Serializable
enum class JwtArtifactContext {
    ACCESS_TOKEN,
    ID_TOKEN,
    JARM_RESPONSE,
    LOGOUT_TOKEN,
}

/** Closed attachment scope for AS/OP-issued protocol artifacts. */
@Serializable
enum class AsJwtArtifactScope {
    ACCESS_TOKEN,
    ID_TOKEN,
    JARM_RESPONSE,
    LOGOUT_TOKEN,
    ;

    fun admits(context: JwtArtifactContext): Boolean = name == context.name
}

/** A scope admits a protocol artifact only when that exact closed value is present. */
fun Set<AsJwtArtifactScope>.admits(context: JwtArtifactContext): Boolean =
    any { it.admits(context) }

/**
 * Caller-established, immutable trust material for one persisted AS issuer.
 *
 * The public API carries no URL-discovered key set. The verifier receives a typed identifier
 * selected by the governed AS source/readiness path; omitting it is fail-closed.
 */
@Serializable
data class AsIssuerTrustMaterial(
    val canonicalIssuer: CanonicalAuthorizationServerIssuer,
    val artifactScopes: Set<AsJwtArtifactScope>,
    @Transient val trustedIdentifier: IdentifierOptsOrResult? = null,
) {
    init {
        require(artifactScopes.isNotEmpty()) { "AS trust material must admit at least one artifact scope" }
    }

    fun admits(context: JwtArtifactContext): Boolean = artifactScopes.admits(context)
}
