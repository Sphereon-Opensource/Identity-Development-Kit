/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.sdjwt.vc

import com.sphereon.core.compat.JsExportCompat
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/**
 * Service for resolving SD-JWT-VC issuer metadata
 *
 * Issuer metadata provides information about the issuer's JWK Set
 * for signature verification.
 *
 * Resolution:
 * - Fetch from /.well-known/jwt-vc-issuer endpoint
 * - Issuer URL must be HTTPS
 * - Returns issuer identifier and JWK Set (inline or URI)
 */
@JsExportCompat
interface IssuerMetadataResolver {
    /**
     * Resolve issuer metadata for the given issuer URL
     *
     * @param issuer Issuer identifier (HTTPS URL)
     * @return Resolution result (success or failure)
     */
    suspend fun resolve(issuer: String): IssuerMetadataResolutionResult

    /**
     * Check if this resolver supports the given issuer format
     *
     * @param issuer Issuer identifier
     * @return True if this resolver can handle the issuer
     */
    fun supports(issuer: String): Boolean
}

/**
 * Well-known issuer metadata resolver
 * Fetches metadata from /.well-known/jwt-vc-issuer endpoint
 *
 * @property httpClient Ktor HTTP client for fetching metadata
 * @property json JSON serializer
 */
class WellKnownIssuerMetadataResolver(
    private val httpClient: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : IssuerMetadataResolver {
    companion object {
        private const val WELL_KNOWN_PATH = "/.well-known/jwt-vc-issuer"
    }

    override suspend fun resolve(issuer: String): IssuerMetadataResolutionResult {
        if (!supports(issuer)) {
            return IssuerMetadataResolutionResult.Failure(
                IssuerMetadataResolutionError.InvalidIssuerUrl(issuer),
            )
        }

        val wellKnownUrl = buildWellKnownUrl(issuer)

        return try {
            val response = httpClient.get(wellKnownUrl)

            if (!response.status.isSuccess()) {
                return when (response.status.value) {
                    404 -> {
                        IssuerMetadataResolutionResult.Failure(IssuerMetadataResolutionError.NotFound)
                    }

                    else -> {
                        IssuerMetadataResolutionResult.Failure(
                            IssuerMetadataResolutionError.NetworkError(
                                "HTTP ${response.status.value}: ${response.status.description}",
                            ),
                        )
                    }
                }
            }

            val body = response.bodyAsText()
            if (body.isEmpty()) {
                return IssuerMetadataResolutionResult.Failure(
                    IssuerMetadataResolutionError.InvalidFormat("Empty response body"),
                )
            }

            val metadata = json.decodeFromString<SdJwtVcIssuerMetadata>(body)

            // Validate that returned issuer matches requested issuer
            if (metadata.issuer != issuer) {
                return IssuerMetadataResolutionResult.Failure(
                    IssuerMetadataResolutionError.InvalidFormat(
                        "Issuer mismatch: expected '$issuer', got '${metadata.issuer}'",
                    ),
                )
            }

            IssuerMetadataResolutionResult.Success(metadata)
        } catch (expected: Exception) {
            IssuerMetadataResolutionResult.Failure(
                IssuerMetadataResolutionError.NetworkError(
                    message = "Failed to resolve issuer metadata: ${expected.message}",
                    cause = expected,
                ),
            )
        }
    }

    override fun supports(issuer: String): Boolean = issuer.startsWith("https://", ignoreCase = true)

    /**
     * Build well-known URL from issuer identifier
     * Handles trailing slashes correctly
     */
    private fun buildWellKnownUrl(issuer: String): String {
        val base = issuer.trimEnd('/')
        return "$base$WELL_KNOWN_PATH"
    }
}

/**
 * In-memory cache for issuer metadata
 * Reduces network requests by caching previously resolved metadata
 *
 * @property delegate Underlying resolver (e.g., WellKnownIssuerMetadataResolver)
 * @property maxSize Maximum cache entries (default: 100)
 */
class CachedIssuerMetadataResolver(
    private val delegate: IssuerMetadataResolver,
    private val maxSize: Int = 100,
) : IssuerMetadataResolver {
    private val cache = mutableMapOf<String, SdJwtVcIssuerMetadata>()

    override suspend fun resolve(issuer: String): IssuerMetadataResolutionResult {
        // Check cache first
        cache[issuer]?.let { cached ->
            return IssuerMetadataResolutionResult.Success(cached)
        }

        // Delegate to underlying resolver
        val result = delegate.resolve(issuer)

        // Cache successful results
        if (result is IssuerMetadataResolutionResult.Success) {
            // Simple LRU: remove oldest if cache is full
            if (cache.size >= maxSize) {
                val firstKey = cache.keys.first()
                cache.remove(firstKey)
            }
            cache[issuer] = result.metadata
        }

        return result
    }

    override fun supports(issuer: String): Boolean = delegate.supports(issuer)

    /**
     * Clear all cached metadata
     */
    fun clear() {
        cache.clear()
    }

    /**
     * Remove specific issuer from cache
     */
    fun evict(issuer: String) {
        cache.remove(issuer)
    }
}

/**
 * Composite resolver that tries multiple resolvers in order
 *
 * @property resolvers List of resolvers to try (in order)
 */
class CompositeIssuerMetadataResolver(
    private val resolvers: List<IssuerMetadataResolver>,
) : IssuerMetadataResolver {
    override suspend fun resolve(issuer: String): IssuerMetadataResolutionResult {
        for (resolver in resolvers) {
            if (resolver.supports(issuer)) {
                val result = resolver.resolve(issuer)
                if (result is IssuerMetadataResolutionResult.Success) {
                    return result
                }
                // Continue to next resolver on failure
            }
        }

        return IssuerMetadataResolutionResult.Failure(
            IssuerMetadataResolutionError.InvalidIssuerUrl(issuer),
        )
    }

    override fun supports(issuer: String): Boolean = resolvers.any { it.supports(issuer) }
}

/**
 * Static/pre-configured issuer metadata resolver
 * Useful for testing or offline scenarios
 *
 * @property metadata Map of issuer URL to pre-configured metadata
 */
class StaticIssuerMetadataResolver(
    private val metadata: Map<String, SdJwtVcIssuerMetadata>,
) : IssuerMetadataResolver {
    override suspend fun resolve(issuer: String): IssuerMetadataResolutionResult {
        val found = metadata[issuer]
        return if (found != null) {
            IssuerMetadataResolutionResult.Success(found)
        } else {
            IssuerMetadataResolutionResult.Failure(IssuerMetadataResolutionError.NotFound)
        }
    }

    override fun supports(issuer: String): Boolean = metadata.containsKey(issuer)
}
