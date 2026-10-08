/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.oauth2.jwt.validation

import kotlinx.serialization.Serializable

/**
 * Supported Identity Provider types.
 */
@Serializable
enum class IdpType {
    /** Generic OIDC-compliant provider */
    OIDC,

    /** Keycloak */
    KEYCLOAK,

    /** Azure AD / Microsoft Entra ID */
    AZURE_AD,

    /** Auth0 */
    AUTH0,

    /** Okta */
    OKTA,

    /** Custom provider with manual JWKS configuration */
    CUSTOM,
}

/**
 * Configuration for a single Identity Provider.
 *
 * Supports multiple deployment scenarios:
 * - Kubernetes with service discovery
 * - Docker Compose with container networking
 * - Bare metal with direct URLs
 *
 * @property id Unique identifier for this IdP configuration
 * @property type Type of identity provider (affects discovery behavior)
 * @property issuer The expected token issuer (iss claim value)
 * @property audience Expected audience (aud claim value)
 * @property jwksUri Direct JWKS URI (optional, discovered via OIDC if not set)
 * @property discoveryUri OIDC discovery endpoint (defaults to issuer/.well-known/openid-configuration)
 * @property tenantClaim Claim name containing the tenant identifier
 * @property tenantClaimAlternatives Alternative claim names for tenant (tried in order if primary missing)
 * @property allowedAlgorithms JWT algorithms allowed for this IdP
 * @property clockSkewSeconds Allowed clock skew for exp/nbf validation
 * @property jwksCacheTtlSeconds How long to cache JWKS responses
 * @property requiredClaims Claims that must be present in the token
 */
@Serializable
data class IdpConfig(
    val id: String,
    val type: IdpType = IdpType.OIDC,
    val issuer: String,
    val audience: String? = null,
    val jwksUri: String? = null,
    val discoveryUri: String? = null,
    val tenantClaim: String = "tenant_id",
    val tenantClaimAlternatives: List<String> = listOf("azp", "client_id"),
    val allowedAlgorithms: List<String> = listOf("RS256", "ES256"),
    val clockSkewSeconds: Long = 60,
    val jwksCacheTtlSeconds: Long = 3600,
    val requiredClaims: List<String> = emptyList(),
) {
    /**
     * Get the effective discovery URI for OIDC metadata.
     */
    fun getEffectiveDiscoveryUri(): String = discoveryUri ?: "$issuer/.well-known/openid-configuration"

    companion object {
        /**
         * Create a minimal OIDC IdP configuration.
         */
        fun oidc(
            id: String,
            issuer: String,
            audience: String? = null,
        ) = IdpConfig(
            id = id,
            type = IdpType.OIDC,
            issuer = issuer,
            audience = audience,
        )

        /**
         * Create a Keycloak IdP configuration.
         */
        fun keycloak(
            id: String,
            baseUrl: String,
            realm: String,
            audience: String? = null,
        ) = IdpConfig(
            id = id,
            type = IdpType.KEYCLOAK,
            issuer = "$baseUrl/realms/$realm",
            audience = audience,
            tenantClaim = "tenant_id",
            tenantClaimAlternatives = listOf("azp", "resource_access"),
        )

        /**
         * Create an Azure AD IdP configuration.
         */
        fun azureAd(
            id: String,
            tenantId: String,
            audience: String? = null,
        ) = IdpConfig(
            id = id,
            type = IdpType.AZURE_AD,
            issuer = "https://login.microsoftonline.com/$tenantId/v2.0",
            audience = audience,
            tenantClaim = "tid",
            tenantClaimAlternatives = listOf("oid"),
        )

        /**
         * Create an Auth0 IdP configuration.
         */
        fun auth0(
            id: String,
            domain: String,
            audience: String? = null,
        ) = IdpConfig(
            id = id,
            type = IdpType.AUTH0,
            issuer = "https://$domain/",
            audience = audience,
            tenantClaim = "org_id",
            tenantClaimAlternatives = listOf("azp"),
        )

        /**
         * Create a custom IdP with direct JWKS URI.
         */
        fun custom(
            id: String,
            issuer: String,
            jwksUri: String,
            audience: String? = null,
        ) = IdpConfig(
            id = id,
            type = IdpType.CUSTOM,
            issuer = issuer,
            jwksUri = jwksUri,
            audience = audience,
        )
    }
}

/**
 * Global JWT validation configuration.
 *
 * @property enabled Whether JWT validation is enabled
 * @property defaultIdp Default IdP configuration used when no tenant-specific IdP is found
 * @property tenantIdps Per-tenant IdP overrides
 * @property anonymous Anonymous access configuration
 * @property strictIssuerMatching When `true`, `IdpRegistry.getIdpByIssuer` returns
 * `Err(UntrustedIssuer)` for issuers that are not explicitly registered. When `false`,
 * unknown issuers fall back to [defaultIdp] if one is configured. Default is `true`
 * (fail-closed) so production BYO deployments never silently accept unknown issuers.
 * Operators may flip to `false` in dev profiles where a permissive fallback is useful.
 */
@Serializable
data class JwtValidationConfig(
    val enabled: Boolean = true,
    val defaultIdp: IdpConfig? = null,
    val tenantIdps: Map<String, IdpConfig> = emptyMap(),
    val anonymous: AnonymousAccessConfig = AnonymousAccessConfig(),
    val strictIssuerMatching: Boolean = true,
)

/**
 * Configuration for anonymous (unauthenticated) access.
 *
 * @property allowed Whether anonymous access is permitted at all
 * @property allowedPaths Paths that allow anonymous access (glob patterns)
 */
@Serializable
data class AnonymousAccessConfig(
    val allowed: Boolean = false,
    val allowedPaths: List<String> = emptyList(),
)
