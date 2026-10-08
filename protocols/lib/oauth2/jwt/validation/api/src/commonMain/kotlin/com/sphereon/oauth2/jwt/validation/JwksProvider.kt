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

import com.sphereon.core.api.IdkResult
import kotlinx.serialization.Serializable

/**
 * OIDC Discovery metadata.
 *
 * Contains the essential fields from OpenID Connect Discovery.
 * Used to discover the jwks_uri for an issuer.
 *
 * Note: Key resolution itself is handled by IDK's identifier resolution services:
 * - IJwksUrlExternalIdentifierResolutionService for JWKS URL resolution
 * - IMultiIdentifierResolutionService for unified key resolution
 */
@Serializable
data class OidcDiscoveryMetadata(
    /** Issuer identifier */
    val issuer: String,
    /** JWKS URI - passed to IJwksUrlExternalIdentifierResolutionService */
    val jwksUri: String,
    /** Authorization endpoint */
    val authorizationEndpoint: String?,
    /** Token endpoint */
    val tokenEndpoint: String?,
    /** UserInfo endpoint */
    val userinfoEndpoint: String?,
    /** Supported response types */
    val responseTypesSupported: List<String>?,
    /** Supported subject types */
    val subjectTypesSupported: List<String>?,
    /** Supported ID token signing algorithms */
    val idTokenSigningAlgValuesSupported: List<String>?,
    /** Supported scopes */
    val scopesSupported: List<String>?,
)

/**
 * OIDC Discovery service for fetching IdP metadata.
 *
 * Responsible for:
 * 1. Fetching and caching .well-known/openid-configuration
 * 2. Extracting jwks_uri for use with IDK's identifier resolution
 *
 * Key resolution is delegated to IDK's IJwksUrlExternalIdentifierResolutionService.
 */
interface OidcDiscoveryService {
    /**
     * Fetch OIDC discovery metadata for an issuer.
     *
     * @param issuer The issuer URL (used to construct discovery endpoint)
     * @return Discovery metadata or error
     */
    suspend fun discover(issuer: String): IdkResult<OidcDiscoveryMetadata, JwtValidationError>

    /**
     * Get cached metadata or fetch if expired.
     *
     * @param issuer The issuer URL
     * @return Cached or fresh discovery metadata
     */
    suspend fun getMetadata(issuer: String): IdkResult<OidcDiscoveryMetadata, JwtValidationError>

    /**
     * Invalidate cached metadata for an issuer.
     *
     * Called when key resolution fails, suggesting possible key rotation.
     *
     * @param issuer The issuer URL
     */
    suspend fun invalidateCache(issuer: String)
}
