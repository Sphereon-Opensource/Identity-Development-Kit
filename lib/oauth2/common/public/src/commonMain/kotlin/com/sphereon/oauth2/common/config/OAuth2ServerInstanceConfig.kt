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
 */

package com.sphereon.oauth2.common.config

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * Configuration for a single authorization server instance.
 *
 * Each instance can operate in HOSTED mode (IDK serves as the AS)
 * or EXTERNAL mode (IDK connects to this AS as an OAuth2 client).
 */
@JsExportCompat
@Suppress("NON_EXPORTABLE_TYPE")
@Serializable
data class OAuth2ServerInstanceConfig(
    // Identity
    val mode: AuthorizationServerMode = AuthorizationServerMode.HOSTED,
    val issuerTemplate: String? = null,
    val issuer: String? = null,
    val baseUrl: String = "http://localhost:8080",
    // Token lifetimes (HOSTED mode)
    val accessTokenLifetimeSeconds: Int = 3600,
    val refreshTokenLifetimeSeconds: Int = 86400,
    val authorizationCodeLifetimeSeconds: Int = 600,
    val tokenFormat: TokenFormat = TokenFormat.JWT,
    val refreshTokenRotation: Boolean = true,
    // Grant types & response types
    val grantTypesEnabled: Set<String> = setOf("authorization_code", "client_credentials", "refresh_token"),
    val responseTypesSupported: Set<String> = setOf("code"),
    val scopesSupported: List<String>? = null,
    // OpenID Connect
    val oidc: FeaturePolicy = FeaturePolicy.DISABLED,
    val idTokenLifetimeSeconds: Int = 3600,
    val subjectTypesSupported: List<String> = listOf("public"),
    val claimsSupported: List<String>? = null,
    val userinfoSigningAlgValuesSupported: Set<String>? = null,
    // Feature policies
    val introspection: FeaturePolicy = FeaturePolicy.SUPPORTED,
    val revocation: FeaturePolicy = FeaturePolicy.SUPPORTED,
    val par: FeaturePolicy = FeaturePolicy.DISABLED,
    val tokenExchange: FeaturePolicy = FeaturePolicy.DISABLED,
    val pkce: FeaturePolicy = FeaturePolicy.REQUIRED,
    val dpop: FeaturePolicy = FeaturePolicy.DISABLED,
    val iae: FeaturePolicy = FeaturePolicy.DISABLED,
    // Auth methods
    val pkceMethodsSupported: Set<String> = setOf("S256"),
    val tokenEndpointAuthMethodsSupported: Set<String> = setOf("client_secret_basic", "client_secret_post"),
    val introspectionEndpointAuthMethodsSupported: Set<String> = setOf("client_secret_basic"),
    val revocationEndpointAuthMethodsSupported: Set<String> = setOf("client_secret_basic", "client_secret_post"),
    val dpopSigningAlgValuesSupported: Set<String>? = null,
    // Attestation-based client auth (draft-ietf-oauth-attestation-based-client-auth)
    val attestation: FeaturePolicy = FeaturePolicy.DISABLED,
    val attestationChallengeRequired: Boolean = false,
    val clientAttestationSigningAlgValuesSupported: Set<String>? = null,
    val clientAttestationPopSigningAlgValuesSupported: Set<String>? = null,
    val attestationMaxLifetimeSeconds: Int = 3600,
    val attestationPopMaxAgeSeconds: Int = 120,
    // Signing key
    val signingKeyAlias: String? = null, // KMS key alias for signing. null = auto (use first key, or create one)
    // Algorithms (null = auto-discover from KMS for HOSTED, or from metadata for EXTERNAL)
    val signingAlgorithmsSupported: Set<String>? = null,
    val idTokenSigningAlgValuesSupported: Set<String>? = null,
    val requestObjectSigningAlgValuesSupported: Set<String>? = null,
    // EXTERNAL mode: client credentials for connecting to this AS
    val tokenEndpointAuthMethod: String? = null,
    val clientId: String? = null,
    val clientSecret: String? = null,
    // EXTERNAL mode: endpoint overrides (if not using metadata discovery)
    val tokenEndpoint: String? = null,
    val introspectionEndpoint: String? = null,
    val revocationEndpoint: String? = null,
    val jwksUri: String? = null,
    // Internal service-to-service clients (role → clientId, clientSecret)
    val internalClients: Map<String, Pair<String, String>> = emptyMap(),
    // Public client policy for authorization code flow (OID4VCI wallets)
    val publicClients: PublicClientConfig = PublicClientConfig(),
) {
    companion object {
        const val CONFIG_PREFIX = "oauth2.servers"
    }
}

/**
 * Configuration for accepting public OAuth2 clients (e.g., OID4VCI wallets).
 *
 * Public clients use PKCE for security and do not authenticate with a client secret.
 */
@JsExportCompat
@Serializable
data class PublicClientConfig(
    val allowAny: Boolean = false,
    val allowedClientIds: List<String> = emptyList(),
)
