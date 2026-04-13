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

package com.sphereon.oauth2.jwt.validation.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.FetchAuthorizationServerMetadataCommand
import com.sphereon.oauth2.client.command.FetchServerMetadataArgs
import com.sphereon.oauth2.jwt.validation.JwtValidationError
import com.sphereon.oauth2.jwt.validation.OidcDiscoveryMetadata
import com.sphereon.oauth2.jwt.validation.OidcDiscoveryService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock

/**
 * Default implementation of OidcDiscoveryService.
 *
 * Wraps IDK's FetchAuthorizationServerMetadataCommand for OIDC discovery,
 * with in-memory caching to reduce network requests.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OidcDiscoveryService>())
class DefaultOidcDiscoveryService(
    private val fetchMetadataCommand: FetchAuthorizationServerMetadataCommand,
    private val cacheTtlSeconds: Long = 3600,
) : OidcDiscoveryService {
    private data class CachedMetadata(
        val metadata: OidcDiscoveryMetadata,
        val expiresAt: Long,
    )

    private val cache = mutableMapOf<String, CachedMetadata>()

    override suspend fun discover(issuer: String): IdkResult<OidcDiscoveryMetadata, JwtValidationError> {
        val normalizedIssuer = issuer.trimEnd('/')

        return fetchMetadataCommand.execute(FetchServerMetadataArgs(normalizedIssuer)).fold(
            success = { authServerMetadata ->
                // Extract OIDC-specific fields from additionalMetadata
                val additional = authServerMetadata.additionalMetadata
                val userinfoEndpoint = additional["userinfo_endpoint"]?.toString()?.trim('"')
                val responseTypesSupported = extractStringList(additional["response_types_supported"])
                val subjectTypesSupported = extractStringList(additional["subject_types_supported"])
                val idTokenSigningAlgValuesSupported = extractStringList(additional["id_token_signing_alg_values_supported"])
                val scopesSupported = extractStringList(additional["scopes_supported"])

                val metadata =
                    OidcDiscoveryMetadata(
                        issuer = authServerMetadata.issuer,
                        jwksUri =
                            authServerMetadata.jwksUri
                                ?: return Err(
                                    JwtValidationError.discoveryFailed(
                                        issuer = normalizedIssuer,
                                        cause = "JWKS URI not found in metadata",
                                    ),
                                ),
                        authorizationEndpoint = authServerMetadata.authorizationEndpoint,
                        tokenEndpoint = authServerMetadata.tokenEndpoint,
                        userinfoEndpoint = userinfoEndpoint,
                        responseTypesSupported = responseTypesSupported,
                        subjectTypesSupported = subjectTypesSupported,
                        idTokenSigningAlgValuesSupported = idTokenSigningAlgValuesSupported,
                        scopesSupported = scopesSupported,
                    )

                // Cache the result
                val now = Clock.System.now().epochSeconds
                cache[normalizedIssuer] =
                    CachedMetadata(
                        metadata = metadata,
                        expiresAt = now + cacheTtlSeconds,
                    )

                Ok(metadata)
            },
            failure = { error ->
                Err(
                    JwtValidationError.discoveryFailed(
                        issuer = normalizedIssuer,
                        cause = error.message?.defaultMessage,
                    ),
                )
            },
        )
    }

    private fun extractStringList(element: kotlinx.serialization.json.JsonElement?): List<String>? {
        if (element == null) {
            return null
        }
        return try {
            (element as? kotlinx.serialization.json.JsonArray)?.map {
                it.toString().trim('"')
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getMetadata(issuer: String): IdkResult<OidcDiscoveryMetadata, JwtValidationError> {
        val normalizedIssuer = issuer.trimEnd('/')
        val now = Clock.System.now().epochSeconds

        // Check cache
        cache[normalizedIssuer]?.let { cached ->
            if (cached.expiresAt > now) {
                return Ok(cached.metadata)
            }
            // Expired, remove from cache
            cache.remove(normalizedIssuer)
        }

        // Fetch fresh metadata
        return discover(normalizedIssuer)
    }

    override suspend fun invalidateCache(issuer: String) {
        val normalizedIssuer = issuer.trimEnd('/')
        cache.remove(normalizedIssuer)
    }
}
