/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.oauth2.client.token

import com.sphereon.core.api.IdkResult
import com.sphereon.oauth2.common.error.Oauth2Error
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import kotlin.time.Clock
import kotlin.time.Instant

/** Application-layer access-token binding selected for the protected resource. */
enum class OAuth2AccessTokenBindingMode {
    BEARER,
    CERTIFICATE_BOUND,
}

/**
 * Non-serializable, normalized identity of one locally cacheable access-token acquisition.
 *
 * This key deliberately contains no credentials, assertions, tokens, private-key selectors, or
 * certificate bytes. Security-policy and identity revisions are part of equality so a change in
 * any governed input cannot reuse a token acquired under an older decision.
 */
class OAuth2AccessTokenCacheKey(
    tenantId: String,
    connectorId: String,
    profileRevision: String,
    effectiveTokenEndpoint: String,
    scopes: Collection<String> = emptyList(),
    resources: Collection<String> = emptyList(),
    audiences: Collection<String> = emptyList(),
    egressRevision: String,
    trustDomainRevision: String,
    anchorSetDigest: String,
    clientCertificateFingerprint: String? = null,
    val authenticationMethod: ClientAuthenticationMethod,
    val accessTokenBindingMode: OAuth2AccessTokenBindingMode,
) {
    val tenantId: String = tenantId.required("tenantId")
    val connectorId: String = connectorId.required("connectorId")
    val profileRevision: String = profileRevision.required("profileRevision")
    val effectiveTokenEndpoint: String = effectiveTokenEndpoint.required("effectiveTokenEndpoint")
    val scopes: List<String> = normalizeOAuth2CacheSelectors(scopes)
    val resources: List<String> = normalizeOAuth2CacheSelectors(resources)
    val audiences: List<String> = normalizeOAuth2CacheSelectors(audiences)
    val egressRevision: String = egressRevision.required("egressRevision")
    val trustDomainRevision: String = trustDomainRevision.required("trustDomainRevision")
    val anchorSetDigest: String = anchorSetDigest.required("anchorSetDigest")
    val clientCertificateFingerprint: String? = clientCertificateFingerprint?.required("clientCertificateFingerprint")

    init {
        val requiresCertificate = authenticationMethod.isMutualTls() ||
            accessTokenBindingMode == OAuth2AccessTokenBindingMode.CERTIFICATE_BOUND
        require(!requiresCertificate || this.clientCertificateFingerprint != null) {
            "Certificate-bound access tokens and mutual-TLS authentication require a client certificate fingerprint"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OAuth2AccessTokenCacheKey) return false
        return tenantId == other.tenantId &&
            connectorId == other.connectorId &&
            profileRevision == other.profileRevision &&
            effectiveTokenEndpoint == other.effectiveTokenEndpoint &&
            scopes == other.scopes &&
            resources == other.resources &&
            audiences == other.audiences &&
            egressRevision == other.egressRevision &&
            trustDomainRevision == other.trustDomainRevision &&
            anchorSetDigest == other.anchorSetDigest &&
            clientCertificateFingerprint == other.clientCertificateFingerprint &&
            authenticationMethod == other.authenticationMethod &&
            accessTokenBindingMode == other.accessTokenBindingMode
    }

    override fun hashCode(): Int {
        var result = tenantId.hashCode()
        result = 31 * result + connectorId.hashCode()
        result = 31 * result + profileRevision.hashCode()
        result = 31 * result + effectiveTokenEndpoint.hashCode()
        result = 31 * result + scopes.hashCode()
        result = 31 * result + resources.hashCode()
        result = 31 * result + audiences.hashCode()
        result = 31 * result + egressRevision.hashCode()
        result = 31 * result + trustDomainRevision.hashCode()
        result = 31 * result + anchorSetDigest.hashCode()
        result = 31 * result + (clientCertificateFingerprint?.hashCode() ?: 0)
        result = 31 * result + authenticationMethod.hashCode()
        result = 31 * result + accessTokenBindingMode.hashCode()
        return result
    }

    override fun toString(): String =
        "OAuth2AccessTokenCacheKey(" +
            "tenantId=$tenantId, connectorId=$connectorId, profileRevision=$profileRevision, " +
            "effectiveTokenEndpoint=<redacted>, scopes=<redacted:${scopes.size}>, " +
            "resources=<redacted:${resources.size}>, audiences=<redacted:${audiences.size}>, " +
            "egressRevision=$egressRevision, trustDomainRevision=$trustDomainRevision, " +
            "anchorSetDigest=$anchorSetDigest, clientCertificateFingerprint=${clientCertificateFingerprint.redactedPresence()}, " +
            "authenticationMethod=$authenticationMethod, accessTokenBindingMode=$accessTokenBindingMode)"
}

/** A memory-only access token. Its string representation never renders the token. */
class OAuth2CachedAccessToken(
    val accessToken: String,
    val tokenType: String,
    val scope: String?,
    val expiresAt: Instant?,
) {
    init {
        require(accessToken.isNotBlank()) { "Access token must not be blank" }
        require(tokenType.isNotBlank()) { "Token type must not be blank" }
    }

    override fun toString(): String = "OAuth2CachedAccessToken(<redacted>)"
}

/** Injectable commonMain clock used to make expiry and skew deterministic. */
fun interface OAuth2AccessTokenClock {
    fun now(): Instant

    companion object {
        val System: OAuth2AccessTokenClock = OAuth2AccessTokenClock { Clock.System.now() }
    }
}

/**
 * Protected-resource response observed by the once-only 401 policy. The response value is omitted
 * from [toString] because it may itself contain protected data.
 */
class OAuth2ProtectedResourceResponse<T>(
    val statusCode: Int,
    val value: T,
) {
    override fun toString(): String = "OAuth2ProtectedResourceResponse(statusCode=$statusCode, value=<redacted>)"
}

/**
 * Local, non-serializing token cache and acquisition coordinator.
 *
 * Credential material remains in [OAuth2TokenEndpointTransportArgs] for the duration of the call;
 * it is never copied into the cache key or value. Implementations must not persist refresh tokens.
 */
interface OAuth2AccessTokenCacheCoordinator {
    suspend fun getOrAcquire(
        key: OAuth2AccessTokenCacheKey,
        transportArgs: OAuth2TokenEndpointTransportArgs,
    ): IdkResult<OAuth2CachedAccessToken, Oauth2Error>

    suspend fun evict(key: OAuth2AccessTokenCacheKey)

    suspend fun invalidateTenant(tenantId: String)

    suspend fun <T> executeProtectedResource(
        key: OAuth2AccessTokenCacheKey,
        transportArgs: OAuth2TokenEndpointTransportArgs,
        request: suspend (OAuth2CachedAccessToken) -> OAuth2ProtectedResourceResponse<T>,
    ): IdkResult<OAuth2ProtectedResourceResponse<T>, Oauth2Error>
}

/** Canonical sort/dedup normalization shared by cache-key construction and request validation. */
fun normalizeOAuth2CacheSelectors(values: Collection<String>): List<String> =
    values.asSequence().map(String::trim).filter(String::isNotEmpty).distinct().sorted().toList()

private fun String.required(name: String): String = trim().also { require(it.isNotEmpty()) { "$name must not be blank" } }

private fun String?.redactedPresence(): String = if (this == null) "null" else "<redacted>"

private fun ClientAuthenticationMethod.isMutualTls(): Boolean =
    this == ClientAuthenticationMethod.TLS_CLIENT_AUTH || this == ClientAuthenticationMethod.SELF_SIGNED_TLS_CLIENT_AUTH


